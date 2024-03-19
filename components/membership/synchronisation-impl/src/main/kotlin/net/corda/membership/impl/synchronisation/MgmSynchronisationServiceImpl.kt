package net.corda.membership.impl.synchronisation

import net.corda.avro.serialization.CordaAvroSerializationFactory
import net.corda.configuration.read.ConfigChangedEvent
import net.corda.configuration.read.ConfigurationReadService
import net.corda.crypto.cipher.suite.CipherSchemeMetadata
import net.corda.crypto.cipher.suite.merkle.MerkleTreeProvider
import net.corda.crypto.client.CryptoOpsClient
import net.corda.crypto.core.toCorda
import net.corda.data.membership.command.synchronisation.mgm.ProcessSyncRequest
import net.corda.data.membership.p2p.DistributionType
import net.corda.data.membership.p2p.MembershipPackage
import net.corda.data.p2p.app.MembershipStatusFilter.ACTIVE_OR_SUSPENDED
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.helper.getConfig
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.RegistrationHandle
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import net.corda.membership.lib.InternalGroupParameters
import net.corda.membership.lib.MemberInfoExtension.Companion.MEMBER_STATUS_ACTIVE
import net.corda.membership.lib.MemberInfoExtension.Companion.MEMBER_STATUS_SUSPENDED
import net.corda.membership.lib.MemberInfoExtension.Companion.isMgm
import net.corda.membership.lib.MemberInfoExtension.Companion.status
import net.corda.membership.lib.SelfSignedMemberInfo
import net.corda.membership.locally.hosted.identities.LocallyHostedIdentitiesService
import net.corda.membership.p2p.helpers.MembershipPackageFactory
import net.corda.membership.p2p.helpers.MerkleTreeGenerator
import net.corda.membership.p2p.helpers.SignerFactory
import net.corda.membership.persistence.client.MembershipQueryClient
import net.corda.membership.read.MembershipGroupReaderProvider
import net.corda.membership.synchronisation.MgmSynchronisationService
import net.corda.membership.synchronisation.SynchronisationService
import net.corda.messaging.api.records.Record
import net.corda.p2p.messaging.P2pRecordsFactory
import net.corda.p2p.messaging.P2pRecordsFactory.Companion.MEMBERSHIP_DATA_DISTRIBUTION_PREFIX
import net.corda.p2p.messaging.P2pRecordsFactory.Companion.getTtlMinutes
import net.corda.schema.configuration.ConfigKeys.MEMBERSHIP_CONFIG
import net.corda.schema.configuration.MembershipConfig.TtlsConfig.MEMBERS_PACKAGE_UPDATE
import net.corda.utilities.time.Clock
import net.corda.utilities.time.UTCClock
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.crypto.SecureHash
import net.corda.v5.membership.MemberInfo
import net.corda.virtualnode.toCorda
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.LoggerFactory
import java.util.UUID

@Component(service = [SynchronisationService::class])
class MgmSynchronisationServiceImpl internal constructor(
    private val services: InjectedServices,
) : MgmSynchronisationService {
    @Suppress("LongParameterList")
    internal class InjectedServices(
        val coordinatorFactory: LifecycleCoordinatorFactory,
        val configurationReadService: ConfigurationReadService,
        val membershipGroupReaderProvider: MembershipGroupReaderProvider,
        cordaAvroSerializationFactory: CordaAvroSerializationFactory,
        cipherSchemeMetadata: CipherSchemeMetadata,
        cryptoOpsClient: CryptoOpsClient,
        val membershipQueryClient: MembershipQueryClient,
        merkleTreeProvider: MerkleTreeProvider,
        locallyHostedIdentitiesService: LocallyHostedIdentitiesService,
    ) {
        val merkleTreeGenerator by lazy {
            MerkleTreeGenerator(
                merkleTreeProvider,
                cordaAvroSerializationFactory
            )
        }
        val membershipPackageFactory by lazy {
            MembershipPackageFactory(
                clock,
                cipherSchemeMetadata,
                DistributionType.SYNC,
                merkleTreeGenerator,
            ) { UUID.randomUUID().toString() }
        }

        val signerFactory by lazy {
            SignerFactory(cryptoOpsClient, locallyHostedIdentitiesService)
        }

        val membershipP2PRecordsFactory by lazy {
            P2pRecordsFactory(
                clock,
                cordaAvroSerializationFactory,
            )
        }
    }

    @Suppress("LongParameterList")
    @Activate
    constructor(
        @Reference(service = LifecycleCoordinatorFactory::class)
        coordinatorFactory: LifecycleCoordinatorFactory,
        @Reference(service = ConfigurationReadService::class)
        configurationReadService: ConfigurationReadService,
        @Reference(service = MembershipGroupReaderProvider::class)
        membershipGroupReaderProvider: MembershipGroupReaderProvider,
        @Reference(service = CordaAvroSerializationFactory::class)
        cordaAvroSerializationFactory: CordaAvroSerializationFactory,
        @Reference(service = CipherSchemeMetadata::class)
        cipherSchemeMetadata: CipherSchemeMetadata,
        @Reference(service = CryptoOpsClient::class)
        cryptoOpsClient: CryptoOpsClient,
        @Reference(service = MembershipQueryClient::class)
        membershipQueryClient: MembershipQueryClient,
        @Reference(service = MerkleTreeProvider::class)
        merkleTreeProvider: MerkleTreeProvider,
        @Reference(service = LocallyHostedIdentitiesService::class)
        locallyHostedIdentitiesService: LocallyHostedIdentitiesService,
    ) :
        this(
            InjectedServices(
                coordinatorFactory,
                configurationReadService,
                membershipGroupReaderProvider,
                cordaAvroSerializationFactory,
                cipherSchemeMetadata,
                cryptoOpsClient,
                membershipQueryClient,
                merkleTreeProvider,
                locallyHostedIdentitiesService,
            )
        )
    private companion object {
        val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
        const val SERVICE = "MgmSynchronisationService"
        private val clock: Clock = UTCClock()
        const val IDENTITY_EX_MESSAGE = "is not part of the membership group!"
    }

    // Component lifecycle coordinator
    private val coordinator = services.coordinatorFactory.createCoordinator(lifecycleCoordinatorName, ::handleEvent)

    // for watching the config changes
    private var configHandle: AutoCloseable? = null

    // for checking the components' health
    private var componentHandle: RegistrationHandle? = null

    override val isRunning: Boolean
        get() = coordinator.isRunning

    /**
     * Private interface used for implementation swapping in response to lifecycle events.
     */
    private interface InnerSynchronisationService {
        fun processSyncRequest(request: ProcessSyncRequest): List<Record<*, *>>
    }

    private var impl: InnerSynchronisationService = InactiveImpl

    override fun start() {
        coordinator.start()
    }

    override fun stop() {
        coordinator.stop()
    }

    private fun activate(coordinator: LifecycleCoordinator, config: SmartConfig) {
        impl = ActiveImpl(config)
        coordinator.updateStatus(LifecycleStatus.UP)
    }

    private fun deactivate(coordinator: LifecycleCoordinator) {
        coordinator.updateStatus(LifecycleStatus.DOWN)
        impl = InactiveImpl
    }

    override fun processSyncRequest(request: ProcessSyncRequest) =
        impl.processSyncRequest(request)

    private object InactiveImpl : InnerSynchronisationService {
        override fun processSyncRequest(request: ProcessSyncRequest) =
            throw IllegalStateException("$SERVICE is currently inactive.")
    }

    private inner class ActiveImpl(
        private val config: SmartConfig
    ) : InnerSynchronisationService {
        override fun processSyncRequest(request: ProcessSyncRequest): List<Record<*, *>> {
            val memberHashFromTheReq = request.syncRequest.membersHash
            val mgm = request.synchronisationMetaData.mgm
            val requester = request.synchronisationMetaData.member
            val groupReader = services.membershipGroupReaderProvider.getGroupReader(mgm.toCorda())
            val mgmName = MemberX500Name.parse(mgm.x500Name)
            val mgmInfo = groupReader.lookup(mgmName)
                ?: throw CordaRuntimeException("MGM $mgmName $IDENTITY_EX_MESSAGE")
            val requesterInfo = services.membershipQueryClient.queryMemberInfo(
                mgm.toCorda(),
                listOf(requester.toCorda()),
                listOf(MEMBER_STATUS_ACTIVE, MEMBER_STATUS_SUSPENDED)
            ).getOrThrow().firstOrNull() ?: throw CordaRuntimeException("Requester ${requester.x500Name} $IDENTITY_EX_MESSAGE")
            // we don't want to include the MGM in the data package since MGM information comes from the group policy
            val allNonPendingMembersExcludingMgm = services.membershipQueryClient.queryMemberInfo(
                mgm.toCorda(),
                listOf(MEMBER_STATUS_ACTIVE, MEMBER_STATUS_SUSPENDED),
            ).getOrThrow().filterNot { it.isMgm }
            val groupParameters = groupReader.groupParameters
                ?: throw CordaRuntimeException("Failed to retrieve group parameters for building membership packages.")
            val record = if (compareHashes(memberHashFromTheReq.toCorda(), requesterInfo)) {
                // member has the latest updates regarding its own membership
                // will send all membership data from MGM
                if (requesterInfo.status == MEMBER_STATUS_SUSPENDED) {
                    createPackageRecord(
                        mgm,
                        requester,
                        createMembershipPackage(mgmInfo, listOf(requesterInfo), groupParameters),
                    )
                } else {
                    createPackageRecord(mgm, requester, createMembershipPackage(mgmInfo, allNonPendingMembersExcludingMgm, groupParameters))
                }
            } else {
                // member has not received the latest updates regarding its own membership
                // will send its missing updates about themselves only
                createPackageRecord(
                    mgm,
                    requester,
                    createMembershipPackage(mgmInfo, listOf(requesterInfo), groupParameters),
                )
            }
            logger.info("Sync package is sent to ${requester.x500Name}.")
            return listOf(record)
        }

        private fun createPackageRecord(
            source: net.corda.data.identity.HoldingIdentity,
            dest: net.corda.data.identity.HoldingIdentity,
            data: MembershipPackage
        ): Record<*, *> {
            return services.membershipP2PRecordsFactory.createMembershipAuthenticatedMessageRecord(
                source = source,
                destination = dest,
                content = data,
                messageIdPrefix = MEMBERSHIP_DATA_DISTRIBUTION_PREFIX,
                minutesToWait = config.getTtlMinutes(MEMBERS_PACKAGE_UPDATE),
                filter = ACTIVE_OR_SUSPENDED
            )
        }

        private fun compareHashes(memberHashSeenByMember: SecureHash, requester: SelfSignedMemberInfo): Boolean {
            val memberHashSeenByMgm = calculateHash(requester)
            if (memberHashSeenByMember != memberHashSeenByMgm) {
                return false
            }
            return true
        }

        private fun calculateHash(memberInfo: SelfSignedMemberInfo): SecureHash {
            return services.merkleTreeGenerator.generateTreeUsingSignedMembers(listOf(memberInfo)).root
        }

        private fun createMembershipPackage(
            mgm: MemberInfo,
            members: Collection<SelfSignedMemberInfo>,
            groupParameters: InternalGroupParameters,
        ): MembershipPackage {
            val mgmSigner = services.signerFactory.createSigner(mgm)
            val membersTree = services.merkleTreeGenerator.generateTreeUsingSignedMembers(members)

            return services.membershipPackageFactory.createMembershipPackage(
                mgmSigner,
                members,
                membersTree.root,
                groupParameters,
            )
        }
    }

    private fun handleEvent(event: LifecycleEvent, coordinator: LifecycleCoordinator) {
        when (event) {
            is StartEvent -> handleStartEvent(coordinator)
            is StopEvent -> handleStopEvent(coordinator)
            is RegistrationStatusChangeEvent -> handleRegistrationChangeEvent(event, coordinator)
            is ConfigChangedEvent -> handleConfigChange(event, coordinator)
        }
    }

    private fun handleStartEvent(coordinator: LifecycleCoordinator) {
        componentHandle?.close()
        componentHandle = coordinator.followStatusChangesByName(
            setOf(
                LifecycleCoordinatorName.forComponent<ConfigurationReadService>(),
                LifecycleCoordinatorName.forComponent<CryptoOpsClient>(),
                LifecycleCoordinatorName.forComponent<MembershipQueryClient>(),
                LifecycleCoordinatorName.forComponent<MembershipGroupReaderProvider>(),
                LifecycleCoordinatorName.forComponent<LocallyHostedIdentitiesService>()
            )
        )
    }

    private fun handleStopEvent(coordinator: LifecycleCoordinator) {
        deactivate(coordinator)
        componentHandle?.close()
        componentHandle = null
        configHandle?.close()
        configHandle = null
    }

    private fun handleRegistrationChangeEvent(
        event: RegistrationStatusChangeEvent,
        coordinator: LifecycleCoordinator,
    ) {
        when (event.status) {
            LifecycleStatus.UP -> {
                configHandle?.close()
                configHandle = services.configurationReadService.registerComponentForUpdates(
                    coordinator,
                    setOf(MEMBERSHIP_CONFIG)
                )
            }
            else -> {
                deactivate(coordinator)
                configHandle?.close()
            }
        }
    }

    private fun handleConfigChange(event: ConfigChangedEvent, coordinator: LifecycleCoordinator) {
        val config = event.config.getConfig(MEMBERSHIP_CONFIG)
        activate(coordinator, config)
    }
}
