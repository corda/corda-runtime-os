package net.corda.p2p.linkmanager.outbound

import net.corda.data.identity.HoldingIdentity
import net.corda.data.p2p.AuthenticatedMessageAndKey
import net.corda.data.p2p.SessionPartitions
import net.corda.data.p2p.app.AppMessage
import net.corda.data.p2p.app.AuthenticatedMessage
import net.corda.data.p2p.app.InboundUnauthenticatedMessage
import net.corda.data.p2p.app.InboundUnauthenticatedMessageHeader
import net.corda.data.p2p.app.MembershipStatusFilter
import net.corda.data.p2p.app.OutboundUnauthenticatedMessage
import net.corda.data.p2p.markers.AppMessageMarker
import net.corda.data.p2p.markers.Component
import net.corda.data.p2p.markers.LinkManagerDiscardedMarker
import net.corda.data.p2p.markers.LinkManagerProcessedMarker
import net.corda.data.p2p.markers.LinkManagerReceivedMarker
import net.corda.data.p2p.markers.LinkManagerSentMarker
import net.corda.data.p2p.markers.TtlExpiredMarker
import net.corda.membership.grouppolicy.GroupPolicyProvider
import net.corda.membership.read.MembershipGroupReaderProvider
import net.corda.messaging.api.processor.EventLogProcessor
import net.corda.messaging.api.records.EventLogRecord
import net.corda.messaging.api.records.Record
import net.corda.metrics.CordaMetrics
import net.corda.p2p.linkmanager.LinkManager
import net.corda.p2p.linkmanager.common.MessageConverter
import net.corda.p2p.linkmanager.grouppolicy.networkType
import net.corda.p2p.linkmanager.hosting.LinkManagerHostingMap
import net.corda.p2p.linkmanager.inbound.InboundAssignmentListener
import net.corda.p2p.linkmanager.membership.NetworkMessagingValidator
import net.corda.p2p.linkmanager.membership.lookup
import net.corda.p2p.linkmanager.metrics.recordInboundMessagesMetric
import net.corda.p2p.linkmanager.sessions.PendingSessionMessageQueues
import net.corda.p2p.linkmanager.sessions.SessionManager
import net.corda.schema.Schemas
import net.corda.tracing.traceEventProcessing
import net.corda.utilities.Either
import net.corda.utilities.debug
import net.corda.utilities.time.Clock
import net.corda.utilities.trace
import net.corda.virtualnode.toCorda
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.Instant
import net.corda.membership.lib.exceptions.BadGroupPolicyException

@Suppress("LongParameterList", "TooManyFunctions")
internal class OutboundMessageProcessor(
    private val sessionManager: SessionManager,
    private val linkManagerHostingMap: LinkManagerHostingMap,
    private val groupPolicyProvider: GroupPolicyProvider,
    private val membershipGroupReaderProvider: MembershipGroupReaderProvider,
    private val inboundAssignmentListener: InboundAssignmentListener,
    private val messagesPendingSession: PendingSessionMessageQueues,
    private val clock: Clock,
    private val networkMessagingValidator: NetworkMessagingValidator =
        NetworkMessagingValidator(membershipGroupReaderProvider)
) : EventLogProcessor<String, AppMessage> {

    override val keyClass = String::class.java
    override val valueClass = AppMessage::class.java
    private var logger = LoggerFactory.getLogger(this::class.java.name)

    companion object {
        fun recordsForNewSessions(
            state: SessionManager.SessionState.NewSessionsNeeded,
            inboundAssignmentListener: InboundAssignmentListener,
            logger: Logger
        ): List<Record<String, *>> {
            val partitions = inboundAssignmentListener.getCurrentlyAssignedPartitions()
            return if (partitions.isEmpty()) {
                val sessionIds = state.messages.map { it.first }
                logger.warn(
                    "No partitions from topic ${Schemas.P2P.LINK_IN_TOPIC} are currently assigned to the inbound message processor." +
                        " Sessions: $sessionIds will not be initiated."
                )
                emptyList()
            } else {
                state.messages.flatMap {
                    listOf(
                        Record(Schemas.P2P.LINK_OUT_TOPIC, LinkManager.generateKey(), it.second),
                        Record(Schemas.P2P.SESSION_OUT_PARTITIONS, it.first, SessionPartitions(partitions.toList()))
                    )
                }
            }
        }
    }

    override fun onNext(events: List<EventLogRecord<String, AppMessage>>): List<Record<*, *>> {
        val records = mutableListOf<Record<*, *>>()
        for (event in events) {
            records += traceEventProcessing(event,"P2P Link Manager Outbound Event") { processEvent(event)}
        }
        return records
    }

    private fun ttlExpired(ttl: Instant?): Boolean {
        if (ttl == null) return false
        val currentTimeInTimeMillis = clock.instant()
        return currentTimeInTimeMillis >= ttl
    }

    private fun processEvent(event: EventLogRecord<String, AppMessage>): List<Record<String, *>> {

        val message = event.value?.message
        if (message == null) {
            logger.error("Received null message. The message was discarded.")
            return emptyList()
        }

        return when (message) {
            is AuthenticatedMessage -> {
                processAuthenticatedMessage(AuthenticatedMessageAndKey(message, event.key))
                    .also { recordOutboundMessagesMetric(message) }
            }
            is OutboundUnauthenticatedMessage -> {
                processUnauthenticatedMessage(message)
                    .also { recordOutboundMessagesMetric(message) }
            }
            else -> {
                logger.warn("Unknown message type: ${message::class.java}")
                emptyList()
            }
        }
    }

    private fun checkSourceAndDestinationValid(
        source: HoldingIdentity, destination: HoldingIdentity, filter: MembershipStatusFilter = MembershipStatusFilter.ACTIVE
    ): String? {
        val cordaSource = try {
            source.toCorda()
        } catch (e: Exception) {
            return "source '${source.x500Name}' is not a valid X500 name: ${e.message}"
        }
        val cordaDestination = try {
            destination.toCorda()
        } catch (e: Exception) {
            return "destination '${destination.x500Name}' is not a valid X500 name: ${e.message}"
        }
        if (source.groupId != destination.groupId) {
            return "group IDs do not match"
        }
        if (!linkManagerHostingMap.isHostedLocally(cordaSource)) {
            return "source ID is not locally hosted"
        }
        // Perform a stricter locally-hosted check on the destination, which includes session key matching, to guard
        // against scenarios such as when a message is being sent to a duplicate holding identity on another cluster,
        // but is instead routed to a matching holding identity which is hosted locally.
        membershipGroupReaderProvider.lookup(cordaSource, cordaDestination, filter)?.let {
            if (linkManagerHostingMap.isHostedLocallyAndSessionKeyMatch(it)) {
                return validateCanMessage(cordaSource, cordaDestination, outbound = true, inbound = true)
            }
        }
        return validateCanMessage(cordaSource, cordaDestination, outbound = true)
    }

    /**
     * Validates that a message can be sent between network participants in either an outbound or inbound direction,
     * or both.
     *
     * Returns error message if either outbound or inbound validation failed or null if validation was successful.
     */
    private fun validateCanMessage(
        source: net.corda.virtualnode.HoldingIdentity,
        destination: net.corda.virtualnode.HoldingIdentity,
        outbound: Boolean = false,
        inbound: Boolean = false
    ) : String? {
        val outResult = if(outbound) {
            when (val result = networkMessagingValidator.validateOutbound(source, destination)) {
                is Either.Left -> null
                is Either.Right -> result.b
            }
        } else null

        val inResult = if(inbound) {
            when (val result = networkMessagingValidator.validateInbound(source, destination)) {
                is Either.Left -> null
                is Either.Right -> result.b
            }
        } else null

        return outResult ?: inResult
    }

    private fun processUnauthenticatedMessage(message: OutboundUnauthenticatedMessage): List<Record<String, *>> {
        logger.debug { "Processing outbound message ${message.header.messageId} to ${message.header.destination}." }

        val discardReason = checkSourceAndDestinationValid(
            message.header.source, message.header.destination
        )
        if (discardReason != null) {
            logger.warn(
                "Dropping outbound unauthenticated message ${message.header.messageId} " +
                "from ${message.header.source} to ${message.header.destination} as the " +
                discardReason
            )
            return emptyList()
        }

        val destinationMemberInfo = membershipGroupReaderProvider.lookup(
            message.header.source.toCorda(),
            message.header.destination.toCorda()
        ) ?: return emptyList<Record<String, *>>().also {
            logger.warn("Trying to send unauthenticated message ${message.header.messageId} from ${message.header.source.toCorda()} " +
                    "to ${message.header.destination.toCorda()}, but destination is not part of the network. Message was discarded.")
        }
        val inboundMessage = InboundUnauthenticatedMessage(
            InboundUnauthenticatedMessageHeader(
                message.header.subsystem,
                message.header.messageId,
            ),
            message.payload,
        )
        if (linkManagerHostingMap.isHostedLocallyAndSessionKeyMatch(destinationMemberInfo)) {
            recordInboundMessagesMetric(inboundMessage)
            return listOf(Record(Schemas.P2P.P2P_IN_TOPIC, LinkManager.generateKey(), AppMessage(inboundMessage)))
        } else {
            val source = message.header.source.toCorda()
            val p2pParams = try {
                groupPolicyProvider.getP2PParameters(source)
            } catch (except: BadGroupPolicyException) {
                logger.warn("The group policy data is unavailable or cannot be parsed for $source. Error: ${except.message}. The message" +
                    " ${message.header.messageId} was discarded.")
                return emptyList()
            }
            if (p2pParams == null) {
                logger.warn(
                    "Could not find the p2p parameters in the GroupPolicyProvider for $source. " +
                    "The message ${message.header.messageId} was discarded."
                )
                return emptyList()
            }

            val linkOutMessage = MessageConverter.linkOutFromUnauthenticatedMessage(
                inboundMessage,
                source,
                destinationMemberInfo,
                p2pParams.networkType
            )
            return listOf(Record(Schemas.P2P.LINK_OUT_TOPIC, LinkManager.generateKey(), linkOutMessage))
        }
    }

    fun processReplayedAuthenticatedMessage(messageAndKey: AuthenticatedMessageAndKey): List<Record<String, *>> =
        processAuthenticatedMessage(messageAndKey, true)

    /**
     * processed an AuthenticatedMessage returning a list of records to be persisted.
     *
     * [isReplay] - If the message is being replayed we don't persist a [LinkManagerSentMarker] as there is already
     * a marker for this message. If the process is restarted we reread the original marker.
     */

    private fun processAuthenticatedMessage(
        messageAndKey: AuthenticatedMessageAndKey,
        isReplay: Boolean = false
    ): List<Record<String, *>> {
        logger.trace {
            "Processing outbound ${messageAndKey.message.javaClass} with ID ${messageAndKey.message.header.messageId} " +
                "to ${messageAndKey.message.header.destination}."
        }

        val discardReason = checkSourceAndDestinationValid(
            messageAndKey.message.header.source, messageAndKey.message.header.destination, messageAndKey.message.header.statusFilter
        )

        if (discardReason != null) {
            logger.warn("Dropping outbound authenticated message ${messageAndKey.message.header.messageId}" +
                    " from ${messageAndKey.message.header.source} to ${messageAndKey.message.header.destination} as the $discardReason")
            return listOf(recordForLMDiscardedMarker(messageAndKey, discardReason))
        }

        if (ttlExpired(messageAndKey.message.header.ttl)) {
            val expiryMarker = recordForTTLExpiredMarker(messageAndKey.message.header.messageId)
            return if (isReplay) {
                listOf(expiryMarker)
            } else {
                listOf(
                    recordForLMProcessedMarker(messageAndKey, messageAndKey.message.header.messageId),
                    expiryMarker
                )
            }
        }

        val source = messageAndKey.message.header.source.toCorda()
        val destination = messageAndKey.message.header.destination.toCorda()
        val destinationMemberInfo = membershipGroupReaderProvider.lookup(
            source, destination, messageAndKey.message.header.statusFilter
        ) ?: return if (isReplay) {
            emptyList()
        } else {
            listOf(recordForLMProcessedMarker(messageAndKey, messageAndKey.message.header.messageId))
        }.also {
            logger.warn("Trying to send authenticated message (${messageAndKey.message.header.messageId}) from $source to $destination, " +
                    "but the destination is not part of the network. Filter was " +
                    "${messageAndKey.message.header.statusFilter} Message will be retried later.")
        }

        if (linkManagerHostingMap.isHostedLocallyAndSessionKeyMatch(destinationMemberInfo)) {
            recordInboundMessagesMetric(messageAndKey.message)
            return if (isReplay) {
                listOf(Record(Schemas.P2P.P2P_IN_TOPIC, messageAndKey.key, AppMessage(messageAndKey.message)),
                    recordForLMReceivedMarker(messageAndKey.message.header.messageId)
                )
            } else {
                listOf(Record(Schemas.P2P.P2P_IN_TOPIC, messageAndKey.key, AppMessage(messageAndKey.message)),
                    recordForLMProcessedMarker(messageAndKey, messageAndKey.message.header.messageId),
                    recordForLMReceivedMarker(messageAndKey.message.header.messageId)
                )
            }
        } else {
            val markers = if (isReplay) {
                emptyList()
            } else {
                listOf(recordForLMProcessedMarker(messageAndKey, messageAndKey.message.header.messageId))
            }
            return processNoTtlRemoteAuthenticatedMessage(messageAndKey, isReplay) + markers
        }
    }
    private fun processNoTtlRemoteAuthenticatedMessage(
        messageAndKey: AuthenticatedMessageAndKey,
        isReplay: Boolean = false
    ): List<Record<String, *>> {

        return when (val state = sessionManager.processOutboundMessage(messageAndKey)) {
            is SessionManager.SessionState.NewSessionsNeeded -> {
                logger.trace {
                    "No existing session with ${messageAndKey.message.header.destination}. " +
                        "Initiating a new one.."
                }
                if (!isReplay) messagesPendingSession.queueMessage(messageAndKey, state.sessionCounterparties)
                recordsForNewSessions(state)
            }
            is SessionManager.SessionState.SessionEstablished -> {
                logger.trace {
                    "Session already established with ${messageAndKey.message.header.destination}." +
                        " Using this to send outbound message."
                }
                recordsForSessionEstablished(state, messageAndKey)
            }
            is SessionManager.SessionState.SessionAlreadyPending -> {
                logger.trace {
                    "Session already pending with ${messageAndKey.message.header.destination}. " +
                        "Message queued until session is established."
                }
                if (!isReplay) messagesPendingSession.queueMessage(messageAndKey, state.sessionCounterparties)
                emptyList()
            }
            is SessionManager.SessionState.CannotEstablishSession -> {
                emptyList()
            }
        }
    }

    private fun recordsForSessionEstablished(
        state: SessionManager.SessionState.SessionEstablished,
        messageAndKey: AuthenticatedMessageAndKey
    ): List<Record<String, *>> {
        return sessionManager.recordsForSessionEstablished(
            state.session,
            messageAndKey,
            state.sessionCounterparties.serial
        )
    }

    private fun recordForLMProcessedMarker(
        message: AuthenticatedMessageAndKey,
        messageId: String
    ): Record<String, AppMessageMarker> {
        val marker = AppMessageMarker(LinkManagerProcessedMarker(message), clock.instant().toEpochMilli())
        return Record(Schemas.P2P.P2P_OUT_MARKERS, messageId, marker)
    }

    private fun recordForLMReceivedMarker(messageId: String): Record<String, AppMessageMarker> {
        val marker = AppMessageMarker(LinkManagerReceivedMarker(), clock.instant().toEpochMilli())
        return Record(Schemas.P2P.P2P_OUT_MARKERS, messageId, marker)
    }

    private fun recordForTTLExpiredMarker(messageId: String): Record<String, AppMessageMarker> {
        val marker = AppMessageMarker(TtlExpiredMarker(Component.LINK_MANAGER), clock.instant().toEpochMilli())
        return Record(Schemas.P2P.P2P_OUT_MARKERS, messageId, marker)
    }

    private fun recordsForNewSessions(state: SessionManager.SessionState.NewSessionsNeeded): List<Record<String, *>> {
        return recordsForNewSessions(state, inboundAssignmentListener, logger)
    }

    private fun recordForLMDiscardedMarker(message: AuthenticatedMessageAndKey,
                                           reason: String): Record<String, AppMessageMarker> {
        val marker = AppMessageMarker(LinkManagerDiscardedMarker(message, reason), clock.instant().toEpochMilli())
        return Record(Schemas.P2P.P2P_OUT_MARKERS, message.message.header.messageId, marker)
    }

    private fun recordOutboundMessagesMetric(message: AuthenticatedMessage) {
        message.header.let {
            recordOutboundMessagesMetric(it.source.x500Name, it.destination.x500Name, it.source.groupId,
                it.subsystem, message::class.java.simpleName)
        }
    }

    private fun recordOutboundMessagesMetric(message: OutboundUnauthenticatedMessage) {
        message.header.let {
            recordOutboundMessagesMetric(it.source.x500Name, it.destination.x500Name, it.source.groupId,
                it.subsystem, message::class.java.simpleName)
        }
    }

    private fun recordOutboundMessagesMetric(source: String, dest: String, group: String, subsystem: String, messageType: String) {
        CordaMetrics.Metric.OutboundMessageCount.builder()
            .withTag(CordaMetrics.Tag.SourceVirtualNode, source)
            .withTag(CordaMetrics.Tag.DestinationVirtualNode, dest)
            .withTag(CordaMetrics.Tag.MembershipGroup, group)
            .withTag(CordaMetrics.Tag.MessagingSubsystem, subsystem)
            .withTag(CordaMetrics.Tag.MessageType, messageType)
            .build().increment()
    }

}
