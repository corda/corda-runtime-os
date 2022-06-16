package net.corda.p2p.linkmanager

import net.corda.messaging.api.processor.EventLogProcessor
import net.corda.messaging.api.records.EventLogRecord
import net.corda.messaging.api.records.Record
import net.corda.p2p.AuthenticatedMessageAndKey
import net.corda.p2p.SessionPartitions
import net.corda.p2p.app.AppMessage
import net.corda.p2p.app.AuthenticatedMessage
import net.corda.p2p.app.UnauthenticatedMessage
import net.corda.p2p.linkmanager.messaging.MessageConverter
import net.corda.p2p.linkmanager.sessions.SessionManager
import net.corda.p2p.linkmanager.sessions.recordsForSessionEstablished
import net.corda.p2p.markers.TtlExpiredMarker
import net.corda.p2p.markers.AppMessageMarker
import net.corda.p2p.markers.Component
import net.corda.p2p.markers.LinkManagerReceivedMarker
import net.corda.p2p.markers.LinkManagerSentMarker
import net.corda.p2p.markers.LinkManagerReplayMarker
import net.corda.schema.Schemas
import net.corda.utilities.time.Clock
import net.corda.v5.base.util.debug
import net.corda.v5.base.util.trace
import org.slf4j.Logger
import org.slf4j.LoggerFactory

@Suppress("LongParameterList")
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

    private fun ttlExpired(ttl: Long?): Boolean {
        if (ttl == null) return false
        val currentTimeInTimeMillis = clock.instant().toEpochMilli()
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

    private fun processUnauthenticatedMessage(message: UnauthenticatedMessage): List<Record<String, *>> {
        logger.debug { "Processing outbound ${message.javaClass} to ${message.header.destination}." }
        return if (linkManagerHostingMap.isHostedLocally(message.header.destination)) {
            listOf(Record(Schemas.P2P.P2P_IN_TOPIC, LinkManager.generateKey(), AppMessage(message)))
        } else {
            val linkOutMessage = MessageConverter.linkOutFromUnauthenticatedMessage(message, groups, members)
            listOf(Record(Schemas.P2P.LINK_OUT_TOPIC, LinkManager.generateKey(), linkOutMessage))
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
        if (ttlExpired(messageAndKey.message.header.ttl)) {
            val expiryMarker = recordForTTLExpiredMarker(messageAndKey.message.header.messageId)
            return if (isReplay) {
                listOf(
                    recordForLMReplayMarker(messageAndKey, messageAndKey.message.header.messageId),
                    expiryMarker
                )
            } else {
                listOf(
                    recordForLMSentMarker(messageAndKey, messageAndKey.message.header.messageId),
                    expiryMarker
                )
            }
        }

        if (linkManagerHostingMap.isHostedLocally(messageAndKey.message.header.destination)) {
            return if (isReplay) {
                /* This code block was added to fix a race which happens if the OutboundMessageProcessor runs quicker than the
                 * DeliveryTracker. Under normal circumstances a message to locally hosted holding identity will be added and then removed
                 * from the delivery tracker, before the message is replayed (as the OutboundMessageProcessor adds both a LinkManagerSent
                 * and a LinkManagerReceived marker).
                 */
                listOf(
                    recordForLMReplayMarker(messageAndKey, messageAndKey.message.header.messageId)
                )
            } else {
                listOf(Record(Schemas.P2P.P2P_IN_TOPIC, messageAndKey.key, AppMessage(messageAndKey.message)),
                    recordForLMSentMarker(messageAndKey, messageAndKey.message.header.messageId),
                    recordForLMReceivedMarker(messageAndKey.message.header.messageId)
                )
            }
        } else {
            val markers = if (isReplay) {
                listOf(
                    recordForLMReplayMarker(messageAndKey, messageAndKey.message.header.messageId)
                )
            } else {
                listOf(recordForLMSentMarker(messageAndKey, messageAndKey.message.header.messageId))
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
        return sessionManager.recordsForSessionEstablished(groups, members, state.session, messageAndKey)
    }

    private fun recordForLMSentMarker(
        message: AuthenticatedMessageAndKey,
        messageId: String
    ): Record<String, AppMessageMarker> {
        val marker = AppMessageMarker(LinkManagerSentMarker(message), clock.instant().toEpochMilli())
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

    private fun recordForLMReplayMarker(
        message: AuthenticatedMessageAndKey,
        messageId: String
    ): Record<String, AppMessageMarker> {
        val marker = AppMessageMarker(LinkManagerReplayMarker(message), clock.instant().toEpochMilli())
        return Record(Schemas.P2P.P2P_OUT_MARKERS, messageId, marker)
    }
}
