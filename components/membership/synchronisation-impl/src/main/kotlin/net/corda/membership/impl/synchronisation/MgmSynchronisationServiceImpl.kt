package net.corda.membership.impl.synchronisation

import net.corda.chunking.toCorda
import net.corda.configuration.read.ConfigChangedEvent
import net.corda.configuration.read.ConfigurationReadService
import net.corda.crypto.client.CryptoOpsClient
import net.corda.data.CordaAvroSerializationFactory
import net.corda.data.membership.command.synchronisation.mgm.ProcessSyncRequest
import net.corda.data.membership.p2p.DistributionType
import net.corda.data.membership.p2p.MembershipPackage
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
import net.corda.membership.p2p.helpers.SignerFactory
import net.corda.membership.persistence.client.MembershipQueryClient
import net.corda.membership.read.MembershipGroupReaderProvider
import net.corda.membership.synchronisation.MgmSynchronisationService
import net.corda.membership.synchronisation.SynchronisationService
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.schema.configuration.ConfigKeys
import net.corda.schema.configuration.ConfigKeys.MESSAGING_CONFIG
import net.corda.utilities.time.Clock
import net.corda.utilities.time.UTCClock
import net.corda.v5.base.concurrent.getOrThrow
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.base.util.contextLogger
import net.corda.v5.cipher.suite.CipherSchemeMetadata
import net.corda.v5.crypto.SecureHash
import net.corda.v5.crypto.merkle.MerkleTreeFactory
import net.corda.v5.membership.MemberInfo
import net.corda.virtualnode.toCorda
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import java.util.UUID

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
        cipherSchemeMetadata: CipherSchemeMetadata,
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
        @Reference(service = CipherSchemeMetadata::class)
        cipherSchemeMetadata: CipherSchemeMetadata,
        @Reference(service = CryptoOpsClient::class)
        cryptoOpsClient: CryptoOpsClient,
        @Reference(service = MembershipQueryClient::class)
        membershipQueryClient: MembershipQueryClient,
        @Reference(service = MerkleTreeFactory::class)
        merkleTreeFactory: MerkleTreeFactory,
    ) :
            this(
                publisherFactory,
                coordinatorFactory,
                configurationReadService,
                membershipGroupReaderProvider,
                membershipQueryClient,
                MerkleTreeGenerator(
                    merkleTreeFactory,
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
        logger.info("$SERVICE started.")
        coordinator.start()
    }

    override fun stop() {
        logger.info("$SERVICE stopped.")
        coordinator.stop()
    }

    private fun activate(coordinator: LifecycleCoordinator) {
        impl = ActiveImpl()
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

    private inner class ActiveImpl : InnerSynchronisationService {
        override fun processSyncRequest(request: ProcessSyncRequest) {
            val memberHashFromTheReq = request.syncRequest.membersHash
            val mgm = request.synchronisationMetaData.mgm
            val requester = request.synchronisationMetaData.member
            val allMembers = membershipGroupReaderProvider.getGroupReader(mgm.toCorda()).lookup()
            val mgmInfo = allMembers.firstOrNull {
                it.holdingIdentity == mgm.toCorda()
            } ?: throw CordaRuntimeException("MGM ${MemberX500Name.parse(mgm.x500Name)} " + IDENTITY_EX_MESSAGE)
            val requesterInfo = allMembers.firstOrNull {
                it.holdingIdentity == requester.toCorda()
            } ?: throw CordaRuntimeException("Requester ${MemberX500Name.parse(requester.x500Name)} " +
                        IDENTITY_EX_MESSAGE)
            if (compareHashes(memberHashFromTheReq.toCorda(), requesterInfo)) {
                // member has the latest updates regarding its own membership
                // will send all membership data from MGM
                sendPackage(mgm, requester, createMembershipPackage(mgmInfo, allMembers))
            } else {
                // member has not received the latest updates regarding its own membership
                // will send its missing updates about themselves only
                sendPackage(mgm, requester, createMembershipPackage(mgmInfo, listOf(requesterInfo)))
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
                        content = data
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
            members: Collection<MemberInfo>
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
                membersTree.root
            )
        }
    }

    private fun handleEvent(event: LifecycleEvent, coordinator: LifecycleCoordinator) {
        logger.info("Received event $event.")
        when (event) {
            is StartEvent -> handleStartEvent(coordinator)
            is StopEvent -> handleStopEvent(coordinator)
            is RegistrationStatusChangeEvent -> handleRegistrationChangeEvent(event, coordinator)
            is ConfigChangedEvent -> handleConfigChange(event, coordinator)
        }
    }

    private fun handleStartEvent(coordinator: LifecycleCoordinator) {
        logger.info("Handling start event.")
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
        logger.info("Handling stop event.")
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
        logger.info("Handling registration changed event.")
        when (event.status) {
            LifecycleStatus.UP -> {
                configHandle?.close()
                configHandle = configurationReadService.registerComponentForUpdates(
                    coordinator,
                    setOf(ConfigKeys.BOOT_CONFIG, MESSAGING_CONFIG)
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
        logger.info("Handling config changed event.")
        _publisher?.close()
        _publisher = publisherFactory.createPublisher(
            PublisherConfig("mgm-synchronisation-service"),
            event.config.getConfig(MESSAGING_CONFIG)
        )
        _publisher?.start()
        activate(coordinator)
    }
}