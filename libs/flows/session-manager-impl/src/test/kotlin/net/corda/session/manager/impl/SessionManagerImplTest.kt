package net.corda.session.manager.impl

import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory
import net.corda.data.chunking.Chunk
import net.corda.data.crypto.SecureHash
import net.corda.data.flow.event.MessageDirection
import net.corda.data.flow.event.session.SessionData
import net.corda.data.flow.event.session.SessionError
import net.corda.data.flow.state.session.SessionStateType
import net.corda.data.identity.HoldingIdentity
import net.corda.flow.utils.emptyKeyValuePairList
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
import java.nio.ByteBuffer
import java.time.Instant

class SessionManagerImplTest {

    private lateinit var messagingChunkFactory: MessagingChunkFactory
    private lateinit var chunkDeserializerService: ChunkDeserializerService<ByteArray>
    private lateinit var sessionManager: SessionManager
    private val realBytes = ByteArray(500)
    private val realBytesBuffer = ByteBuffer.wrap(realBytes)
    private val sessionTimeout = 30000L
    private val testIdentity = HoldingIdentity()
    private val testConfig = ConfigFactory.empty()
        .withValue(FlowConfig.SESSION_TIMEOUT_WINDOW, ConfigValueFactory.fromAnyRef(sessionTimeout))
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
                buildSessionEvent(MessageDirection.INBOUND, "sessionId", 1, SessionData(), contextSessionProps = emptyKeyValuePairList()),
                buildSessionEvent(MessageDirection.INBOUND, "sessionId", 3, SessionData(), contextSessionProps = emptyKeyValuePairList()),
                buildSessionEvent(MessageDirection.INBOUND, "sessionId", 4, SessionData(), contextSessionProps = emptyKeyValuePairList()),
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
                buildSessionEvent(MessageDirection.INBOUND, "sessionId", 3, SessionData(), contextSessionProps = emptyKeyValuePairList()),
                buildSessionEvent(MessageDirection.INBOUND, "sessionId", 4, SessionData(), contextSessionProps = emptyKeyValuePairList()),
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
                buildSessionEvent(MessageDirection.INBOUND, "sessionId", 1, SessionData(), contextSessionProps = emptyKeyValuePairList()),
                buildSessionEvent(MessageDirection.INBOUND, "sessionId", 3, SessionData(), contextSessionProps = emptyKeyValuePairList()),
                buildSessionEvent(MessageDirection.INBOUND, "sessionId", 4, SessionData(), contextSessionProps = emptyKeyValuePairList()),
            ),
            0,
            listOf()
        )
        val outputState = sessionManager.acknowledgeReceivedEvent(sessionState, 1)
        assertThat(outputState.receivedEventsState.undeliveredMessages.size).isEqualTo(2)
        assertThat(outputState.receivedEventsState.undeliveredMessages.find { it.sequenceNum == 1 }).isNull()
    }

    @Test
    fun `Get messages to send`() {
        val instant = Instant.now()
        val sessionState = buildSessionState(
            SessionStateType.CONFIRMED,
            0,
            listOf(),
            4,
            listOf(
                buildSessionEvent(
                    MessageDirection.OUTBOUND,
                    "sessionId",
                    2,
                    SessionData(),
                    instant.minusMillis(50),
                    contextSessionProps = emptyKeyValuePairList()
                ),
                buildSessionEvent(
                    MessageDirection.OUTBOUND,
                    "sessionId",
                    3,
                    SessionData(),
                    instant,
                    contextSessionProps = emptyKeyValuePairList()
                )
            ),
        )
        //validate only messages with a timestamp in the past are returned.
        val (outputState, messagesToSend) = sessionManager.getMessagesToSend(sessionState, instant, testSmartConfig, testIdentity)
        assertThat(messagesToSend.size).isEqualTo(2)
        //validate all acks removed
        assertThat(outputState.sendEventsState.undeliveredMessages.size).isEqualTo(0)
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
                buildSessionEvent(
                    MessageDirection.OUTBOUND,
                    "sessionId",
                    2,
                    SessionData(chunks[0], null),
                    instant,
                    contextSessionProps = emptyKeyValuePairList()
                ),
                buildSessionEvent(
                    MessageDirection.OUTBOUND,
                    "sessionId",
                    3,
                    SessionData(chunks[1], null),
                    instant,
                    contextSessionProps = emptyKeyValuePairList()
                ),
                buildSessionEvent(
                    MessageDirection.OUTBOUND,
                    "sessionId",
                    4,
                    SessionData(chunks[2], null),
                    instant,
                    contextSessionProps = emptyKeyValuePairList()
                ),
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
                buildSessionEvent(
                    MessageDirection.OUTBOUND,
                    "sessionId",
                    2,
                    SessionData(chunks[0], null),
                    instant,
                    contextSessionProps = emptyKeyValuePairList()
                ),
                buildSessionEvent(
                    MessageDirection.OUTBOUND,
                    "sessionId",
                    3,
                    SessionData(chunks[1], null),
                    instant,
                    contextSessionProps = emptyKeyValuePairList()
                ),
                buildSessionEvent(
                    MessageDirection.OUTBOUND,
                    "sessionId",
                    4,
                    SessionData(chunks[2], null),
                    instant,
                    contextSessionProps = emptyKeyValuePairList()
                ),
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
    fun `next message is a chunk and all chunks are present but deserialization fails, returns null and set session state to error`() {
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
                buildSessionEvent(
                    MessageDirection.OUTBOUND,
                    "sessionId",
                    2,
                    SessionData(chunks[0], null),
                    instant,
                    contextSessionProps = emptyKeyValuePairList()
                ),
                buildSessionEvent(
                    MessageDirection.OUTBOUND,
                    "sessionId",
                    3,
                    SessionData(chunks[1], null),
                    instant,
                    contextSessionProps = emptyKeyValuePairList()
                ),
                buildSessionEvent(
                    MessageDirection.OUTBOUND,
                    "sessionId",
                    4,
                    SessionData(chunks[2], null),
                    instant,
                    contextSessionProps = emptyKeyValuePairList()
                ),
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
            .setChecksum(checksum)
            .setRequestId(id)
            .setPartNumber(partNumber.toInt())
            .setOffset(partNumber)
            .setData(ByteBuffer.wrap(bytes))
            .build()
    }
}
