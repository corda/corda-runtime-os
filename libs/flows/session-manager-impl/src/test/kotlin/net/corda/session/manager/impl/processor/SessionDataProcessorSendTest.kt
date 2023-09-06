package net.corda.session.manager.impl.processor

import net.corda.avro.serialization.CordaAvroSerializer
import net.corda.data.chunking.Chunk
import net.corda.data.flow.event.MessageDirection
import net.corda.data.flow.event.session.SessionData
import net.corda.data.flow.event.session.SessionError
import net.corda.data.flow.state.session.SessionStateType
import net.corda.flow.utils.emptyKeyValuePairList
import net.corda.messaging.api.chunking.ChunkSerializerService
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
class SessionDataProcessorSendTest {

    private lateinit var chunkSerializerService: ChunkSerializerService
    private lateinit var chunkSerializer: CordaAvroSerializer<Any>
    private val payload: SessionData = mock()

    @BeforeEach
    fun setup() {
        whenever(payload.payload).thenReturn(ByteBuffer.wrap("bytes".toByteArray()))
        chunkSerializer = mock()
        chunkSerializerService = mock()
    }
    @Test
    fun `Send data when in state ERROR`() {
        val sessionEvent = buildSessionEvent(
            MessageDirection.OUTBOUND,
            "sessionId",
            null,
            SessionData(),
            contextSessionProps = emptyKeyValuePairList()
        )

        val inputState = buildSessionState(
            SessionStateType.ERROR, 0, mutableListOf(), 0, mutableListOf()
        )

        val result = SessionDataProcessorSend("key", inputState, sessionEvent, Instant.now(), chunkSerializerService, 
            payload).execute()
        assertThat(result).isNotNull
        assertThat(result.status).isEqualTo(SessionStateType.ERROR)
        assertThat(result.sendEventsState.undeliveredMessages).isEmpty()
    }

    @Test
    fun `Send data when in state CLOSING results in error`() {
        val sessionEvent = buildSessionEvent(
            MessageDirection.OUTBOUND,
            "sessionId",
            null,
            SessionData(),
            contextSessionProps = emptyKeyValuePairList()
        )

        val inputState = buildSessionState(
            SessionStateType.CLOSING, 0, mutableListOf(), 0, mutableListOf()
        )

        val result = SessionDataProcessorSend("key", inputState, sessionEvent, Instant.now(), chunkSerializerService, 
            payload).execute()
        assertThat(result).isNotNull
        assertThat(result.status).isEqualTo(SessionStateType.ERROR)
        assertThat(result.sendEventsState.undeliveredMessages.size).isEqualTo(1)
        assertThat(result.sendEventsState.undeliveredMessages.first().payload::class.java).isEqualTo(SessionError::class.java)
    }


    @Test
    fun `Send data when in state CREATED results in send`() {
        val sessionEvent = buildSessionEvent(
            MessageDirection.OUTBOUND,
            "sessionId",
            null,
            SessionData(),
            contextSessionProps = emptyKeyValuePairList()
        )
        val inputState = buildSessionState(
            SessionStateType.CREATED, 0, mutableListOf(), 0, mutableListOf()
        )

        val result = SessionDataProcessorSend("key", inputState, sessionEvent, Instant.now(), chunkSerializerService, 
            payload).execute()
        assertThat(result).isNotNull
        assertThat(result.status).isEqualTo(SessionStateType.CREATED)
        assertThat(result.sendEventsState.undeliveredMessages.size).isEqualTo(1)
        val sessionEventOutput = result.sendEventsState.undeliveredMessages.first()
        assertThat(sessionEventOutput.sequenceNum).isNotNull
        assertThat(sessionEventOutput.payload).isEqualTo(sessionEvent.payload)    }

    @Test
    fun `Send data when state is CONFIRMED`() {
        val sessionEvent = buildSessionEvent(
            MessageDirection.OUTBOUND,
            "sessionId",
            null,
            SessionData(),
            contextSessionProps = emptyKeyValuePairList()
        )
        val inputState = buildSessionState(
            SessionStateType.CONFIRMED, 0, mutableListOf(), 0, mutableListOf()
        )

        val result = SessionDataProcessorSend("key", inputState, sessionEvent, Instant.now(), chunkSerializerService, payload).execute()
        assertThat(result).isNotNull
        assertThat(result.status).isEqualTo(SessionStateType.CONFIRMED)
        assertThat(result.sendEventsState.undeliveredMessages.size).isEqualTo(1)
        val sessionEventOutput = result.sendEventsState.undeliveredMessages.first()
        assertThat(sessionEventOutput.sequenceNum).isNotNull
        assertThat(sessionEventOutput.payload).isEqualTo(sessionEvent.payload)
    }

    @Test
    fun `Send large data when state is CONFIRMED`() {
        val sessionEvent = buildSessionEvent(
            MessageDirection.OUTBOUND,
            "sessionId",
            null,
            SessionData(),
            contextSessionProps = emptyKeyValuePairList()
        )
        val inputState = buildSessionState(
            SessionStateType.CONFIRMED, 0, mutableListOf(), 0, mutableListOf()
        )

        whenever(chunkSerializerService.generateChunks(any())).thenReturn(listOf(Chunk(), Chunk(), Chunk()))
        val result = SessionDataProcessorSend("key", inputState, sessionEvent, Instant.now(), chunkSerializerService, payload).execute()
        assertThat(result).isNotNull
        assertThat(result.status).isEqualTo(SessionStateType.CONFIRMED)
        assertThat(result.sendEventsState.undeliveredMessages.size).isEqualTo(3)
        val sessionEventOutput = result.sendEventsState.undeliveredMessages.first()
        assertThat(sessionEventOutput.sequenceNum).isNotNull
        assertThat(sessionEventOutput.payload).isEqualTo(SessionData(Chunk(), null))
    }
}
