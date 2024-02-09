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
import net.corda.p2p.linkmanager.TraceableItem
import net.corda.p2p.linkmanager.metrics.recordOutboundMessagesMetric
import net.corda.p2p.linkmanager.metrics.recordOutboundSessionMessagesMetric

@Suppress("LongParameterList", "TooManyFunctions")
internal class OutboundMessageProcessor(
    private val sessionManager: SessionManager,
    private val linkManagerHostingMap: LinkManagerHostingMap,
    private val groupPolicyProvider: GroupPolicyProvider,
    private val membershipGroupReaderProvider: MembershipGroupReaderProvider,
    private val inboundAssignmentListener: InboundAssignmentListener,
    private val messagesPendingSession: PendingSessionMessageQueues,
    private val clock: Clock,
    private val messageConverter: MessageConverter,
    private val networkMessagingValidator: NetworkMessagingValidator =
        NetworkMessagingValidator(membershipGroupReaderProvider),
) : EventLogProcessor<String, AppMessage> {

    override val keyClass = String::class.java
    override val valueClass = AppMessage::class.java
    private var logger = LoggerFactory.getLogger(this::class.java.name)

    companion object {
        private const val tracingEventName = "P2P Link Manager Outbound Event"
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
                state.messages.forEach {
                    recordOutboundSessionMessagesMetric(state.sessionCounterparties.ourId)
                }
                state.messages.flatMap {
                    listOf(
                        Record(Schemas.P2P.LINK_OUT_TOPIC, LinkManager.generateKey(), it.second),
                        Record(Schemas.P2P.SESSION_OUT_PARTITIONS, it.first, SessionPartitions(partitions.toList()))
                    )
                }
            }
        }
    }

    private fun ttlExpired(ttl: Instant?): Boolean {
        if (ttl == null) return false
        val currentTimeInTimeMillis = clock.instant()
        return currentTimeInTimeMillis >= ttl
    }

    override fun onNext(events: List<EventLogRecord<String, AppMessage>>): List<Record<String, *>> {
        val authenticatedMessages = mutableListOf<TraceableItem<AuthenticatedMessageAndKey, AppMessage>>()
        val unauthenticatedMessages = mutableListOf<TraceableItem<OutboundUnauthenticatedMessage, AppMessage>>()
        for (event in events) {
            when (val message = event.value?.message) {
                is AuthenticatedMessage -> {
                    authenticatedMessages += TraceableItem(AuthenticatedMessageAndKey(message, event.key), event)
                    recordOutboundMessagesMetric(message)
                }
                is OutboundUnauthenticatedMessage -> {
                    unauthenticatedMessages += TraceableItem(message, event)
                    recordOutboundMessagesMetric(message)
                }
                null -> {
                    logger.warn("Message is null.")
                }
                else -> {
                    logger.warn("Unknown message type: ${message::class.java}.")
                }
            }
        }

        val results = unauthenticatedMessages.map { (message, event) ->
            TraceableItem(processUnauthenticatedMessage(message), event)
        } + processAuthenticatedMessages(authenticatedMessages)

        for (result in results) {
            result.source?.let { originalRecord ->
                traceEventProcessing(originalRecord, tracingEventName) { result.item }
            }
        }
        return results.map { it.item }.flatten()
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
        processAuthenticatedMessages(listOf(TraceableItem(messageAndKey, null)), true).flatMap { it.item }

    private fun processAuthenticatedMessages(
        messagesWithKeys: List<TraceableItem<AuthenticatedMessageAndKey, AppMessage>>,
        isReplay: Boolean = false
    ): List<TraceableItem<List<Record<String, *>>, AppMessage>> {
        val validatedMessages = messagesWithKeys.map { message ->
            message to validateAndCheckIfSessionNeeded(message.item, isReplay)
        }
        val messagesWithSession = validatedMessages.mapNotNull { (message, result) ->
            if (result is ValidateAuthenticatedMessageResult.SessionNeeded) {
                TraceableItem(result, message.source)
            } else {
                null
            }
        }
        val messageWithNoSession = validatedMessages.mapNotNull { (message, result) ->
            if (result is ValidateAuthenticatedMessageResult.NoSessionNeeded) {
                TraceableItem(result.records, message.source)
            } else {
                null
            }
        }
        return messageWithNoSession + processRemoteAuthenticatedMessage(messagesWithSession, isReplay)
    }

    internal sealed class ValidateAuthenticatedMessageResult {
        data class SessionNeeded(
            val messageWithKey: AuthenticatedMessageAndKey,
            val markerRecords: List<Record<String, *>>
        ): ValidateAuthenticatedMessageResult()
        data class NoSessionNeeded(val records: List<Record<String, *>>): ValidateAuthenticatedMessageResult()
    }

    /**
     * validates an AuthenticatedMessage and checks if a session is needed.
     *
     * [isReplay] - If the message is being replayed we don't persist a [LinkManagerSentMarker] as there is already
     * a marker for this message. If the process is restarted we reread the original marker.
     *
     * @return
     * [ValidateAuthenticatedMessageResult.SessionNeeded] if validation succeeds and the destination identity is hosted in a
     * different corda cluster. This contains the authenticated message to be authenticated (and optionally encrypted by the session, along
     * with some marker records.
     * [ValidateAuthenticatedMessageResult.NoSessionNeeded] in all other cases. This contains some marker records and the message to be
     * looped back (if validation succeeded).
     */
    private fun validateAndCheckIfSessionNeeded(
        messageAndKey: AuthenticatedMessageAndKey,
        isReplay: Boolean = false
    ): ValidateAuthenticatedMessageResult {
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
            return ValidateAuthenticatedMessageResult.NoSessionNeeded(listOf(recordForLMDiscardedMarker(messageAndKey, discardReason)))
        }

        if (ttlExpired(messageAndKey.message.header.ttl)) {
            val expiryMarker = recordForTTLExpiredMarker(messageAndKey.message.header.messageId)
            return if (isReplay) {
                ValidateAuthenticatedMessageResult.NoSessionNeeded(listOf(expiryMarker))
            } else {
                ValidateAuthenticatedMessageResult.NoSessionNeeded(
                    listOf(recordForLMProcessedMarker(messageAndKey, messageAndKey.message.header.messageId), expiryMarker)
                )
            }
        }

        val source = messageAndKey.message.header.source.toCorda()
        val destination = messageAndKey.message.header.destination.toCorda()
        val destinationMemberInfo = membershipGroupReaderProvider.lookup(
            source, destination, messageAndKey.message.header.statusFilter
        ) ?: return if (isReplay) {
            ValidateAuthenticatedMessageResult.NoSessionNeeded(emptyList())
        } else {
            ValidateAuthenticatedMessageResult.NoSessionNeeded(
                listOf(recordForLMProcessedMarker(messageAndKey, messageAndKey.message.header.messageId))
            )
        }.also {
            logger.warn("Trying to send authenticated message (${messageAndKey.message.header.messageId}) from $source to $destination, " +
                    "but the destination is not part of the network. Filter was " +
                    "${messageAndKey.message.header.statusFilter} Message will be retried later.")
        }

        if (linkManagerHostingMap.isHostedLocallyAndSessionKeyMatch(destinationMemberInfo)) {
            recordInboundMessagesMetric(messageAndKey.message)
            return if (isReplay) {
                ValidateAuthenticatedMessageResult.NoSessionNeeded(
                    listOf(
                        Record(Schemas.P2P.P2P_IN_TOPIC, messageAndKey.key, AppMessage(messageAndKey.message)),
                        recordForLMReceivedMarker(messageAndKey.message.header.messageId)
                    )
                )
            } else {
                ValidateAuthenticatedMessageResult.NoSessionNeeded(
                    listOf(
                        Record(Schemas.P2P.P2P_IN_TOPIC, messageAndKey.key, AppMessage(messageAndKey.message)),
                        recordForLMProcessedMarker(messageAndKey, messageAndKey.message.header.messageId),
                        recordForLMReceivedMarker(messageAndKey.message.header.messageId)
                    )
                )
            }
        } else {
            val markers = if (isReplay) {
                emptyList()
            } else {
                listOf(recordForLMProcessedMarker(messageAndKey, messageAndKey.message.header.messageId))
            }
            return ValidateAuthenticatedMessageResult.SessionNeeded(messageAndKey, markers)
        }
    }

    private fun processRemoteAuthenticatedMessage(
        validationResults: List<TraceableItem<ValidateAuthenticatedMessageResult.SessionNeeded, AppMessage>>,
        isReplay: Boolean = false
    ): List<TraceableItem<List<Record<String, *>>, AppMessage>> {
        return sessionManager.processOutboundMessages(validationResults) { validationResult ->
            validationResult.item.messageWithKey
        }.map { (message, state) ->
                when (state) {
                is SessionManager.SessionState.NewSessionsNeeded -> {
                    logger.trace {
                        "No existing session with ${message.item.messageWithKey.message.header.destination}. Initiating a new one.."
                    }
                    if (!isReplay) messagesPendingSession.queueMessage(message.item.messageWithKey, state.sessionCounterparties)
                    TraceableItem(recordsForNewSessions(state) + message.item.markerRecords, message.source)
                }
                is SessionManager.SessionState.SessionEstablished -> {
                    logger.trace {
                        "Session already established with ${message.item.messageWithKey.message.header.destination}. Using this to send" +
                            " outbound message."
                    }
                    TraceableItem(
                        recordsForSessionEstablished(state, message.item.messageWithKey) + message.item.markerRecords,
                        message.source
                    )
                }
                is SessionManager.SessionState.SessionAlreadyPending -> {
                    logger.trace {
                        "Session already pending with ${message.item.messageWithKey.message.header.destination}. Message queued until " +
                            "session is established."
                    }
                    if (!isReplay) messagesPendingSession.queueMessage(message.item.messageWithKey, state.sessionCounterparties)
                    TraceableItem(message.item.markerRecords, message.source)
                }
                is SessionManager.SessionState.CannotEstablishSession -> {
                    TraceableItem(message.item.markerRecords, message.source)
                }
            }
        }
    }

    private fun recordsForSessionEstablished(
        state: SessionManager.SessionState.SessionEstablished,
        messageAndKey: AuthenticatedMessageAndKey
    ): List<Record<String, *>> {
        return messageConverter.recordsForSessionEstablished(
            sessionManager,
            state.session,
            state.sessionCounterparties.serial,
            messageAndKey,
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

}
