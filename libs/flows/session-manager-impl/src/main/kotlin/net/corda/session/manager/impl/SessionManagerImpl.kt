package net.corda.session.manager.impl

import java.nio.ByteBuffer
import java.time.Instant
import net.corda.data.chunking.Chunk
import net.corda.data.flow.event.MessageDirection
import net.corda.data.flow.event.SessionEvent
import net.corda.data.flow.event.session.SessionAck
import net.corda.data.flow.event.session.SessionClose
import net.corda.data.flow.event.session.SessionData
import net.corda.data.flow.event.session.SessionError
import net.corda.data.flow.state.session.SessionState
import net.corda.data.flow.state.session.SessionStateType
import net.corda.data.identity.HoldingIdentity
import net.corda.libs.configuration.SmartConfig
import net.corda.messaging.api.chunking.MessagingChunkFactory
import net.corda.schema.configuration.FlowConfig.SESSION_HEARTBEAT_TIMEOUT_WINDOW
import net.corda.schema.configuration.FlowConfig.SESSION_MESSAGE_RESEND_WINDOW
import net.corda.session.manager.Constants.Companion.INITIATED_SESSION_ID_SUFFIX
import net.corda.session.manager.SessionManager
import net.corda.session.manager.impl.factory.SessionEventProcessorFactory
import net.corda.session.manager.impl.processor.helper.generateErrorEvent
import net.corda.session.manager.impl.processor.helper.setErrorState
import net.corda.v5.base.util.uncheckedCast
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.LoggerFactory

@Component
class SessionManagerImpl @Activate constructor(
    @Reference(service = SessionEventProcessorFactory::class)
    private val sessionEventProcessorFactory: SessionEventProcessorFactory,
    @Reference(service = MessagingChunkFactory::class)
    private val messagingChunkFactory: MessagingChunkFactory,
) : SessionManager {

    private companion object {
        const val INITIAL_PART_NUMBER = 1
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    private val chunkDeserializerService = messagingChunkFactory.createChunkDeserializerService(ByteArray::class.java)

    override fun processMessageReceived(key: Any, sessionState: SessionState?, event: SessionEvent, instant: Instant):
            SessionState {
        val updatedSessionState = sessionState?.let {
            it.lastReceivedMessageTime = instant
            processAcks(event, it)
        }

        return sessionEventProcessorFactory.createEventReceivedProcessor(key, event, updatedSessionState, instant).execute()
    }

    override fun processMessageToSend(
        key: Any,
        sessionState: SessionState?,
        event: SessionEvent,
        instant: Instant,
        maxMsgSize: Long,
    ): SessionState {
        return sessionEventProcessorFactory.createEventToSendProcessor(key, event, sessionState, instant, maxMsgSize).execute()
    }

    override fun getNextReceivedEvent(sessionState: SessionState): SessionEvent? {
        val receivedEvents = sessionState.receivedEventsState ?: return null
        val undeliveredMessages = receivedEvents.undeliveredMessages
        val status = sessionState.status
        val incorrectSessionState = status == SessionStateType.CREATED || status == SessionStateType.ERROR
        return when {
            //must be an active session
            undeliveredMessages.isEmpty() -> null
            //don't allow data messages to be consumed when session is not fully established or if there is an error
            incorrectSessionState -> null
            //only allow client to see a close message after the session is closed on both sides
            status != SessionStateType.CLOSED && undeliveredMessages.first().payload is SessionClose -> null
            //return the next valid message
            undeliveredMessages.first().sequenceNum <= receivedEvents.lastProcessedSequenceNum -> {
                val nextMessage = undeliveredMessages.first()
                val nextMessagePayload = nextMessage.payload
                if (nextMessagePayload is SessionData && nextMessagePayload.payload is Chunk) {
                    assembleAndReturnChunkIfPossible(sessionState, uncheckedCast(nextMessagePayload.payload))
                } else {
                    nextMessage
                }
            }
            else -> null
        }
    }

    override fun acknowledgeReceivedEvent(sessionState: SessionState, seqNum: Int): SessionState {
        return sessionState.apply {
            receivedEventsState.undeliveredMessages =
                receivedEventsState.undeliveredMessages.filter { it.sequenceNum > seqNum }
        }
    }

    override fun getMessagesToSend(
        sessionState: SessionState,
        instant: Instant,
        config: SmartConfig,
        identity: HoldingIdentity,
    ): Pair<SessionState,
            List<SessionEvent>> {
        var messagesToReturn = getMessagesToSendAndUpdateSendState(sessionState, instant, config)

        //add heartbeat if no messages sent recently, add ack if needs to be sent, error session if no heartbeat received within timeout
        messagesToReturn = handleHeartbeatAndAcknowledgements(sessionState, config, instant, messagesToReturn, identity)

        if (messagesToReturn.isNotEmpty()) {
            sessionState.sendAck = false
            sessionState.lastSentMessageTime = instant
        }

        return Pair(sessionState, messagesToReturn)
    }

    /**
     * If no heartbeat received from counterparty after session timeout has been reached, error the session
     * If no messages to send, and current time has surpassed the heartbeat window (message resend window), send a new ack.
     * If no messages to send, and there are messages received to be acked, send an ack.
     * else return back [messagesToReturn] unedited
     * @param sessionState to update
     * @param config contains message resend window and heartbeat timeout values
     * @param instant for timestamps of new messages
     * @param messagesToReturn add any new messages to send to this list
     * @param identity identity of the party sending messages
     * @return Messages to send to the counterparty
     */
    private fun handleHeartbeatAndAcknowledgements(
        sessionState: SessionState,
        config: SmartConfig,
        instant: Instant,
        messagesToReturn: List<SessionEvent>,
        identity: HoldingIdentity,
    ): List<SessionEvent> {
        val messageResendWindow = config.getLong(SESSION_MESSAGE_RESEND_WINDOW)
        val lastReceivedMessageTime = sessionState.lastReceivedMessageTime

        val sessionTimeoutTimestamp = lastReceivedMessageTime.plusMillis(config.getLong(SESSION_HEARTBEAT_TIMEOUT_WINDOW))
        val scheduledHeartbeatTimestamp = sessionState.lastSentMessageTime.plusMillis(messageResendWindow)

        return if (instant > sessionTimeoutTimestamp) {
            //send an error if the session has timed out
            sessionState.status = SessionStateType.ERROR
            val (initiatingIdentity, initiatedIdentity) = getInitiatingAndInitiatedParties(sessionState, identity)
            listOf(
                generateErrorEvent(
                    sessionState,
                    initiatingIdentity,
                    initiatedIdentity,
                    "Session has timed out. No messages received since $lastReceivedMessageTime",
                    "SessionTimeout-Heartbeat",
                    instant
                ).apply {
                    this.initiatedIdentity = initiatedIdentity
                    this.initiatingIdentity = initiatingIdentity
                }
            )

        } else if (messagesToReturn.isEmpty() && (instant > scheduledHeartbeatTimestamp || sessionState.sendAck)) {
            listOf(generateAck(sessionState, instant, identity))
        } else {
            messagesToReturn
        }
    }

    /**
     * Get any new messages to send from the sendEvents state within [sessionState].
     * Send any Acks or Errors regardless of timestamps.
     * Send any other messages with a timestamp less than that of [instantInMillis].
     * Don't send a SessionAck if other events present in the send list as ack info is present at SessionEvent level on all events.
     * Update sendEvents state to remove acks/errors and increase timestamps of messages to send.
     * @param sessionState to examine sendEventsState.undeliveredMessages
     * @param instant to compare against messages to avoid resending messages in quick succession
     * @param config config to get resend values from
     * @return Messages to send
     */
    private fun getMessagesToSendAndUpdateSendState(
        sessionState: SessionState,
        instant: Instant,
        config: SmartConfig,
    ): List<SessionEvent> {
        //get all events with a timestamp in the past, as well as any acks or errors
        val sessionEvents = sessionState.sendEventsState.undeliveredMessages.filter {
            it.timestamp <= instant || it.payload is SessionError
        }

        //update events with the latest ack info from the current state
        sessionEvents.forEach { eventToSend ->
            eventToSend.receivedSequenceNum = sessionState.receivedEventsState.lastProcessedSequenceNum
            eventToSend.outOfOrderSequenceNums = sessionState.receivedEventsState.undeliveredMessages.map { it.sequenceNum }
        }

        //remove SessionAcks/SessionErrors and increase timestamp of messages to be sent that are awaiting acknowledgement
        val messageResendWindow = config.getLong(SESSION_MESSAGE_RESEND_WINDOW)
        updateSessionStateSendEvents(sessionState, instant, messageResendWindow)

        return sessionEvents
    }

    /**
     * Remove Acks and Errors from the session state sendEvents.undeliveredMessages as these cannot be acked by a counterparty.
     * Increase the timestamps of messages with a timestamp less than [instantInMillis]
     * by the configurable value of the [messageResendWindow]. This will avoid message resends in quick succession.
     * @param sessionState to update the sendEventsState.undeliveredMessages
     * @param instant to update the sendEventsState.undeliveredMessages
     * @param messageResendWindow time to wait between resending messages
     */
    private fun updateSessionStateSendEvents(
        sessionState: SessionState,
        instant: Instant,
        messageResendWindow: Long,
    ) {
        sessionState.sendEventsState.undeliveredMessages = sessionState.sendEventsState.undeliveredMessages.filter {
            it.payload !is SessionError
        }.map {
            if (it.timestamp <= instant) {
                it.timestamp = instant.plusMillis(messageResendWindow)
            }
            it
        }
    }

    /**
     * Remove any messages from the send events state that have been acknowledged by the counterparty.
     * Examine the [sessionEvent] to get the highest contiguous sequence number received by the other side
     * as well as any out of order messages
     * they have also received. Remove these events if present from the sendEvents undelivered messages.
     * If the current session state has a status of WAIT_FOR_FINAL_ACK and the ack info contains the sequence number of the session close
     * message then the session can be set to CLOSED.
     * If the current session state has a status of CREATED and the SessionInit has been acked then the session can be set to CONFIRMED
     *
     * @param sessionEvent to get ack info from
     * @param sessionState to get the sent events
     * @return Session state updated with any messages that were delivered to the counterparty removed from [sessionState].sendEventsState
     */
    private fun processAcks(sessionEvent: SessionEvent, sessionState: SessionState): SessionState {
        val highestContiguousSeqNum = sessionEvent.receivedSequenceNum
        val outOfOrderSeqNums = sessionEvent.outOfOrderSequenceNums

        val undeliveredMessages = sessionState.sendEventsState.undeliveredMessages.filter {
            it.sequenceNum == null ||
                    (it.sequenceNum > highestContiguousSeqNum &&
                            (outOfOrderSeqNums.isNullOrEmpty() || !outOfOrderSeqNums.contains(it.sequenceNum)))
        }

        return sessionState.apply {
            sendEventsState.undeliveredMessages = undeliveredMessages
            val nonAckUndeliveredMessages = undeliveredMessages.filter { it.payload !is SessionAck }
            if (status == SessionStateType.WAIT_FOR_FINAL_ACK && nonAckUndeliveredMessages.isEmpty()) {
                status = SessionStateType.CLOSED
            } else if (status == SessionStateType.CREATED && !nonAckUndeliveredMessages.any { it.sequenceNum == 1 }) {
                status = SessionStateType.CONFIRMED
            }
        }
    }

    /**
     * Generate an SessionAck containing the latest info regarding messages received.
     * @param sessionState to examine which messages have been received
     * @param instant to set timestamp on SessionAck
     * @param identity Identity of the party calling this method
     * @return A SessionAck SessionEvent with ack fields set on the SessionEvent based on messages received from a counterparty
     */
    private fun generateAck(sessionState: SessionState, instant: Instant, identity: HoldingIdentity): SessionEvent {
        val receivedEventsState = sessionState.receivedEventsState
        val outOfOrderSeqNums = receivedEventsState.undeliveredMessages
            .filter { it.sequenceNum > receivedEventsState.lastProcessedSequenceNum }
            .map { it.sequenceNum }
        val (initiatingIdentity, initiatedIdentity) = getInitiatingAndInitiatedParties(sessionState, identity)
        return SessionEvent.newBuilder()
            .setMessageDirection(MessageDirection.OUTBOUND)
            .setTimestamp(instant)
            .setSequenceNum(null)
            .setInitiatingIdentity(initiatingIdentity)
            .setInitiatedIdentity(initiatedIdentity)
            .setSessionId(sessionState.sessionId)
            .setReceivedSequenceNum(receivedEventsState.lastProcessedSequenceNum)
            .setOutOfOrderSequenceNums(outOfOrderSeqNums)
            .setPayload(SessionAck())
            .build()
    }

    /**
     * Get the initiating and initiated identities.
     * @param sessionState Session state
     * @param identity The identity of the party to send messages from
     * @return Pair with initiating party as the first identity, initiated party as the second identity
     */
    private fun getInitiatingAndInitiatedParties(sessionState: SessionState, identity: HoldingIdentity):
            Pair<HoldingIdentity, HoldingIdentity> {
        return if (sessionState.sessionId.contains(INITIATED_SESSION_ID_SUFFIX)) {
            Pair(sessionState.counterpartyIdentity, identity)
        } else {
            Pair(identity, sessionState.counterpartyIdentity)
        }
    }

    /**
     * Take a chunk received. Get the chunks received for this chunk requestId
     * If there are missing chunks return null
     * Otherwise try to assemble the chunks into a complete data event.
     * Remove chunked records from the receivedEventState and add the complete record.
     * Use the sequenceNumber of the last chunked data message for the completed record.
     * If chunks fail to reassemble set the session as errored.
     * @param sessionState current sessions state
     * @param chunk the chunk for the next message available in the received events state
     * @return A data session event reassembled from chunks. null if chunks missing or an error occurred on deserialization
     */
    private fun assembleAndReturnChunkIfPossible(sessionState: SessionState, chunk: Chunk): SessionEvent? {
        val requestId = chunk.requestId
        val receivedEventState = sessionState.receivedEventsState
        val chunkSessionEvents = receivedEventState.undeliveredMessages.filter {
            val eventPayload = it.payload
            if (eventPayload is SessionData) {
                val dataPayload = eventPayload.payload
                dataPayload is Chunk && dataPayload.requestId == requestId
            } else {
                false
            }
        }.sortedBy { it.sequenceNum }

        val chunks = chunkSessionEvents.map {
            (it.payload as SessionData).payload as Chunk
        }

        return if (!chunkMissing(chunks)) {
            val dataPayload = chunkDeserializerService.assembleChunks(chunks)
            if (dataPayload == null) {
                val errorMessage = "Failed to deserialize chunks into a complete data message. First chunk seqNum is ${
                    chunkSessionEvents
                        .first().sequenceNum
                }"
                logger.warn(errorMessage)
                setErrorState(sessionState, receivedEventState.undeliveredMessages.first(), Instant.now(), errorMessage, "SessionData-ChunkError")
                null
            } else {
                val sessionEvent = chunkSessionEvents.last()
                (sessionEvent.payload as SessionData).payload = ByteBuffer.wrap(dataPayload)
                val undeliveredMessages = receivedEventState.undeliveredMessages.filterNot { chunkSessionEvents.contains(it) }.toMutableList()
                receivedEventState.undeliveredMessages = undeliveredMessages.apply {
                    add(sessionEvent)
                }
                sessionEvent
            }
        } else null
    }

    /**
     * Check to see if any chunks are missing.
     * Checks to see if the checksum is present or if the partNumbers are not contiguous starting at 1
     * @param chunkList chunks received so far
     * @return true if there is a chunk missing, false otherwise
     */
    private fun chunkMissing(chunkList: List<Chunk>): Boolean {
        val sortedChunks = chunkList.sortedBy { it.partNumber }
        return sortedChunks.last().checksum == null || missingPartNumber(sortedChunks)
    }

    /**
     * Check to see if there are any missing parts in the chunks sorted already by partNumber
     * @param sortedChunks sorted chunks by part number
     * @return true if there is a chunk missing, false otherwise
     */
    private fun missingPartNumber(sortedChunks: List<Chunk>): Boolean {
        var expected = INITIAL_PART_NUMBER
        for (chunk in sortedChunks) {
            if (chunk.partNumber != expected) {
                return true
            }
            expected++
        }
        return false
    }
}
