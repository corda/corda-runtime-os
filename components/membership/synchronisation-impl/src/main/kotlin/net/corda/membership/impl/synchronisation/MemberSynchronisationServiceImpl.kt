package net.corda.membership.impl.synchronisation

import net.corda.avro.serialization.CordaAvroSerializationFactory
import net.corda.configuration.read.ConfigChangedEvent
import net.corda.configuration.read.ConfigurationReadService
import net.corda.crypto.cipher.suite.KeyEncodingService
import net.corda.crypto.cipher.suite.SignatureVerificationService
import net.corda.crypto.cipher.suite.merkle.MerkleTreeProvider
import net.corda.crypto.core.bytes
import net.corda.crypto.core.toAvro
import net.corda.crypto.core.toCorda
import net.corda.data.membership.command.synchronisation.member.ProcessMembershipUpdates
import net.corda.data.membership.p2p.DistributionMetaData
import net.corda.data.membership.p2p.MembershipPackage
import net.corda.data.membership.p2p.MembershipSyncRequest
import net.corda.data.p2p.app.AppMessage
import net.corda.data.p2p.app.MembershipStatusFilter
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
import net.corda.lifecycle.TimerEvent
import net.corda.membership.lib.GroupParametersFactory
import net.corda.membership.lib.MemberInfoExtension.Companion.MEMBER_STATUS_SUSPENDED
import net.corda.membership.lib.MemberInfoExtension.Companion.id
import net.corda.membership.lib.MemberInfoExtension.Companion.isMgm
import net.corda.membership.lib.MemberInfoExtension.Companion.sessionInitiationKeys
import net.corda.membership.lib.MemberInfoExtension.Companion.status
import net.corda.membership.lib.MemberInfoFactory
import net.corda.membership.p2p.helpers.MembershipP2pRecordsFactory
import net.corda.membership.p2p.helpers.MerkleTreeGenerator
import net.corda.membership.p2p.helpers.Verifier
import net.corda.membership.persistence.client.MembershipPersistenceClient
import net.corda.membership.read.MembershipGroupReader
import net.corda.membership.read.MembershipGroupReaderProvider
import net.corda.membership.synchronisation.MemberSynchronisationService
import net.corda.membership.synchronisation.SynchronisationService
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.records.Record
import net.corda.p2p.messaging.P2pRecordsFactory
import net.corda.schema.Schemas.Membership.MEMBER_LIST_TOPIC
import net.corda.schema.configuration.ConfigKeys.BOOT_CONFIG
import net.corda.schema.configuration.ConfigKeys.MEMBERSHIP_CONFIG
import net.corda.schema.configuration.ConfigKeys.MESSAGING_CONFIG
import net.corda.schema.configuration.MembershipConfig.MAX_DURATION_BETWEEN_SYNC_REQUESTS_MINUTES
import net.corda.utilities.debug
import net.corda.utilities.time.Clock
import net.corda.utilities.time.UTCClock
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.membership.MemberInfo
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import net.corda.virtualnode.toAvro
import net.corda.virtualnode.toCorda
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.LoggerFactory
import java.util.Random
import java.util.UUID
import java.util.concurrent.TimeUnit

@Component(service = [SynchronisationService::class])
class MemberSynchronisationServiceImpl internal constructor(
    private val services: Services,
    coordinatorFactory: LifecycleCoordinatorFactory,
) : MemberSynchronisationService {
    @Suppress("LongParameterList")
    @Activate
    constructor(
        @Reference(service = PublisherFactory::class)
        publisherFactory: PublisherFactory,
        @Reference(service = ConfigurationReadService::class)
        configurationReadService: ConfigurationReadService,
        @Reference(service = LifecycleCoordinatorFactory::class)
        coordinatorFactory: LifecycleCoordinatorFactory,
        @Reference(service = MemberInfoFactory::class)
        memberInfoFactory: MemberInfoFactory,
        @Reference(service = MembershipGroupReaderProvider::class)
        membershipGroupReaderProvider: MembershipGroupReaderProvider,
        @Reference(service = CordaAvroSerializationFactory::class)
        cordaAvroSerializationFactory: CordaAvroSerializationFactory,
        @Reference(service = MerkleTreeProvider::class)
        merkleTreeProvider: MerkleTreeProvider,
        @Reference(service = VirtualNodeInfoReadService::class)
        virtualNodeInfoReadService: VirtualNodeInfoReadService,
        @Reference(service = SignatureVerificationService::class)
        signatureVerificationService: SignatureVerificationService,
        @Reference(service = KeyEncodingService::class)
        keyEncodingService: KeyEncodingService,
        @Reference(service = MembershipPersistenceClient::class)
        membershipPersistenceClient: MembershipPersistenceClient,
        @Reference(service = GroupParametersFactory::class)
        groupParametersFactory: GroupParametersFactory,
    ) : this(
        Services(
            publisherFactory,
            configurationReadService,
            memberInfoFactory,
            membershipGroupReaderProvider,
            Verifier(
                signatureVerificationService,
                keyEncodingService,
            ),
            LocallyHostedMembersReader(
                virtualNodeInfoReadService,
                membershipGroupReaderProvider,
            ),
            MembershipP2pRecordsFactory(
                cordaAvroSerializationFactory,
                P2pRecordsFactory(UTCClock()),
            ),
            MerkleTreeGenerator(
                merkleTreeProvider,
                cordaAvroSerializationFactory,
            ),
            UTCClock(),
            membershipPersistenceClient,
            groupParametersFactory,
        ),
        coordinatorFactory,
    )

    // A wrapper that holds the services used by  this class. Introduce to avoid the OSGi issue with
    // two constructors with the same number of arguments.
    internal data class Services(
        val publisherFactory: PublisherFactory,
        val configurationReadService: ConfigurationReadService,
        val memberInfoFactory: MemberInfoFactory,
        val membershipGroupReaderProvider: MembershipGroupReaderProvider,
        val verifier: Verifier,
        val membersReader: LocallyHostedMembersReader,
        val membershipP2PRecordsFactory: MembershipP2pRecordsFactory,
        val merkleTreeGenerator: MerkleTreeGenerator,
        val clock: Clock,
        val membershipPersistenceClient: MembershipPersistenceClient,
        val groupParametersFactory: GroupParametersFactory,
    )

    /**
     * Private interface used for implementation swapping in response to lifecycle events.
     */
    private interface InnerSynchronisationService : AutoCloseable {
        fun processMembershipUpdates(updates: ProcessMembershipUpdates): List<Record<*, *>>

        fun cancelCurrentRequestAndScheduleNewOne(
            memberIdentity: HoldingIdentity,
            mgm: HoldingIdentity,
        ): Boolean
    }

    private companion object {
        val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)

        const val PUBLICATION_TIMEOUT_SECONDS = 30L
        const val RESEND_NOW_MAX_IN_MINUTES = 5L
        const val SERVICE = "MemberSynchronisationService"

        private val random by lazy {
            Random()
        }
    }

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

    // Component lifecycle coordinator
    private val coordinator = coordinatorFactory.createCoordinator(lifecycleCoordinatorName, ::handleEvent)

    private var impl: InnerSynchronisationService = InactiveImpl

    override fun processMembershipUpdates(updates: ProcessMembershipUpdates) =
        impl.processMembershipUpdates(updates)

    override val isRunning: Boolean
        get() = coordinator.isRunning

    override fun start() {
        coordinator.start()
    }

    override fun stop() {
        coordinator.stop()
    }

    private fun activate(membershipConfigurations: SmartConfig) {
        impl = ActiveImpl(membershipConfigurations)
        coordinator.updateStatus(LifecycleStatus.UP)
        services.membersReader.readAllLocalMembers().forEach {
            impl.cancelCurrentRequestAndScheduleNewOne(it.member, it.mgm)
        }
    }

    private fun deactivate(coordinator: LifecycleCoordinator) {
        coordinator.updateStatus(LifecycleStatus.DOWN)
        impl.close()
        impl = InactiveImpl
    }

    private object InactiveImpl : InnerSynchronisationService {
        override fun processMembershipUpdates(updates: ProcessMembershipUpdates) =
            throw IllegalStateException("$SERVICE is currently inactive.")

        override fun cancelCurrentRequestAndScheduleNewOne(
            memberIdentity: HoldingIdentity,
            mgm: HoldingIdentity,
        ) = false

        override fun close() = Unit
    }

    private data class SendSyncRequest(
        val member: HoldingIdentity,
        val mgm: HoldingIdentity,
        override val key: String,
    ) : TimerEvent

    private inner class ActiveImpl(
        membershipConfigurations: SmartConfig,
    ) : InnerSynchronisationService {
        private val maxDelayBetweenRequestsInMillis = membershipConfigurations
            .getLong(MAX_DURATION_BETWEEN_SYNC_REQUESTS_MINUTES).let {
                TimeUnit.MINUTES.toMillis(it)
            }

        private fun delayToNextRequestInMilliSeconds(): Long {
            // Add noise to prevent all the members to ask for sync in the same time
            return maxDelayBetweenRequestsInMillis -
                (random.nextDouble() * 0.1 * maxDelayBetweenRequestsInMillis).toLong()
        }

        private fun parseGroupParameters(
            mgm: MemberInfo,
            membershipPackage: MembershipPackage
        ) = with(membershipPackage.groupParameters) {
            services.verifier.verify(
                mgm.sessionInitiationKeys,
                mgmSignature,
                mgmSignatureSpec,
                groupParameters.array()
            )
            services.groupParametersFactory.create(this)
        }

        override fun cancelCurrentRequestAndScheduleNewOne(
            memberIdentity: HoldingIdentity,
            mgm: HoldingIdentity,
        ): Boolean {
            coordinator.setTimer(
                key = "SendSyncRequest-${memberIdentity.fullHash}",
                delay = delayToNextRequestInMilliSeconds()
            ) {
                SendSyncRequest(memberIdentity, mgm, it)
            }
            return true
        }

        override fun processMembershipUpdates(updates: ProcessMembershipUpdates): List<Record<*, *>> {
            val viewOwningMember = updates.synchronisationMetaData.member.toCorda()
            val mgm = updates.synchronisationMetaData.mgm.toCorda()
            val groupReader = services.membershipGroupReaderProvider.getGroupReader(viewOwningMember)
            val mgmInfo = groupReader.lookup().firstOrNull { it.isMgm } ?: throw CordaRuntimeException(
                "Could not find MGM info in the member list for member ${viewOwningMember.x500Name}"
            )
            logger.info("Member $viewOwningMember received membership updates from $mgm.")

            return try {
                cancelCurrentRequestAndScheduleNewOne(viewOwningMember, mgm)
                val updateMembersInfo = updates.membershipPackage.memberships?.memberships?.map { update ->
                    val selfSignedMemberInfo = services.memberInfoFactory.createSelfSignedMemberInfo(
                        update.memberContext.data.array(),
                        update.mgmContext.data.array(),
                        update.memberContext.signature,
                        update.memberContext.signatureSpec,
                    )

                    services.verifier.verify(
                        selfSignedMemberInfo.sessionInitiationKeys,
                        update.memberContext.signature,
                        update.memberContext.signatureSpec,
                        update.memberContext.data.array()
                    )

                    val contextByteArray = listOf(
                        update.memberContext.data.array(),
                        update.mgmContext.data.array()
                    )

                    services.verifier.verify(
                        mgmInfo.sessionInitiationKeys,
                        update.mgmContext.signature,
                        update.mgmContext.signatureSpec,
                        services.merkleTreeGenerator
                            .createTree(contextByteArray)
                            .root
                            .bytes
                    )

                    selfSignedMemberInfo
                }?.associateBy { it.id } ?: emptyMap()

                val persistentMemberInfoRecords = updateMembersInfo.map { (_, memberInfo) ->
                    Record(
                        MEMBER_LIST_TOPIC,
                        "${viewOwningMember.shortHash}-${memberInfo.id}",
                        services.memberInfoFactory.createPersistentMemberInfo(
                            viewOwningMember.toAvro(),
                            memberInfo.memberContextBytes,
                            memberInfo.mgmContextBytes,
                            memberInfo.memberSignature,
                            memberInfo.memberSignatureSpec,
                        )
                    )
                }

                val packageHash = updates.membershipPackage.memberships?.hashCheck?.toCorda()
                val allRecords = if (packageHash == null) {
                    persistentMemberInfoRecords + createSynchronisationRequestMessage(
                        groupReader,
                        viewOwningMember,
                        mgm,
                    )
                } else {
                    val knownMembers = groupReader.lookup(MembershipStatusFilter.ACTIVE_OR_SUSPENDED)
                        .filter { !it.isMgm }.associateBy { it.id }
                    val viewOwnerShortHash = viewOwningMember.shortHash.value
                    val latestViewOwnerMemberInfo =
                        updateMembersInfo[viewOwnerShortHash] ?: knownMembers[viewOwnerShortHash]
                    val expectedHash = if (latestViewOwnerMemberInfo?.status == MEMBER_STATUS_SUSPENDED) {
                        services.merkleTreeGenerator.generateTreeUsingMembers(listOf(latestViewOwnerMemberInfo)).root
                    } else {
                        val allMembers = knownMembers + updateMembersInfo
                        services.merkleTreeGenerator.generateTreeUsingMembers(allMembers.values).root
                    }
                    if (packageHash != expectedHash) {
                        persistentMemberInfoRecords + createSynchronisationRequestMessage(
                            groupReader,
                            viewOwningMember,
                            mgm,
                        )
                    } else {
                        persistentMemberInfoRecords
                    }
                }

                val persistRecords = mgmInfo.let {
                    val groupParameters = parseGroupParameters(
                        it,
                        updates.membershipPackage
                    )
                    services.membershipPersistenceClient
                        .persistGroupParameters(viewOwningMember, groupParameters)
                        .createAsyncCommands()
                }
                allRecords + persistRecords
            } catch (e: Exception) {
                logger.warn(
                    "Failed to process membership updates received by ${viewOwningMember.x500Name}. " +
                        "Will trigger a fresh sync with MGM.",
                    e,
                )
                createSynchroniseNowRequest(
                    viewOwningMember,
                    mgm,
                )
            }
        }

        override fun close() {
            publisher.close()
        }
    }

    private fun createSynchronisationRequestMessage(
        groupReader: MembershipGroupReader,
        memberIdentity: HoldingIdentity,
        mgm: HoldingIdentity,
    ): Record<String, AppMessage> {
        val member = groupReader.lookup(
            memberIdentity.x500Name,
            MembershipStatusFilter.ACTIVE_OR_SUSPENDED
        ) ?: throw CordaRuntimeException("Unknown member $memberIdentity")
        val memberHash = services.merkleTreeGenerator.generateTreeUsingMembers(listOf(member))
            .root
            .toAvro()
        return services.membershipP2PRecordsFactory.createAuthenticatedMessageRecord(
            source = memberIdentity.toAvro(),
            destination = mgm.toAvro(),
            content = MembershipSyncRequest(
                DistributionMetaData(
                    UUID.randomUUID().toString(),
                    services.clock.instant(),
                ),
                memberHash,
                // TODO Set Bloom filter
                null,
                // TODO Set Group Parameters Hash
                memberHash,
                // TODO Set CPI whitelist Hash
                memberHash,
            )
        )
    }

    private fun createSynchroniseNowRequest(
        viewOwningMember: HoldingIdentity,
        mgm: HoldingIdentity
    ): List<Record<*, *>> {
        return try {
            listOf(
                createSynchronisationRequestMessage(
                    groupReader = services.membershipGroupReaderProvider.getGroupReader(viewOwningMember),
                    memberIdentity = viewOwningMember,
                    mgm = mgm,
                ),
            )
        } catch (e: Exception) {
            logger.warn("Failed to trigger an immediate sync, will schedule one to be triggered later.", e)
            coordinator.setTimer(
                key = "SendSyncRequest-${viewOwningMember.fullHash}",
                delay = TimeUnit.MINUTES.toMillis(RESEND_NOW_MAX_IN_MINUTES),
            ) {
                SendSyncRequest(viewOwningMember, mgm, it)
            }
            emptyList()
        }
    }

    private fun handleEvent(event: LifecycleEvent, coordinator: LifecycleCoordinator) {
        when (event) {
            is StartEvent -> handleStartEvent(coordinator)
            is StopEvent -> handleStopEvent(coordinator)
            is RegistrationStatusChangeEvent -> handleRegistrationChangeEvent(event, coordinator)
            is ConfigChangedEvent -> handleConfigChange(event)
            is SendSyncRequest -> sendSyncRequest(event)
        }
    }

    private fun sendSyncRequest(request: SendSyncRequest) {
        if (!impl.cancelCurrentRequestAndScheduleNewOne(request.member, request.mgm)) {
            return
        }
        logger.info(
            "Member ${request.member} had not received membership package for a while now, " +
                "asking MGM to send sync package"
        )
        try {
            val groupReader = services.membershipGroupReaderProvider.getGroupReader(request.member)
            val syncRequest = createSynchronisationRequestMessage(
                groupReader,
                request.member,
                request.mgm,
            )
            publisher.publish(listOf(syncRequest)).forEach {
                it.get(PUBLICATION_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            }
            logger.debug { "Member ${request.member} has asked the MGM for a sync package." }
        } catch (e: Exception) {
            logger.warn("Sync request for ${request.member} failed!", e)
        }
    }

    private fun handleStartEvent(coordinator: LifecycleCoordinator) {
        componentHandle?.close()
        componentHandle = coordinator.followStatusChangesByName(
            setOf(
                LifecycleCoordinatorName.forComponent<ConfigurationReadService>(),
                LifecycleCoordinatorName.forComponent<VirtualNodeInfoReadService>(),
                LifecycleCoordinatorName.forComponent<MembershipGroupReaderProvider>(),
                LifecycleCoordinatorName.forComponent<MembershipPersistenceClient>(),
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
                configHandle = services.configurationReadService.registerComponentForUpdates(
                    coordinator,
                    setOf(BOOT_CONFIG, MESSAGING_CONFIG, MEMBERSHIP_CONFIG)
                )
            }
            else -> {
                deactivate(coordinator)
                configHandle?.close()
            }
        }
    }

    // re-creates the publisher with the new config, sets the lifecycle status to UP when the publisher is ready for the first time
    private fun handleConfigChange(event: ConfigChangedEvent) {
        _publisher?.close()
        _publisher = services.publisherFactory.createPublisher(
            PublisherConfig("member-synchronisation-service"),
            event.config.getConfig(MESSAGING_CONFIG)
        )
        _publisher?.start()
        activate(event.config.getConfig(MEMBERSHIP_CONFIG))
    }
}
