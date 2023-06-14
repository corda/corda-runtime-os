package net.corda.membership.impl.synchronisation

import net.corda.configuration.read.ConfigChangedEvent
import net.corda.configuration.read.ConfigurationReadService
import net.corda.crypto.cipher.suite.KeyEncodingService
import net.corda.crypto.cipher.suite.SignatureVerificationService
import net.corda.crypto.cipher.suite.merkle.MerkleTreeProvider
import net.corda.crypto.core.bytes
import net.corda.crypto.core.toAvro
import net.corda.crypto.core.toCorda
import net.corda.avro.serialization.CordaAvroDeserializer
import net.corda.avro.serialization.CordaAvroSerializationFactory
import net.corda.data.KeyValuePairList
import net.corda.data.crypto.wire.CryptoSignatureSpec
import net.corda.data.crypto.wire.CryptoSignatureWithKey
import net.corda.data.membership.PersistentMemberInfo
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
import net.corda.membership.lib.toSortedMap
import net.corda.membership.lib.toWire
import net.corda.membership.p2p.helpers.MerkleTreeGenerator
import net.corda.membership.p2p.helpers.P2pRecordsFactory
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
@Suppress("LongParameterList")
class MemberSynchronisationServiceImpl internal constructor(
    private val publisherFactory: PublisherFactory,
    private val configurationReadService: ConfigurationReadService,
    coordinatorFactory: LifecycleCoordinatorFactory,
    private val serializationFactory: CordaAvroSerializationFactory,
    private val memberInfoFactory: MemberInfoFactory,
    private val membershipGroupReaderProvider: MembershipGroupReaderProvider,
    private val verifier: Verifier,
    private val membersReader: LocallyHostedMembersReader,
    private val p2pRecordsFactory: P2pRecordsFactory,
    private val merkleTreeGenerator: MerkleTreeGenerator,
    private val clock: Clock,
    private val membershipPersistenceClient: MembershipPersistenceClient,
    private val groupParametersFactory: GroupParametersFactory,
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
        publisherFactory,
        configurationReadService,
        coordinatorFactory,
        cordaAvroSerializationFactory,
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
        P2pRecordsFactory(
            cordaAvroSerializationFactory,
            UTCClock(),
        ),
        MerkleTreeGenerator(
            merkleTreeProvider,
            cordaAvroSerializationFactory,
        ),
        UTCClock(),
        membershipPersistenceClient,
        groupParametersFactory,
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
        membersReader.readAllLocalMembers().forEach {
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

        private val deserializer: CordaAvroDeserializer<KeyValuePairList> =
            serializationFactory.createAvroDeserializer({
                logger.error("Deserialization of KeyValuePairList from MembershipPackage failed while processing membership updates.")
            }, KeyValuePairList::class.java)

        private fun delayToNextRequestInMilliSeconds(): Long {
            // Add noise to prevent all the members to ask for sync in the same time
            return maxDelayBetweenRequestsInMillis -
                    (random.nextDouble() * 0.1 * maxDelayBetweenRequestsInMillis).toLong()
        }

        private fun parseGroupParameters(
            mgm: MemberInfo,
            membershipPackage: MembershipPackage
        ) = with(membershipPackage.groupParameters) {
            verifier.verify(
                mgm.sessionInitiationKeys,
                mgmSignature,
                mgmSignatureSpec,
                groupParameters.array()
            )
            groupParametersFactory.create(this)
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
            logger.debug { "Member $viewOwningMember received membership updates from $mgm." }

            return try {
                cancelCurrentRequestAndScheduleNewOne(viewOwningMember, mgm)
                val updateMembersInfo = updates.membershipPackage.memberships.memberships.map { update ->
                    verifier.verify(
                        update.memberContext.signature,
                        update.memberContext.signatureSpec,
                        update.memberContext.data.array()
                    )
                    verifyMgmSignature(
                        update.mgmContext.signature,
                        update.mgmContext.signatureSpec,
                        update.memberContext.data.array(),
                        update.mgmContext.data.array(),
                    )
                    val memberContext = deserializer.deserialize(update.memberContext.data.array())
                        ?: throw CordaRuntimeException("Invalid member context")
                    val mgmContext = deserializer.deserialize(update.mgmContext.data.array())
                        ?: throw CordaRuntimeException("Invalid MGM context")
                    memberInfoFactory.create(
                        memberContext.toSortedMap(),
                        mgmContext.toSortedMap()
                    )
                }.associateBy { it.id }

                val persistentMemberInfoRecords = updateMembersInfo.entries.map { (id, memberInfo) ->
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
                    val knownMembers = groupReader.lookup(MembershipStatusFilter.ACTIVE_OR_SUSPENDED)
                        .filter { !it.isMgm }.associateBy { it.id }
                    val viewOwnerShortHash = viewOwningMember.shortHash.value
                    val latestViewOwnerMemberInfo = updateMembersInfo[viewOwnerShortHash] ?: knownMembers[viewOwnerShortHash]
                    val expectedHash = if (latestViewOwnerMemberInfo?.status == MEMBER_STATUS_SUSPENDED) {
                        merkleTreeGenerator.generateTree(listOf(latestViewOwnerMemberInfo)).root
                    } else {
                        val allMembers = knownMembers + updateMembersInfo
                        merkleTreeGenerator.generateTree(allMembers.values).root
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


                val persistRecords = groupReader.lookup().firstOrNull { it.isMgm }?.let {
                    val groupParameters = parseGroupParameters(
                        it,
                        updates.membershipPackage
                    )
                        membershipPersistenceClient
                            .persistGroupParameters(viewOwningMember, groupParameters)
                            .createAsyncCommands()
                } ?: throw CordaRuntimeException(
                    "Could not find MGM info in the member list for member ${viewOwningMember.x500Name}"
                )
                allRecords + persistRecords
            } catch (e: Exception) {
                logger.warn(
                    "Failed to process membership updates received by ${viewOwningMember.x500Name}. Will retry again soon.",
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

    private fun verifyMgmSignature(
        mgmSignature: CryptoSignatureWithKey,
        mgmSignatureSpec: CryptoSignatureSpec,
        vararg leaves: ByteArray,
    ) {
        val data = merkleTreeGenerator.createTree(leaves.toList())
            .root.bytes
        verifier.verify(mgmSignature, mgmSignatureSpec, data)
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

    private fun createSynchroniseNowRequest(
        viewOwningMember: HoldingIdentity,
        mgm: HoldingIdentity
    ): List<Record<*, *>> {
        return try {
            listOf(
                createSynchronisationRequestMessage(
                    groupReader = membershipGroupReaderProvider.getGroupReader(viewOwningMember),
                    memberIdentity = viewOwningMember,
                    mgm = mgm,
                ),
            )
        } catch (e: Exception) {
            logger.warn("Failed to trigger an immediate sync, will schedule on to be triggered later.", e)
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
            val groupReader = membershipGroupReaderProvider.getGroupReader(request.member)
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
        _publisher = publisherFactory.createPublisher(
            PublisherConfig("member-synchronisation-service"),
            event.config.getConfig(MESSAGING_CONFIG)
        )
        _publisher?.start()
        activate(event.config.getConfig(MEMBERSHIP_CONFIG))
    }
}
