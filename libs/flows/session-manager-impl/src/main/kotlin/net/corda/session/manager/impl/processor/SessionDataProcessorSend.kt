package net.corda.session.manager.impl.processor

import net.corda.data.chunking.Chunk
import net.corda.data.flow.event.SessionEvent
import net.corda.data.flow.event.session.SessionData
import net.corda.data.flow.state.session.SessionProcessState
import net.corda.data.flow.state.session.SessionState
import net.corda.data.flow.state.session.SessionStateType
import net.corda.flow.utils.isInitiatedParty
import net.corda.messaging.api.chunking.ChunkSerializerService
import net.corda.session.manager.impl.SessionEventProcessor
import net.corda.session.manager.impl.processor.helper.generateErrorEvent
import net.corda.utilities.debug
import org.slf4j.LoggerFactory
import java.nio.ByteBuffer
import java.time.Instant

/**
 * Process a [SessionData] event to be sent to a counterparty.
 * If the current state is not CONFIRMED or CREATED it indicates a session mismatch bug, return an error message to the counterparty.
 * Set the sequence number of the outbound message and add it to the list of unacked outbound messages to be sent to a counterparty.
 * If the message is too large to send then it will be chunked and sent as separate data messages with the payload set to [Chunk]
 */
@Suppress("LongParameterList")
class SessionDataProcessorSend(
    private val key: Any,
    private val sessionState: SessionState,
    private val sessionEvent: SessionEvent,
    private val instant: Instant,
    private val chunkSerializer: ChunkSerializerService,
    private val sessionData: SessionData
) : SessionEventProcessor {

    companion object {
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    private val isInitiatedParty = isInitiatedParty(sessionEvent)

    override fun execute(): SessionState {
        val sessionId = sessionEvent.sessionId

        return when (val currentStatus = sessionState.status) {
            SessionStateType.ERROR -> {
                val errorMessage = "Tried to send SessionData on key $key for sessionId with status of ${SessionStateType.ERROR}. "
                logger.warn(errorMessage)
                sessionState
            }
            SessionStateType.CREATED, SessionStateType.CONFIRMED  -> {
                if (currentStatus == SessionStateType.CREATED ||
                    (isInitiatedParty && sessionState.sendEventsState.lastProcessedSequenceNum == 0)) {
                    //ensure first message to arrive to counterparty contains session props
                    sessionEvent.contextSessionProperties = sessionState.sessionProperties
                }
                val bytes = (sessionData.payload as ByteBuffer).array()
                val chunks = chunkSerializer.generateChunks(bytes)
                val sendEventsState = sessionState.sendEventsState
                if (chunks.isNotEmpty()) {
                    chunks.forEach {
                        addDataEventToSendEvents(sendEventsState, generateChunkedDataEvent(it))
                    }
                } else {
                    addDataEventToSendEvents(sendEventsState, sessionEvent)
                }
                sessionState
            }
            else -> {
                //If the session is in states CLOSING, or CLOSED then this indicates a session mismatch as no
                // more data messages are expected to be sent. Send an error to the counterparty to inform it of the mismatch.
                val errorMessage = "Tried to send SessionData on key $key for sessionId $sessionId when status was : $currentStatus. " +
                        "SessionState: $sessionState"
                logger.warn(errorMessage)
                sessionState.apply {
                    status = SessionStateType.ERROR
                    sendEventsState.undeliveredMessages =
                        sendEventsState.undeliveredMessages.plus(
                            generateErrorEvent(
                                sessionState,
                                sessionEvent,
                                errorMessage,
                                "SessionData-InvalidStatus",
                                instant
                            )
                        )

                }
            }
        }
    }

    /**
     * Take a [sessionEvent], and add it to the [sendEventsState]. Increment the [sendEventsState] lastProcessedSequenceNum
     */
    private fun addDataEventToSendEvents(sendEventsState: SessionProcessState, sessionEvent: SessionEvent) {
        val nextSeqNum = sendEventsState.lastProcessedSequenceNum + 1
        sessionEvent.sequenceNum = nextSeqNum
        sendEventsState.lastProcessedSequenceNum = nextSeqNum
        logger.debug { "Adding data message with seqNum ${sessionEvent.sequenceNum} to send queue. ${sessionEvent.sessionId}" }
        sendEventsState.undeliveredMessages = sendEventsState.undeliveredMessages.plus(sessionEvent)
    }

    /**
     * Generate a new session data SessionEvent object with the payload set as a chunk
     * @param chunk the chunk to generate a data message for
     * @return SessionData event with chunk payload
     */
    private fun generateChunkedDataEvent(chunk: Chunk) : SessionEvent {
        val copy = SessionEvent.newBuilder(sessionEvent).build()
        (copy.payload as SessionData).payload = chunk
        return copy
    }
}
