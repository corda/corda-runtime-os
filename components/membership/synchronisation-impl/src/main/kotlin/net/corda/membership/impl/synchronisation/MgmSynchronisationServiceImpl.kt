package net.corda.membership.impl.synchronisation

import java.util.UUID
import net.corda.chunking.toCorda
import net.corda.configuration.read.ConfigChangedEvent
import net.corda.configuration.read.ConfigurationReadService
import net.corda.crypto.client.CryptoOpsClient
import net.corda.data.CordaAvroSerializationFactory
import net.corda.data.membership.command.synchronisation.mgm.ProcessSyncRequest
import net.corda.data.membership.p2p.DistributionType
import net.corda.data.membership.p2p.MembershipPackage
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
import net.corda.membership.lib.MemberInfoExtension.Companion.holdingIdentity
import net.corda.membership.p2p.helpers.MembershipPackageFactory
import net.corda.membership.p2p.helpers.MerkleTreeGenerator
import net.corda.membership.p2p.helpers.P2pRecordsFactory
import net.corda.membership.p2p.helpers.P2pRecordsFactory.Companion.getTtlMinutes
import net.corda.membership.p2p.helpers.SignerFactory
import net.corda.membership.persistence.client.MembershipQueryClient
import net.corda.membership.read.MembershipGroupReaderProvider
import net.corda.membership.synchronisation.MgmSynchronisationService
import net.corda.membership.synchronisation.SynchronisationService
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.schema.configuration.ConfigKeys
import net.corda.schema.configuration.ConfigKeys.MEMBERSHIP_CONFIG
import net.corda.schema.configuration.ConfigKeys.MESSAGING_CONFIG
import net.corda.schema.configuration.MembershipConfig.TtlsConfig.MEMBERS_PACKAGE_UPDATE
import net.corda.utilities.concurrent.getOrThrow
import net.corda.utilities.time.Clock
import net.corda.utilities.time.UTCClock
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.base.util.contextLogger
import net.corda.crypto.cipher.suite.CipherSchemeMetadata
import net.corda.v5.cipher.suite.merkle.MerkleTreeProvider
import net.corda.v5.crypto.SecureHash
import net.corda.v5.membership.GroupParameters
import net.corda.v5.membership.MemberInfo
import net.corda.virtualnode.toCorda
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Suppress("LongParameterList")
@Component(service = [SynchronisationService::class])
class MgmSynchronisationServiceImpl internal constructor(
    private val publisherFactory: PublisherFactory,
    coordinatorFactory: LifecycleCoordinatorFactory,
    private val configurationReadService: ConfigurationReadService,
    private val membershipGroupReaderProvider: MembershipGroupReaderProvider,
    private val membershipQueryClient: MembershipQueryClient,
    private val merkleTreeGenerator: MerkleTreeGenerator,
    private val membershipPackageFactory: MembershipPackageFactory,
    private val signerFactory: SignerFactory,
    private val p2pRecordsFactory: P2pRecordsFactory,
) : MgmSynchronisationService {
    private constructor(
        publisherFactory: PublisherFactory,
        coordinatorFactory: LifecycleCoordinatorFactory,
        configurationReadService: ConfigurationReadService,
        membershipGroupReaderProvider: MembershipGroupReaderProvider,
        membershipQueryClient: MembershipQueryClient,
        merkleTreeGenerator: MerkleTreeGenerator,
        cordaAvroSerializationFactory: CordaAvroSerializationFactory,
        signerFactory: SignerFactory,
        cipherSchemeMetadata: _root_ide_package_.net.corda.crypto.cipher.suite.CipherSchemeMetadata,
        p2pRecordsFactory: P2pRecordsFactory,
    ) : this(
        publisherFactory,
        coordinatorFactory,
        configurationReadService,
        membershipGroupReaderProvider,
        membershipQueryClient,
        merkleTreeGenerator,
        MembershipPackageFactory(
            clock,
            cordaAvroSerializationFactory,
            cipherSchemeMetadata,
            DistributionType.SYNC,
            merkleTreeGenerator
        ) { UUID.randomUUID().toString() },
        signerFactory,
        p2pRecordsFactory,
    )

    @Activate constructor(
        @Reference(service = PublisherFactory::class)
        publisherFactory: PublisherFactory,
        @Reference(service = LifecycleCoordinatorFactory::class)
        coordinatorFactory: LifecycleCoordinatorFactory,
        @Reference(service = ConfigurationReadService::class)
        configurationReadService: ConfigurationReadService,
        @Reference(service = MembershipGroupReaderProvider::class)
        membershipGroupReaderProvider: MembershipGroupReaderProvider,
        @Reference(service = CordaAvroSerializationFactory::class)
        cordaAvroSerializationFactory: CordaAvroSerializationFactory,
        @Reference(service = _root_ide_package_.net.corda.crypto.cipher.suite.CipherSchemeMetadata::class)
        cipherSchemeMetadata: _root_ide_package_.net.corda.crypto.cipher.suite.CipherSchemeMetadata,
        @Reference(service = CryptoOpsClient::class)
        cryptoOpsClient: CryptoOpsClient,
        @Reference(service = MembershipQueryClient::class)
        membershipQueryClient: MembershipQueryClient,
        @Reference(service = MerkleTreeProvider::class)
        merkleTreeProvider: MerkleTreeProvider,
    ) :
            this(
                publisherFactory,
                coordinatorFactory,
                configurationReadService,
                membershipGroupReaderProvider,
                membershipQueryClient,
                MerkleTreeGenerator(
                    merkleTreeProvider,
                    cordaAvroSerializationFactory
                ),
                cordaAvroSerializationFactory,
                SignerFactory(cryptoOpsClient),
                cipherSchemeMetadata,
                P2pRecordsFactory(
                    cordaAvroSerializationFactory,
                    clock,
                )
            )

    private companion object {
        val logger = contextLogger()
        const val SERVICE = "MgmSynchronisationService"
        private val clock: Clock = UTCClock()
        const val IDENTITY_EX_MESSAGE = "is not part of the membership group!"
    }

    // Component lifecycle coordinator
    private val coordinator = coordinatorFactory.createCoordinator(lifecycleCoordinatorName, ::handleEvent)

    // for watching the config changes
    private var configHandle: AutoCloseable? = null

    // for checking the components' health
    private var componentHandle: RegistrationHandle? = null

    private var _publisher: Publisher? = null

    /**
     * Publisher for Kafka messaging. Recreated after every [MESSAGING_CONFIG] change.
     */
    private val publisher: Publisher
        get() = _publisher ?: throw IllegalArgumentException("Publisher is not initialized.")

    override val isRunning: Boolean
        get() = coordinator.isRunning

    /**
     * Private interface used for implementation swapping in response to lifecycle events.
     */
    private interface InnerSynchronisationService : AutoCloseable {
        fun processSyncRequest(request: ProcessSyncRequest)
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
        impl.close()
        impl = InactiveImpl
    }

    override fun processSyncRequest(request: ProcessSyncRequest) =
        impl.processSyncRequest(request)

    private object InactiveImpl : InnerSynchronisationService {
        override fun processSyncRequest(request: ProcessSyncRequest) =
            throw IllegalStateException("$SERVICE is currently inactive.")

        override fun close() = Unit
    }

    private inner class ActiveImpl(
        private val config: SmartConfig
    ) : InnerSynchronisationService {
        override fun processSyncRequest(request: ProcessSyncRequest) {
            val memberHashFromTheReq = request.syncRequest.membersHash
            val mgm = request.synchronisationMetaData.mgm
            val requester = request.synchronisationMetaData.member
            val groupReader = membershipGroupReaderProvider.getGroupReader(mgm.toCorda())
            val mgmName = MemberX500Name.parse(mgm.x500Name)
            val mgmInfo = groupReader.lookup(mgmName)
                ?: throw CordaRuntimeException("MGM $mgmName $IDENTITY_EX_MESSAGE")
            val requesterName = MemberX500Name.parse(requester.x500Name)
            val requesterInfo = groupReader.lookup(requesterName)
                ?: throw CordaRuntimeException("Requester $requesterName $IDENTITY_EX_MESSAGE")
            // we don't want to include the MGM in the data package since MGM information comes from the group policy
            val allMembers = groupReader.lookup().filterNot { it.holdingIdentity == mgm.toCorda() }
            val groupParameters = groupReader.groupParameters
                ?: throw CordaRuntimeException("Failed to retrieve group parameters for building membership packages.")
            if (compareHashes(memberHashFromTheReq.toCorda(), requesterInfo)) {
                // member has the latest updates regarding its own membership
                // will send all membership data from MGM
                sendPackage(mgm, requester, createMembershipPackage(mgmInfo, allMembers, groupParameters))
            } else {
                // member has not received the latest updates regarding its own membership
                // will send its missing updates about themselves only
                sendPackage(mgm, requester, createMembershipPackage(mgmInfo, listOf(requesterInfo), groupParameters))
            }
            logger.info("Sync package is sent to ${requester.x500Name}.")
        }

        override fun close() {
            publisher.close()
        }

        private fun sendPackage(
            source: net.corda.data.identity.HoldingIdentity,
            dest: net.corda.data.identity.HoldingIdentity,
            data: MembershipPackage
        ) {
            val syncPackage = publisher.publish(
                listOf(
                    p2pRecordsFactory.createAuthenticatedMessageRecord(
                        source = source,
                        destination = dest,
                        content = data,
                        minutesToWait = config.getTtlMinutes(MEMBERS_PACKAGE_UPDATE),
                    )
                )
            )
            syncPackage.forEach { it.getOrThrow() }
        }

        private fun compareHashes(memberHashSeenByMember: SecureHash, requester: MemberInfo):  Boolean {
            val memberHashSeenByMgm = calculateHash(requester)
            if (memberHashSeenByMember != memberHashSeenByMgm) {
                return false
            }
            return true
        }

        private fun calculateHash(memberInfo: MemberInfo): SecureHash {
            return merkleTreeGenerator.generateTree(listOf(memberInfo)).root
        }

        private fun createMembershipPackage(
            mgm: MemberInfo,
            members: Collection<MemberInfo>,
            groupParameters: GroupParameters,
        ): MembershipPackage {
            val mgmSigner = signerFactory.createSigner(mgm)
            val signatures = membershipQueryClient
                .queryMembersSignatures(
                    mgm.holdingIdentity,
                    members.map {
                        it.holdingIdentity
                    }
                ).getOrThrow()
            val membersTree = merkleTreeGenerator.generateTree(members)

            return membershipPackageFactory.createMembershipPackage(
                mgmSigner,
                signatures,
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
            )
        )
    }

    private fun handleStopEvent(coordinator: LifecycleCoordinator) {
        deactivate(coordinator)
        componentHandle?.close()
        componentHandle = null
        configHandle?.close()
        configHandle = null
        _publisher?.close()
        _publisher = null
    }

    private fun handleRegistrationChangeEvent(
        event: RegistrationStatusChangeEvent,
        coordinator: LifecycleCoordinator,
    ) {
        when (event.status) {
            LifecycleStatus.UP -> {
                configHandle?.close()
                configHandle = configurationReadService.registerComponentForUpdates(
                    coordinator,
                    setOf(ConfigKeys.BOOT_CONFIG, MESSAGING_CONFIG, MEMBERSHIP_CONFIG)
                )
            }
            else -> {
                deactivate(coordinator)
                configHandle?.close()
            }
        }
    }

    // re-creates the publisher with the new config, sets the lifecycle status to UP when the publisher is ready for the first time
    private fun handleConfigChange(event: ConfigChangedEvent, coordinator: LifecycleCoordinator) {
        _publisher?.close()
        _publisher = publisherFactory.createPublisher(
            PublisherConfig("mgm-synchronisation-service"),
            event.config.getConfig(MESSAGING_CONFIG)
        )
        _publisher?.start()
        val config = event.config.getConfig(MEMBERSHIP_CONFIG)
        activate(coordinator, config)
    }
}