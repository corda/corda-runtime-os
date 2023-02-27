package net.corda.session.manager.impl

import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory
import java.nio.ByteBuffer
import java.time.Instant
import net.corda.data.chunking.Chunk
import net.corda.data.crypto.SecureHash
import net.corda.data.flow.event.MessageDirection
import net.corda.data.flow.event.session.SessionAck
import net.corda.data.flow.event.session.SessionClose
import net.corda.data.flow.event.session.SessionData
import net.corda.data.flow.event.session.SessionError
import net.corda.data.flow.event.session.SessionInit
import net.corda.data.flow.state.session.SessionStateType
import net.corda.data.identity.HoldingIdentity
import net.corda.libs.configuration.SmartConfigFactory
import net.corda.messaging.api.chunking.ChunkDeserializerService
import net.corda.messaging.api.chunking.MessagingChunkFactory
import net.corda.schema.configuration.FlowConfig
import net.corda.session.manager.SessionManager
import net.corda.session.manager.impl.factory.SessionEventProcessorFactory
import net.corda.test.flow.util.buildSessionEvent
import net.corda.test.flow.util.buildSessionState
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class SessionManagerImplTest {

    private lateinit var messagingChunkFactory: MessagingChunkFactory
    private lateinit var chunkDeserializerService: ChunkDeserializerService<ByteArray>
    private lateinit var sessionManager: SessionManager
    private val realBytes =ByteArray(500)
    private val realBytesBuffer = ByteBuffer.wrap(realBytes)
    private val testResendWindow = 5000L
    private val testHeartbeatTimeout = 30000L
    private val testIdentity = HoldingIdentity()
    private val testConfig = ConfigFactory.empty()
        .withValue(FlowConfig.SESSION_MESSAGE_RESEND_WINDOW, ConfigValueFactory.fromAnyRef(testResendWindow))
        .withValue(FlowConfig.SESSION_HEARTBEAT_TIMEOUT_WINDOW, ConfigValueFactory.fromAnyRef(testHeartbeatTimeout))
    private val configFactory = SmartConfigFactory.createWithoutSecurityServices()
    private val testSmartConfig = configFactory.create(testConfig)

    @BeforeEach
    fun setup() {
        chunkDeserializerService = mock()
        messagingChunkFactory = mock()

        whenever(chunkDeserializerService.assembleChunks(any())).thenReturn(realBytes)
        whenever(messagingChunkFactory.createChunkDeserializerService(any<Class<ByteArray>>(), any())).thenReturn(chunkDeserializerService)

        sessionManager = SessionManagerImpl(SessionEventProcessorFactory(mock()), messagingChunkFactory)
    }

    @Test
    fun testGetNextReceivedEvent() {
        val sessionState = buildSessionState(
            SessionStateType.CONFIRMED,
            1,
            listOf(
                buildSessionEvent(MessageDirection.INBOUND, "sessionId", 1, SessionData()),
                buildSessionEvent(MessageDirection.INBOUND, "sessionId", 3, SessionData()),
                buildSessionEvent(MessageDirection.INBOUND, "sessionId", 4, SessionData()),
            ),
            0,
            listOf()
        )
        val outputEvent = sessionManager.getNextReceivedEvent(sessionState)
        assertThat(outputEvent).isNotNull
        assertThat(outputEvent!!.sequenceNum).isEqualTo(1)
    }

    @Test
    fun testGetNextReceivedEventOutOfOrder() {
        val sessionState = buildSessionState(
            SessionStateType.CONFIRMED,
            1,
            listOf(
                buildSessionEvent(MessageDirection.INBOUND, "sessionId", 3, SessionData()),
                buildSessionEvent(MessageDirection.INBOUND, "sessionId", 4, SessionData()),
            ),
            0,
            listOf()
        )
        val outputEvent = sessionManager.getNextReceivedEvent(sessionState)
        assertThat(outputEvent).isNull()
    }

    @Test
    fun testAcknowledgeReceivedEvent() {
        val sessionState = buildSessionState(
            SessionStateType.CONFIRMED,
            1,
            listOf(
                buildSessionEvent(MessageDirection.INBOUND, "sessionId", 1, SessionData()),
                buildSessionEvent(MessageDirection.INBOUND, "sessionId", 3, SessionData()),
                buildSessionEvent(MessageDirection.INBOUND, "sessionId", 4, SessionData()),
            ),
            0,
            listOf()
        )
        val outputState = sessionManager.acknowledgeReceivedEvent(sessionState, 1)
        assertThat(outputState.receivedEventsState.undeliveredMessages.size).isEqualTo(2)
        assertThat(outputState.receivedEventsState.undeliveredMessages.find { it.sequenceNum == 1 }).isNull()
    }

    @Test
    fun `Get messages with datas, error and acks with timestamps in the future and past`() {
        val instant = Instant.now()
        val sessionState = buildSessionState(
            SessionStateType.CONFIRMED,
            0,
            listOf(),
            4,
            listOf(
                buildSessionEvent(MessageDirection.OUTBOUND, "sessionId", 2, SessionData(), 0, emptyList(), instant.minusMillis(50)),
                buildSessionEvent(MessageDirection.OUTBOUND, "sessionId", 3, SessionData(), 0, emptyList(), instant),
                buildSessionEvent(MessageDirection.OUTBOUND, "sessionId", 4, SessionData(), 0, emptyList(), instant.plusMillis(100)),
            ),
        )
        //validate only messages with a timestamp in the past are returned.
        val (outputState, messagesToSend) = sessionManager.getMessagesToSend(sessionState, instant, testSmartConfig, testIdentity)
        assertThat(messagesToSend.size).isEqualTo(2)
        //validate all acks removed
        assertThat(outputState.sendEventsState.undeliveredMessages.size).isEqualTo(3)
        assertThat(outputState.sendEventsState.undeliveredMessages.filter { it.payload::class.java == SessionAck::class.java }).isEmpty()

        //Validate all acks removed and normal session events are resent
        val (secondOutputState, secondMessagesToSend) = sessionManager.getMessagesToSend(
            sessionState, instant.plusMillis(testResendWindow + 100),
            testSmartConfig,
            testIdentity
        )
        assertThat(secondMessagesToSend.size).isEqualTo(3)
        assertThat(secondOutputState.sendEventsState.undeliveredMessages.size).isEqualTo(3)
        assertThat(secondOutputState.sendEventsState.undeliveredMessages.filter {
            it.payload::class.java == SessionAck::class.java }
        ).isEmpty()
    }

    @Test
    fun `Send heartbeat`() {
        val instant = Instant.now()
        val sessionState = buildSessionState(
            SessionStateType.CONFIRMED,
            0,
            listOf(),
            4,
            listOf(),
            instant
        )

        //validate no heartbeat
        val (_, messagesToSend) = sessionManager.getMessagesToSend(sessionState, instant, testSmartConfig, testIdentity)
        assertThat(messagesToSend.size).isEqualTo(0)

        //Validate heartbeat
        val (_, secondMessagesToSend) = sessionManager.getMessagesToSend(
            sessionState, instant.plusMillis(testResendWindow  + 1),
            testSmartConfig,
            testIdentity
        )

        assertThat(secondMessagesToSend.size).isEqualTo(1)
        val messageToSend = secondMessagesToSend.first()
        assertThat(messageToSend.payload::class.java).isEqualTo(SessionAck::class.java)
        assertThat(messageToSend.receivedSequenceNum).isEqualTo(0)
    }

    @Test
    fun `Send Ack when flag is set`() {
        val instant = Instant.now()
        val sessionState = buildSessionState(
            SessionStateType.CONFIRMED,
            0,
            listOf(),
            4,
            listOf(),
            instant,
            true
        )

        //validate no heartbeat
        val (_, messagesToSend) = sessionManager.getMessagesToSend(sessionState, instant, testSmartConfig, testIdentity)
        assertThat(messagesToSend.size).isEqualTo(1)
        val messageToSend = messagesToSend.first()
        assertThat(messageToSend.payload::class.java).isEqualTo(SessionAck::class.java)
        assertThat(messageToSend.receivedSequenceNum).isEqualTo(0)
    }

    @Test
    fun `Dont send Ack when flag is not set`() {
        val instant = Instant.now()
        val sessionState = buildSessionState(
            SessionStateType.CONFIRMED,
            0,
            listOf(),
            4,
            listOf(),
            instant,
            false
        )

        //validate no heartbeat
        val (_, messagesToSend) = sessionManager.getMessagesToSend(sessionState, instant, testSmartConfig, testIdentity)
        assertThat(messagesToSend).isEmpty()
    }

    @Test
    fun `Send error for session timed out`() {
        val instant = Instant.now()
        val sessionState = buildSessionState(
            SessionStateType.CONFIRMED,
            0,
            listOf(),
            4,
            listOf(),
            instant
        )

        //validate no heartbeat
        val (firstUpdatedState, messagesToSend) = sessionManager.getMessagesToSend(sessionState, instant, testSmartConfig, testIdentity)
        assertThat(messagesToSend.size).isEqualTo(0)
        assertThat(firstUpdatedState.status).isEqualTo(SessionStateType.CONFIRMED)

        //Validate heartbeat
        val (secondUpdatedState, secondMessagesToSend) = sessionManager.getMessagesToSend(
            sessionState, instant.plusMillis(testHeartbeatTimeout  + 1),
            testSmartConfig,
            testIdentity
        )

        assertThat(secondMessagesToSend.size).isEqualTo(1)
        assertThat(secondUpdatedState.status).isEqualTo(SessionStateType.ERROR)
        val messageToSend = secondMessagesToSend.first()
        assertThat(messageToSend.payload::class.java).isEqualTo(SessionError::class.java)
    }

    @Test
    fun `CREATED state, ack received for init message sent, state moves to CONFIRMED`() {
        val init = buildSessionEvent(MessageDirection.OUTBOUND, "sessionId", 1, SessionInit())
        val sessionState = buildSessionState(
            SessionStateType.CREATED, 0, emptyList(), 1,
            mutableListOf(init)
        )

        val sessionEvent = buildSessionEvent(MessageDirection.INBOUND, "sessionId", null, SessionAck(), 1)
        val updatedState = sessionManager.processMessageReceived("key", sessionState, sessionEvent, Instant.now())

        assertThat(updatedState.status).isEqualTo(SessionStateType.CONFIRMED)
        assertThat(updatedState.sendEventsState?.undeliveredMessages).isEmpty()
    }

    @Test
    fun `CONFIRMED state, ack received for 1 or 2 data events, 1 data is left`() {
        val sessionState = buildSessionState(
            SessionStateType.CONFIRMED, 0, emptyList(), 3, mutableListOf(
                buildSessionEvent(MessageDirection.OUTBOUND, "sessionId", 2, SessionData()),
                buildSessionEvent(MessageDirection.OUTBOUND, "sessionId", 3, SessionData()),
            )
        )

        val sessionEvent = buildSessionEvent(MessageDirection.INBOUND, "sessionId", null, SessionAck(), 2)

        val updatedState = sessionManager.processMessageReceived("key", sessionState, sessionEvent, Instant.now())

        assertThat(updatedState.status).isEqualTo(SessionStateType.CONFIRMED)
        assertThat(updatedState.sendEventsState?.undeliveredMessages?.size).isEqualTo(1)
    }

    @Test
    fun `WAIT_FOR_FINAL_ACK state, final ack is received via out of order acks, state moves to CLOSED`() {
        val close = buildSessionEvent(MessageDirection.OUTBOUND, "sessionId", 3, SessionClose(), 2)
        val sessionState = buildSessionState(
            SessionStateType.WAIT_FOR_FINAL_ACK, 0, emptyList(), 3,
            mutableListOf(close)
        )

        val sessionEvent = buildSessionEvent(MessageDirection.INBOUND, "sessionId", null, SessionAck(), 0, listOf(3))
        val updatedState = sessionManager.processMessageReceived("key", sessionState, sessionEvent, Instant.now())

        assertThat(updatedState.status).isEqualTo(SessionStateType.CLOSED)
        assertThat(updatedState.sendEventsState?.undeliveredMessages).isEmpty()
    }

    @Test
    fun `next message is a chunk but all chunks are not present, returns null`() {
        val instant = Instant.now()
        val requestId = "chunkId"
        val chunks = listOf(
            buildChunk(requestId, ByteArray(20), 1),
            buildChunk(requestId, ByteArray(20), 2),
            buildChunk(requestId, ByteArray(20), 3),
        )
        val sessionState = buildSessionState(
            SessionStateType.CONFIRMED,
            4,
            listOf(
                buildSessionEvent(MessageDirection.OUTBOUND, "sessionId", 2, SessionData(chunks[0]), 0, emptyList(), instant),
                buildSessionEvent(MessageDirection.OUTBOUND, "sessionId", 3, SessionData(chunks[1]), 0, emptyList(), instant),
                buildSessionEvent(MessageDirection.OUTBOUND, "sessionId", 4, SessionData(chunks[2]), 0, emptyList(), instant),
            ),
            4,
            listOf(),
        )
        val nextMessage = sessionManager.getNextReceivedEvent(sessionState)
        assertThat(nextMessage).isNull()
        assertThat(sessionState.receivedEventsState.undeliveredMessages.size).isEqualTo(3)
    }

    @Test
    fun `next message is a chunk and all chunks are present, assembled record, returns the record and clears out chunks from state`() {
        val instant = Instant.now()
        val requestId = "chunkId"
        val chunks = listOf(
            buildChunk(requestId, ByteArray(20), 1),
            buildChunk(requestId, ByteArray(20), 2),
            buildChunk(requestId, ByteArray(20), 3, SecureHash("", ByteBuffer.wrap(ByteArray(1)))),
        )
        val sessionState = buildSessionState(
            SessionStateType.CONFIRMED,
            4,
            listOf(
                buildSessionEvent(MessageDirection.OUTBOUND, "sessionId", 2, SessionData(chunks[0]), 0, emptyList(), instant),
                buildSessionEvent(MessageDirection.OUTBOUND, "sessionId", 3, SessionData(chunks[1]), 0, emptyList(), instant),
                buildSessionEvent(MessageDirection.OUTBOUND, "sessionId", 4, SessionData(chunks[2]), 0, emptyList(), instant),
            ),
            4,
            listOf(),
        )

        val nextMessage = sessionManager.getNextReceivedEvent(sessionState)
        assertThat(nextMessage).isNotNull
        assertThat((nextMessage?.payload as SessionData).payload).isEqualTo(realBytesBuffer)
        assertThat(sessionState.receivedEventsState.undeliveredMessages.size).isEqualTo(1)
    }

    @Test
    fun `next message is a chunk and all chunks are present but deserialization fails, returns null and updates the session state to error`
                () {
        whenever(chunkDeserializerService.assembleChunks(any())).thenReturn(null)
        val instant = Instant.now()
        val requestId = "chunkId"
        val chunks = listOf(
            buildChunk(requestId, ByteArray(20), 1),
            buildChunk(requestId, ByteArray(20), 2),
            buildChunk(requestId, ByteArray(20), 3, SecureHash("", ByteBuffer.wrap(ByteArray(1)))),
        )
        val sessionState = buildSessionState(
            SessionStateType.CONFIRMED,
            4,
            listOf(
                buildSessionEvent(MessageDirection.OUTBOUND, "sessionId", 2, SessionData(chunks[0]), 0, emptyList(), instant),
                buildSessionEvent(MessageDirection.OUTBOUND, "sessionId", 3, SessionData(chunks[1]), 0, emptyList(), instant),
                buildSessionEvent(MessageDirection.OUTBOUND, "sessionId", 4, SessionData(chunks[2]), 0, emptyList(), instant),
            ),
            4,
            listOf(),
        )
        val nextMessage = sessionManager.getNextReceivedEvent(sessionState)
        assertThat(nextMessage).isNull()
        assertThat(sessionState.receivedEventsState.undeliveredMessages.size).isEqualTo(3)
        assertThat(sessionState.sendEventsState.undeliveredMessages.size).isEqualTo(1)
        assertThat(sessionState.status).isEqualTo(SessionStateType.ERROR)
        val messageToSend = sessionState.sendEventsState.undeliveredMessages.first()
        assertThat(messageToSend.payload::class.java).isEqualTo(SessionError::class.java)
    }

    private fun buildChunk(id: String, bytes: ByteArray, partNumber: Long, checksum: SecureHash? = null): Chunk {
        return Chunk.newBuilder()
            .setProperties(null)
            .setFileName(null)
            .setChecksum(checksum)
            .setRequestId(id)
            .setPartNumber(partNumber.toInt())
            .setOffset(partNumber)
            .setData(ByteBuffer.wrap(bytes))
            .build()
    }
}
