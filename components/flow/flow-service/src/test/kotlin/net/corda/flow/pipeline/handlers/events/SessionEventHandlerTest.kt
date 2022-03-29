package net.corda.flow.pipeline.handlers.events

import net.corda.data.flow.FlowKey
import net.corda.data.flow.event.MessageDirection
import net.corda.data.flow.event.SessionEvent
import net.corda.data.flow.event.session.SessionAck
import net.corda.data.flow.event.session.SessionClose
import net.corda.data.flow.event.session.SessionData
import net.corda.data.flow.event.session.SessionError
import net.corda.data.flow.event.session.SessionInit
import net.corda.data.flow.state.Checkpoint
import net.corda.data.flow.state.session.SessionProcessState
import net.corda.data.flow.state.session.SessionState
import net.corda.data.flow.state.session.SessionStateType
import net.corda.data.identity.HoldingIdentity
import net.corda.flow.pipeline.FlowProcessingException
import net.corda.flow.pipeline.sandbox.FlowSandboxContextTypes
import net.corda.flow.pipeline.sandbox.FlowSandboxService
import net.corda.flow.test.utils.buildFlowEventContext
import net.corda.sandboxgroupcontext.SandboxGroupContext
import net.corda.session.manager.SessionManager
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.nio.ByteBuffer
import java.time.Instant

@Suppress("MaxLineLength")
class SessionEventHandlerTest {

    private companion object {
        const val FLOW_ID = "flow id"
        const val SESSION_ID = "session id"
        const val CPI_ID = "cpi id"
        const val INITIATING_FLOW_NAME = "Initiating flow"
        const val INITIATED_FLOW_NAME = "Initiated flow"

        val HOLDING_IDENTITY = HoldingIdentity("x500 name", "group id")
        val FLOW_KEY = FlowKey(FLOW_ID, HOLDING_IDENTITY)
    }
    private val sessionState = SessionState.newBuilder()
        .setSessionId(SESSION_ID)
        .setSessionStartTime(Instant.now())
        .setLastReceivedMessageTime(Instant.now())
        .setLastSentMessageTime(Instant.now())
        .setCounterpartyIdentity(HoldingIdentity("Alice", "group1"))
        .setSendAck(true)
        .setReceivedEventsState(SessionProcessState(0, emptyList()))
        .setSendEventsState(SessionProcessState(0, emptyList()))
        .setStatus(SessionStateType.CONFIRMED)
        .build()

    private val sandboxGroupContext = mock<SandboxGroupContext>()
    private val flowSandboxService = mock<FlowSandboxService>().apply {
        whenever(get(any())).thenReturn(sandboxGroupContext)
    }
    private val sessionManager = mock<SessionManager>().apply {
        whenever(processMessageReceived(any(), anyOrNull(), any(), any())).thenReturn(sessionState)
    }

    private val sessionEventHandler = SessionEventHandler(flowSandboxService, sessionManager)

    @Test
    fun `Receiving a session init payload creates a checkpoint if one does not exist for the initiated flow and adds the new session to it`() {
        val sessionEvent = createSessionInit()

        whenever(sandboxGroupContext.get(FlowSandboxContextTypes.INITIATING_TO_INITIATED_FLOWS, Map::class.java))
            .thenReturn(mapOf(Pair(CPI_ID, INITIATING_FLOW_NAME) to INITIATED_FLOW_NAME))
        whenever(sessionManager.getNextReceivedEvent(any())).thenReturn(sessionEvent)

        val inputContext = buildFlowEventContext(checkpoint = null, inputEventPayload = sessionEvent)
        val outputContext = sessionEventHandler.preProcess(inputContext)
        assertNotNull(outputContext.checkpoint)
        assertEquals(INITIATED_FLOW_NAME, outputContext.checkpoint?.flowStartContext?.flowClassName)
        assertEquals(1, outputContext.checkpoint?.sessions?.size)
        assertEquals(0, outputContext.outputRecords.size)
    }

    @Test
    fun `Receiving a session init payload does not create a checkpoint when the session manager returns no next received event`() {
        whenever(sessionManager.getNextReceivedEvent(any())).thenReturn(null)

        val sessionEvent = createSessionInit()
        val checkpoint = Checkpoint().apply {
            fiber = ByteBuffer.wrap(byteArrayOf(1, 1, 1, 1))
            sessions = emptyList()
        }
        val inputContext = buildFlowEventContext(checkpoint = checkpoint, inputEventPayload = sessionEvent)
        val outputContext = sessionEventHandler.preProcess(inputContext)
        assertEquals(1, outputContext.checkpoint?.sessions?.size)
        assertEquals(0, outputContext.outputRecords.size)
    }

    @Test
    fun `Receiving a session init payload throws an exception if a checkpoint already exists`() {
        val sessionEvent = createSessionInit()

        whenever(sandboxGroupContext.get(FlowSandboxContextTypes.INITIATING_TO_INITIATED_FLOWS, Map::class.java))
            .thenReturn(mapOf(Pair(CPI_ID, INITIATING_FLOW_NAME) to INITIATED_FLOW_NAME))
        whenever(sessionManager.getNextReceivedEvent(any())).thenReturn(sessionEvent)

        val checkpoint = Checkpoint().apply {
            flowKey = FLOW_KEY
            fiber = ByteBuffer.wrap(byteArrayOf(1, 1, 1, 1))
            sessions = emptyList()
        }

        val inputContext = buildFlowEventContext(checkpoint = checkpoint, inputEventPayload = sessionEvent)
        assertThrows<FlowProcessingException> { sessionEventHandler.preProcess(inputContext) }
    }

    @Test
    fun `Receiving a session init payload throws an exception if there is no matching initiated flow`() {
        val sessionEvent = createSessionInit()

        whenever(sandboxGroupContext.get(FlowSandboxContextTypes.INITIATING_TO_INITIATED_FLOWS, Map::class.java))
            .thenReturn(emptyMap<String, String>())
        whenever(sessionManager.getNextReceivedEvent(any())).thenReturn(sessionEvent)

        val inputContext = buildFlowEventContext(checkpoint = null, inputEventPayload = sessionEvent)
        assertThrows<FlowProcessingException> { sessionEventHandler.preProcess(inputContext) }
    }

    @Test
    fun `Receiving a session data payload does not create a checkpoint`() {
        whenever(sessionManager.getNextReceivedEvent(any())).thenReturn(null)

        val sessionEvent = createSessionEvent(SessionData())
        val checkpoint = Checkpoint().apply {
            fiber = ByteBuffer.wrap(byteArrayOf(1, 1, 1, 1))
            sessions = listOf(SessionState().apply {
                sessionId = SESSION_ID
            })
        }
        val inputContext = buildFlowEventContext(checkpoint = checkpoint, inputEventPayload = sessionEvent)
        val outputContext = sessionEventHandler.preProcess(inputContext)
        assertEquals(1, outputContext.checkpoint?.sessions?.size)
        assertEquals(0, outputContext.outputRecords.size)
    }

    @Test
    fun `Receiving a session ack payload does not create a checkpoint`() {
        whenever(sessionManager.getNextReceivedEvent(any())).thenReturn(null)

        val sessionEvent = createSessionEvent(SessionAck())
        val checkpoint = Checkpoint().apply {
            fiber = ByteBuffer.wrap(byteArrayOf(1, 1, 1, 1))
            sessions = listOf(SessionState().apply {
                sessionId = SESSION_ID
            })
        }
        val inputContext = buildFlowEventContext(checkpoint = checkpoint, inputEventPayload = sessionEvent)
        val outputContext = sessionEventHandler.preProcess(inputContext)
        assertEquals(1, outputContext.checkpoint?.sessions?.size)
        assertEquals(0, outputContext.outputRecords.size)
    }

    @Test
    fun `Receiving a session close payload does not create a checkpoint`() {
        whenever(sessionManager.getNextReceivedEvent(any())).thenReturn(null)

        val sessionEvent = createSessionEvent(SessionClose())
        val checkpoint = Checkpoint().apply {
            fiber = ByteBuffer.wrap(byteArrayOf(1, 1, 1, 1))
            sessions = listOf(SessionState().apply {
                sessionId = SESSION_ID
            })
        }
        val inputContext = buildFlowEventContext(checkpoint = checkpoint, inputEventPayload = sessionEvent)
        val outputContext = sessionEventHandler.preProcess(inputContext)
        assertEquals(1, outputContext.checkpoint?.sessions?.size)
        assertEquals(0, outputContext.outputRecords.size)
    }

    @Test
    fun `Receiving a session error payload does not create a checkpoint`() {
        whenever(sessionManager.getNextReceivedEvent(any())).thenReturn(null)

        val sessionEvent = createSessionEvent(SessionError())
        val checkpoint = Checkpoint().apply {
            fiber = ByteBuffer.wrap(byteArrayOf(1, 1, 1, 1))
            sessions = listOf(SessionState().apply {
                sessionId = SESSION_ID
            })
        }
        val inputContext = buildFlowEventContext(checkpoint = checkpoint, inputEventPayload = sessionEvent)
        val outputContext = sessionEventHandler.preProcess(inputContext)
        assertEquals(1, outputContext.checkpoint?.sessions?.size)
        assertEquals(0, outputContext.outputRecords.size)
    }

    @Test
    fun `Processing updates the flow's checkpoint's session state`() {
        whenever(sessionManager.getNextReceivedEvent(any())).thenReturn(null)

        val sessionEvent = createSessionEvent(SessionData())
        val inputSessionState = SessionState.newBuilder(sessionState)
            .setSessionId(SESSION_ID)
            .setLastReceivedMessageTime(sessionState.lastReceivedMessageTime.minusMillis(1000))
            .build()
        val checkpoint = Checkpoint().apply {
            fiber = ByteBuffer.wrap(byteArrayOf(1, 1, 1, 1))
            sessions = listOf(inputSessionState)
        }
        val inputContext = buildFlowEventContext(checkpoint = checkpoint, inputEventPayload = sessionEvent)
        val outputContext = sessionEventHandler.preProcess(inputContext)
        assertEquals(sessionState, outputContext.checkpoint?.sessions?.single())
        assertNotEquals(sessionState, inputSessionState)
    }

    private fun createSessionInit(): SessionEvent {
        val payload = SessionInit.newBuilder()
            .setFlowName(INITIATING_FLOW_NAME)
            .setFlowKey(FLOW_KEY)
            .setCpiId(CPI_ID)
            .setPayload(ByteBuffer.wrap(byteArrayOf()))
            .build()

        return createSessionEvent(payload)
    }

    private fun createSessionEvent(payload: Any): SessionEvent {
        return SessionEvent.newBuilder()
            .setSessionId(SESSION_ID)
            .setMessageDirection(MessageDirection.INBOUND)
            .setTimestamp(Instant.now())
            .setInitiatedIdentity(HOLDING_IDENTITY)
            .setInitiatingIdentity(HOLDING_IDENTITY)
            .setSequenceNum(1)
            .setReceivedSequenceNum(0)
            .setOutOfOrderSequenceNums(listOf(0))
            .setPayload(payload)
            .build()
    }
}