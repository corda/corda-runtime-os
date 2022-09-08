package net.corda.membership.impl.synchronisation

import net.corda.chunking.toAvro
import net.corda.chunking.toCorda
import net.corda.configuration.read.ConfigChangedEvent
import net.corda.configuration.read.ConfigurationReadService
import net.corda.data.CordaAvroDeserializer
import net.corda.data.CordaAvroSerializationFactory
import net.corda.data.KeyValuePairList
import net.corda.data.membership.PersistentMemberInfo
import net.corda.data.membership.command.synchronisation.member.ProcessMembershipUpdates
import net.corda.data.membership.p2p.DistributionMetaData
import net.corda.data.membership.p2p.MembershipSyncRequest
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
import net.corda.membership.lib.MemberInfoExtension.Companion.id
import net.corda.membership.lib.MemberInfoExtension.Companion.isMgm
import net.corda.membership.lib.MemberInfoFactory
import net.corda.membership.lib.toSortedMap
import net.corda.membership.lib.toWire
import net.corda.membership.p2p.helpers.MerkleTreeGenerator
import net.corda.membership.p2p.helpers.P2pRecordsFactory
import net.corda.membership.read.MembershipGroupReader
import net.corda.membership.read.MembershipGroupReaderProvider
import net.corda.membership.synchronisation.MemberSynchronisationService
import net.corda.membership.synchronisation.SynchronisationService
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.records.Record
import net.corda.p2p.app.AppMessage
import net.corda.schema.Schemas.Membership.Companion.MEMBER_LIST_TOPIC
import net.corda.schema.configuration.ConfigKeys.BOOT_CONFIG
import net.corda.schema.configuration.ConfigKeys.MEMBERSHIP_CONFIG
import net.corda.schema.configuration.ConfigKeys.MESSAGING_CONFIG
import net.corda.schema.configuration.MembershipConfig.MAX_DURATION_BETWEEN_SYNC_REQUESTS_MINUTES
import net.corda.utilities.time.Clock
import net.corda.utilities.time.UTCClock
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.base.util.contextLogger
import net.corda.v5.crypto.merkle.MerkleTreeFactory
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.toAvro
import net.corda.virtualnode.toCorda
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import java.util.Random
import java.util.UUID
import java.util.concurrent.TimeUnit

@Component(service = [SynchronisationService::class])
@Suppress("LongParameterList")
class MemberSynchronisationServiceImpl internal constructor(
    private val publisherFactory: PublisherFactory,
    private val configurationReadService: ConfigurationReadService,
    coordinatorFactory: LifecycleCoordinatorFactory,
    private val serializationFactory: CordaAvroSerializationFactory,
    private val memberInfoFactory: MemberInfoFactory,
    private val membershipGroupReaderProvider: MembershipGroupReaderProvider,
    private val p2pRecordsFactory: P2pRecordsFactory,
    private val merkleTreeGenerator: MerkleTreeGenerator,
    private val clock: Clock,
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
        @Reference(service = CordaAvroSerializationFactory::class)
        serializationFactory: CordaAvroSerializationFactory,
        @Reference(service = MemberInfoFactory::class)
        memberInfoFactory: MemberInfoFactory,
        @Reference(service = MembershipGroupReaderProvider::class)
        membershipGroupReaderProvider: MembershipGroupReaderProvider,
        @Reference(service = CordaAvroSerializationFactory::class)
        cordaAvroSerializationFactory: CordaAvroSerializationFactory,
        @Reference(service = MerkleTreeFactory::class)
        merkleTreeFactory: MerkleTreeFactory,
    ) : this(
        publisherFactory,
        configurationReadService,
        coordinatorFactory,
        serializationFactory,
        memberInfoFactory,
        membershipGroupReaderProvider,
        P2pRecordsFactory(
            cordaAvroSerializationFactory,
            UTCClock(),
        ),
        MerkleTreeGenerator(
            merkleTreeFactory,
            cordaAvroSerializationFactory,
        ),
        UTCClock(),
    )

    /**
     * Private interface used for implementation swapping in response to lifecycle events.
     */
    private interface InnerSynchronisationService : AutoCloseable {
        fun processMembershipUpdates(updates: ProcessMembershipUpdates)

        fun cancelCurrentRequestAndScheduleNewOne(
            memberIdentity: HoldingIdentity,
            mgm: HoldingIdentity,
        ): Boolean
    }

    private companion object {
        val logger = contextLogger()

        const val PUBLICATION_TIMEOUT_SECONDS = 30L
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
        logger.info("$SERVICE started.")
        coordinator.start()
    }

    override fun stop() {
        logger.info("$SERVICE stopped.")
        coordinator.stop()
    }

    private fun activate(membershipConfigurations: SmartConfig) {
        impl = ActiveImpl(membershipConfigurations)
        coordinator.updateStatus(LifecycleStatus.UP)
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

        private val deserializer: CordaAvroDeserializer<KeyValuePairList> =
            serializationFactory.createAvroDeserializer({
                logger.error("Deserialization of KeyValuePairList from MembershipPackage failed while processing membership updates.")
            }, KeyValuePairList::class.java)

        private fun delayToNextRequestInMilliSeconds(): Long {
            // Add noise to prevent all the members to ask for sync in the same time
            return maxDelayBetweenRequestsInMillis -
                (random.nextDouble() * 0.1 * maxDelayBetweenRequestsInMillis).toLong()
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

        override fun processMembershipUpdates(updates: ProcessMembershipUpdates) {
            val viewOwningMember = updates.synchronisationMetaData.member.toCorda()
            val mgm = updates.synchronisationMetaData.mgm.toCorda()
            logger.info("Member $viewOwningMember received membership updates from $mgm.")
            cancelCurrentRequestAndScheduleNewOne(viewOwningMember, mgm)

            try {
                val updateMembersInfo = updates.membershipPackage.memberships.memberships.map { update ->
                    val memberContext = deserializer.deserialize(update.memberContext.array())
                        ?: throw CordaRuntimeException("Invalid member context")
                    val mgmContext = deserializer.deserialize(update.mgmContext.array())
                        ?: throw CordaRuntimeException("Invalid MGM context")
                    memberInfoFactory.create(
                        memberContext.toSortedMap(),
                        mgmContext.toSortedMap()
                    )
                }.associateBy { it.id }

                val persistentMemberInfoRecords = updateMembersInfo.entries.map { (id, memberInfo) ->
                    // TODO - CORE-5811 - verify signatures in signed member infos.
                    val persistentMemberInfo = PersistentMemberInfo(
                        viewOwningMember.toAvro(),
                        memberInfo.memberProvidedContext.toWire(),
                        memberInfo.mgmProvidedContext.toWire(),
                    )
                    Record(
                        MEMBER_LIST_TOPIC,
                        "${viewOwningMember.shortHash}-$id",
                        persistentMemberInfo
                    )
                }

                val packageHash = updates.membershipPackage.memberships.hashCheck?.toCorda()
                val groupReader = membershipGroupReaderProvider.getGroupReader(viewOwningMember)
                val allRecords = if (packageHash == null) {
                    persistentMemberInfoRecords + createSynchronisationRequestMessage(
                        groupReader,
                        viewOwningMember,
                        mgm,
                    )
                } else {
                    val knownMembers = groupReader.lookup().filter { !it.isMgm }.associateBy { it.id }
                    val allMembers = knownMembers + updateMembersInfo
                    val expectedHash = merkleTreeGenerator.generateTree(allMembers.values).root
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

                publisher.publish(allRecords).first().get(PUBLICATION_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            } catch (e: Exception) {
                logger.warn("Failed to process membership updates received by ${viewOwningMember.x500Name}.", e)
                // TODO - CORE-5813 - trigger sync protocol.
                logger.warn(
                    "Cannot recover from failure to process membership updates. ${viewOwningMember.x500Name}" +
                        " cannot initiate sync protocol with MGM as this is not implemented."
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
            memberIdentity.x500Name
        ) ?: throw CordaRuntimeException("Unknown member $memberIdentity")
        val memberHash = merkleTreeGenerator.generateTree(listOf(member))
            .root
            .toAvro()
        return p2pRecordsFactory.createAuthenticatedMessageRecord(
            source = memberIdentity.toAvro(),
            destination = mgm.toAvro(),
            content = MembershipSyncRequest(
                DistributionMetaData(
                    UUID.randomUUID().toString(),
                    clock.instant(),
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

    private fun handleEvent(event: LifecycleEvent, coordinator: LifecycleCoordinator) {
        logger.info("Received event $event.")
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
            val groupReader = membershipGroupReaderProvider.getGroupReader(request.member)
            val syncRequest = createSynchronisationRequestMessage(
                groupReader,
                request.member,
                request.mgm,
            )
            publisher.publish(listOf(syncRequest)).forEach {
                it.get(PUBLICATION_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            }
            logger.info("Member ${request.member} had asked the MGM for a sync package.")
        } catch (e: Exception) {
            logger.warn("Sync request for ${request.member} failed!", e)
        }
    }

    private fun handleStartEvent(coordinator: LifecycleCoordinator) {
        logger.info("Handling start event.")
        componentHandle?.close()
        componentHandle = coordinator.followStatusChangesByName(
            setOf(
                LifecycleCoordinatorName.forComponent<ConfigurationReadService>(),
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
        logger.info("Handling config changed event.")
        _publisher?.close()
        _publisher = publisherFactory.createPublisher(
            PublisherConfig("member-synchronisation-service"),
            event.config.getConfig(MESSAGING_CONFIG)
        )
        _publisher?.start()
        activate(event.config.getConfig(MEMBERSHIP_CONFIG))
    }
}
