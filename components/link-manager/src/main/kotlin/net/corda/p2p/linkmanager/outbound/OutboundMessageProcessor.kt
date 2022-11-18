package net.corda.p2p.linkmanager.outbound

import net.corda.messaging.api.processor.EventLogProcessor
import net.corda.messaging.api.records.EventLogRecord
import net.corda.messaging.api.records.Record
import net.corda.p2p.AuthenticatedMessageAndKey
import net.corda.p2p.SessionPartitions
import net.corda.p2p.app.AppMessage
import net.corda.p2p.app.AuthenticatedMessage
import net.corda.p2p.app.UnauthenticatedMessage
import net.corda.p2p.linkmanager.LinkManager
import net.corda.p2p.linkmanager.grouppolicy.LinkManagerGroupPolicyProvider
import net.corda.p2p.linkmanager.hosting.LinkManagerHostingMap
import net.corda.p2p.linkmanager.membership.LinkManagerMembershipGroupReader
import net.corda.p2p.linkmanager.sessions.PendingSessionMessageQueues
import net.corda.p2p.linkmanager.common.MessageConverter
import net.corda.p2p.linkmanager.inbound.InboundAssignmentListener
import net.corda.p2p.linkmanager.sessions.SessionManager
import net.corda.p2p.linkmanager.sessions.recordsForSessionEstablished
import net.corda.p2p.markers.TtlExpiredMarker
import net.corda.p2p.markers.AppMessageMarker
import net.corda.p2p.markers.LinkManagerDiscardedMarker
import net.corda.p2p.markers.LinkManagerReceivedMarker
import net.corda.p2p.markers.LinkManagerSentMarker
import net.corda.p2p.markers.LinkManagerProcessedMarker
import net.corda.p2p.markers.Component
import net.corda.schema.Schemas
import net.corda.utilities.time.Clock
import net.corda.v5.base.util.debug
import net.corda.v5.base.util.trace
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.toCorda
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.Instant

@Suppress("LongParameterList", "TooManyFunctions")
internal class OutboundMessageProcessor(
    private val sessionManager: SessionManager,
    private val linkManagerHostingMap: LinkManagerHostingMap,
    private val groups: LinkManagerGroupPolicyProvider,
    private val members: LinkManagerMembershipGroupReader,
    private val inboundAssignmentListener: InboundAssignmentListener,
    private val messagesPendingSession: PendingSessionMessageQueues,
    private val clock: Clock
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
        val records = mutableListOf<Record<String, *>>()
        for (event in events) {
            records += processEvent(event)
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
            }
            is UnauthenticatedMessage -> {
                processUnauthenticatedMessage(message)
            }
            else -> {
                logger.warn("Unknown message type: ${message::class.java}")
                emptyList()
            }
        }
    }

    private fun checkSourceAndDestinationValid(
        source: HoldingIdentity, destination: HoldingIdentity
    ): Pair<Boolean, String?> {
         return if (source.groupId != destination.groupId) {
            return Pair(false, "group IDs do not match")
        } else if (!linkManagerHostingMap.isHostedLocally(source)) {
            return Pair(false, "source ID is not locally hosted")
        } else Pair(true, null)
    }

    private fun processUnauthenticatedMessage(message: UnauthenticatedMessage): List<Record<String, *>> {
        logger.debug { "Processing outbound ${message.javaClass} to ${message.header.destination}." }

        val (sourceAndDestinationValid, discardReason) = checkSourceAndDestinationValid(
            message.header.source.toCorda(), message.header.destination.toCorda()
        )
        if (!sourceAndDestinationValid) {
            logger.warn("Dropping outbound unauthenticated message from ${message.header.source} to ${message.header.destination} as the " +
                    discardReason)
            return emptyList()
        }

        val destMemberInfo = members.getMemberInfo(message.header.source.toCorda(), message.header.destination.toCorda())
        if (linkManagerHostingMap.isHostedLocally(message.header.destination.toCorda())) {
            return listOf(Record(Schemas.P2P.P2P_IN_TOPIC, LinkManager.generateKey(), AppMessage(message)))
        } else if (destMemberInfo != null) {
            val source = message.header.source.toCorda()
            val groupInfo = groups.getGroupInfo(source)
            if (groupInfo == null) {
                logger.warn("Could not find the group information in the GroupPolicyProvider for $source. The message was discarded.")
                return emptyList()
            }

            val linkOutMessage = MessageConverter.linkOutFromUnauthenticatedMessage(message, destMemberInfo, groupInfo)
            return listOf(Record(Schemas.P2P.LINK_OUT_TOPIC, LinkManager.generateKey(), linkOutMessage))
        } else {
            logger.warn("Trying to send unauthenticated message from ${message.header.source.toCorda()} " +
                    "to ${message.header.destination.toCorda()}, but destination is not part of the network. Message was discarded.")
            return emptyList()
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

        val (sourceAndDestinationValid, discardReason) = checkSourceAndDestinationValid(
            messageAndKey.message.header.source.toCorda(), messageAndKey.message.header.destination.toCorda()
        )

        if (!sourceAndDestinationValid) {
            logger.warn("Dropping outbound authenticated message ${messageAndKey.message.header.messageId}" +
                    " from ${messageAndKey.message.header.source} to ${messageAndKey.message.header.destination} as the $discardReason")
            return listOf(recordForLMDiscardedMarker(messageAndKey, discardReason!!))
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
        if (linkManagerHostingMap.isHostedLocally(destination)) {
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
        } else if (members.getMemberInfo(source, destination) != null) {
            val markers = if (isReplay) {
                emptyList()
            } else {
                listOf(recordForLMProcessedMarker(messageAndKey, messageAndKey.message.header.messageId))
            }
            return processNoTtlRemoteAuthenticatedMessage(messageAndKey, isReplay) + markers
        } else {
            logger.warn("Trying to send authenticated message (${messageAndKey.message.header.messageId}) from $source to $destination, " +
                    "but the destination is not part of the network. Message will be retried later.")
            return if (isReplay) {
                emptyList()
            } else {
                listOf(recordForLMProcessedMarker(messageAndKey, messageAndKey.message.header.messageId))
            }
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
                if (!isReplay) messagesPendingSession.queueMessage(messageAndKey)
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
                if (!isReplay) messagesPendingSession.queueMessage(messageAndKey)
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
        val list = sessionManager.recordsForSessionEstablished(groups, members, state.session, messageAndKey).toMutableList()
        list.add(recordForLMSentMarker(messageAndKey.message.header.messageId))
        return list
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

    private fun recordForLMSentMarker(
        messageId: String
    ): Record<String, AppMessageMarker> {
        val marker = AppMessageMarker(LinkManagerSentMarker(), clock.instant().toEpochMilli())
        return Record(Schemas.P2P.P2P_OUT_MARKERS, messageId, marker)
    }

    private fun recordForLMDiscardedMarker(message: AuthenticatedMessageAndKey,
                                           reason: String): Record<String, AppMessageMarker> {
        val marker = AppMessageMarker(LinkManagerDiscardedMarker(message, reason), clock.instant().toEpochMilli())
        return Record(Schemas.P2P.P2P_OUT_MARKERS, message.message.header.messageId, marker)
    }

}
