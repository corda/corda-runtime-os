package net.corda.session.manager.impl

import net.corda.data.KeyValuePairList
import net.corda.data.chunking.Chunk
import net.corda.data.flow.event.SessionEvent
import net.corda.data.flow.event.session.SessionClose
import net.corda.data.flow.event.session.SessionData
import net.corda.data.flow.state.session.SessionProcessState
import net.corda.data.flow.state.session.SessionState
import net.corda.data.flow.state.session.SessionStateType
import net.corda.data.identity.HoldingIdentity
import net.corda.libs.configuration.SmartConfig
import net.corda.messaging.api.chunking.MessagingChunkFactory
import net.corda.session.manager.Constants
import net.corda.session.manager.SessionManager
import net.corda.session.manager.impl.factory.SessionEventProcessorFactory
import net.corda.session.manager.impl.processor.helper.setErrorState
import net.corda.utilities.debug
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.LoggerFactory
import java.nio.ByteBuffer
import java.time.Instant

@Suppress("TooManyFunctions")
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
        val updatedSessionState = sessionState?.apply {
            lastReceivedMessageTime = instant
        }

        return sessionEventProcessorFactory.createEventReceivedProcessor(key, event, updatedSessionState, instant).execute()
    }

    override fun processMessageToSend(
        key: Any,
        sessionState: SessionState,
        event: SessionEvent,
        instant: Instant,
        maxMsgSize: Long,
    ): SessionState {
        return sessionEventProcessorFactory.createEventToSendProcessor(key, event, sessionState, instant, maxMsgSize).execute()
    }

    override fun generateSessionState(
        sessionId: String,
        contextSessionProperties: KeyValuePairList,
        counterparty: HoldingIdentity,
        instant: Instant,
        initialStatus: SessionStateType,
    ): SessionState = SessionState.newBuilder()
            .setSessionId(sessionId)
            .setSessionStartTime(instant)
            .setLastReceivedMessageTime(instant)
            .setCounterpartyIdentity(counterparty)
            .setReceivedEventsState(SessionProcessState(0, mutableListOf()))
            .setSendEventsState(SessionProcessState(0, mutableListOf()))
            .setSessionProperties(contextSessionProperties)
            .setStatus(initialStatus)
            .setHasScheduledCleanup(false)
            .build()

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
            //only allow client to see a close message after the session is closed
            status != SessionStateType.CLOSED && undeliveredMessages.first().payload is SessionClose -> null
            //return the next valid message
            undeliveredMessages.first().sequenceNum <= receivedEvents.lastProcessedSequenceNum -> {
                val nextMessage = undeliveredMessages.first()
                val nextMessagePayload = nextMessage.payload
                if (nextMessagePayload is SessionData && nextMessagePayload.payload is Chunk) {
                    assembleAndReturnChunkIfPossible(sessionState, nextMessagePayload.payload as Chunk)
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
    ): Pair<SessionState, List<SessionEvent>> {
        val sessionEvents = getMessagesToSend(sessionState)
        sessionState.sendEventsState.undeliveredMessages = emptyList()
        return Pair(sessionState, sessionEvents)
    }

    override fun errorSession(sessionState: SessionState): SessionState {
        sessionState.status = SessionStateType.ERROR
        return sessionState
    }

    /**
     * Get any new messages to send from the sendEvents state within [sessionState]
     * and log some debug information.
     * @param sessionState to examine sendEventsState.undeliveredMessages
     * @return Messages to send
     */
    private fun getMessagesToSend(
        sessionState: SessionState,
    ): List<SessionEvent> {
        val sessionEvents = sessionState.sendEventsState.undeliveredMessages
        if (sessionEvents.isNotEmpty()) {
            logger.debug {
                "Dispatching events for session [${sessionState.sessionId}]: " +
                        sessionEvents.joinToString {
                            "[Sequence: ${it.sequenceNum}, Class: ${it.payload::class.java.simpleName}, Resend timestamp: ${it.timestamp}]"
                        }
            }
        } else {
            logger.debug {
                "No SessionEvents to dispatch for [${sessionState.sessionId}]"
            }
        }

        return sessionEvents
    }

    /**
     * Get the initiating and initiated identities.
     * @param sessionState Session state
     * @param identity The identity of the party to send messages from
     * @return Pair with initiating party as the first identity, initiated party as the second identity
     */
    private fun getInitiatingAndInitiatedParties(sessionState: SessionState, identity: HoldingIdentity):
            Pair<HoldingIdentity, HoldingIdentity> {
        return if (sessionState.sessionId.contains(Constants.INITIATED_SESSION_ID_SUFFIX)) {
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
                setErrorState(
                    sessionState,
                    receivedEventState.undeliveredMessages.first(),
                    Instant.now(),
                    errorMessage,
                    "SessionData-ChunkError"
                )
                null
            } else {
                val sessionEvent = chunkSessionEvents.last()
                (sessionEvent.payload as SessionData).payload = ByteBuffer.wrap(dataPayload)
                val undeliveredMessages =
                    receivedEventState.undeliveredMessages.filterNot { chunkSessionEvents.contains(it) }.toMutableList()
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
