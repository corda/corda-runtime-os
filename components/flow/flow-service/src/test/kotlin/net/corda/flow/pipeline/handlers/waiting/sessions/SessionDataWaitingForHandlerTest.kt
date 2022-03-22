package net.corda.flow.pipeline.handlers.waiting.sessions

import net.corda.data.flow.FlowKey
import net.corda.data.flow.event.FlowEvent
import net.corda.data.flow.event.SessionEvent
import net.corda.data.flow.event.session.SessionClose
import net.corda.data.flow.event.session.SessionData
import net.corda.data.flow.state.Checkpoint
import net.corda.data.flow.state.session.SessionState
import net.corda.data.identity.HoldingIdentity
import net.corda.flow.fiber.FlowContinuation
import net.corda.flow.pipeline.FlowEventContext
import net.corda.flow.pipeline.FlowProcessingException
import net.corda.flow.pipeline.sandbox.FlowSandboxService
import net.corda.sandboxgroupcontext.SandboxGroupContext
import net.corda.session.manager.SessionManager
import net.corda.v5.application.services.serialization.SerializationService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.nio.ByteBuffer

class SessionDataWaitingForHandlerTest {

    private companion object {
        const val FLOW_ID = "flow id"
        const val SESSION_ID = "session id"
        const val ANOTHER_SESSION_ID = "another session id"
        const val DATA = "data"
        const val MORE_DATA = "more data"
        val HOLDING_IDENTITY = HoldingIdentity("x500 name", "group id")
        val FLOW_KEY = FlowKey(FLOW_ID, HOLDING_IDENTITY)
    }

    private val serializationService = mock<SerializationService>()
    private val sandboxGroupContext = mock<SandboxGroupContext>().apply {
        whenever(get(any(), eq(SerializationService::class.java))).thenReturn(serializationService)
    }
    private val flowSandboxService = mock<FlowSandboxService>().apply {
        whenever(get(any())).thenReturn(sandboxGroupContext)
    }

    private val sessionManager = mock<SessionManager>()

    private val sessionDataWaitingForHandler = SessionDataWaitingForHandler(flowSandboxService, sessionManager)

    @Test
    fun `Receiving all required session data events returns a FlowContinuation#Run`() {
        val sessionState = SessionState().apply {
            sessionId = SESSION_ID
        }
        val anotherSessionState = SessionState().apply {
            sessionId = ANOTHER_SESSION_ID
        }

        whenever(sessionManager.getNextReceivedEvent(sessionState)).thenReturn(SessionEvent().apply {
            sessionId = SESSION_ID
            payload = SessionData(ByteBuffer.wrap(DATA.toByteArray()))
            sequenceNum = 1
        })
        whenever(sessionManager.getNextReceivedEvent(anotherSessionState)).thenReturn(SessionEvent().apply {
            sessionId = ANOTHER_SESSION_ID
            payload = SessionData(ByteBuffer.wrap(MORE_DATA.toByteArray()))
            sequenceNum = 1
        })
        whenever(serializationService.deserialize(eq(DATA.toByteArray()), any<Class<*>>())).thenReturn(DATA)
        whenever(serializationService.deserialize(eq(MORE_DATA.toByteArray()), any<Class<*>>())).thenReturn(MORE_DATA)

        val inputContext = FlowEventContext(
            checkpoint = Checkpoint().apply {
                flowKey = FLOW_KEY
                sessions = listOf(sessionState, anotherSessionState)
            },
            inputEvent = FlowEvent(FLOW_KEY, Unit),
            inputEventPayload = Unit,
            outputRecords = emptyList()
        )

        val continuation = sessionDataWaitingForHandler.runOrContinue(
            inputContext,
            net.corda.data.flow.state.waiting.SessionData(listOf(SESSION_ID, ANOTHER_SESSION_ID))
        )

        assertEquals(FlowContinuation.Run(mapOf(SESSION_ID to DATA, ANOTHER_SESSION_ID to MORE_DATA)), continuation)
    }

    @Test
    fun `Receiving all required session data events acknowledges the received events`() {
        val data = "data"
        val moreData = "more data"
        val sessionState = SessionState().apply {
            sessionId = SESSION_ID
        }
        val anotherSessionState = SessionState().apply {
            sessionId = ANOTHER_SESSION_ID
        }

        whenever(sessionManager.getNextReceivedEvent(sessionState)).thenReturn(SessionEvent().apply {
            sessionId = SESSION_ID
            payload = SessionData(ByteBuffer.wrap(DATA.toByteArray()))
            sequenceNum = 1
        })
        whenever(sessionManager.getNextReceivedEvent(anotherSessionState)).thenReturn(SessionEvent().apply {
            sessionId = ANOTHER_SESSION_ID
            payload = SessionData(ByteBuffer.wrap(MORE_DATA.toByteArray()))
            sequenceNum = 1
        })
        whenever(serializationService.deserialize(eq(DATA.toByteArray()), any<Class<*>>())).thenReturn(data)
        whenever(serializationService.deserialize(eq(MORE_DATA.toByteArray()), any<Class<*>>())).thenReturn(moreData)

        val inputContext = FlowEventContext(
            checkpoint = Checkpoint().apply {
                flowKey = FLOW_KEY
                sessions = listOf(sessionState, anotherSessionState)
            },
            inputEvent = FlowEvent(FLOW_KEY, Unit),
            inputEventPayload = Unit,
            outputRecords = emptyList()
        )

        sessionDataWaitingForHandler.runOrContinue(
            inputContext,
            net.corda.data.flow.state.waiting.SessionData(listOf(SESSION_ID, ANOTHER_SESSION_ID))
        )

        verify(sessionManager, times(2)).acknowledgeReceivedEvent(any(), eq(1))
    }

    @Test
    fun `Requiring more session events to be received returns a FlowContinuation#Continue`() {
        val sessionState = SessionState().apply {
            sessionId = SESSION_ID
        }
        val anotherSessionState = SessionState().apply {
            sessionId = ANOTHER_SESSION_ID
        }

        whenever(sessionManager.getNextReceivedEvent(sessionState)).thenReturn(SessionEvent().apply {
            sessionId = SESSION_ID
            payload = SessionData(ByteBuffer.wrap(DATA.toByteArray()))
            sequenceNum = 1
        })

        val inputContext = FlowEventContext(
            checkpoint = Checkpoint().apply {
                flowKey = FLOW_KEY
                sessions = listOf(sessionState, anotherSessionState)
            },
            inputEvent = FlowEvent(FLOW_KEY, Unit),
            inputEventPayload = Unit,
            outputRecords = emptyList()
        )

        val continuation = sessionDataWaitingForHandler.runOrContinue(
            inputContext,
            net.corda.data.flow.state.waiting.SessionData(listOf(SESSION_ID, ANOTHER_SESSION_ID))
        )

        assertEquals(FlowContinuation.Continue, continuation)
    }

    @Test
    fun `Receiving a non-session data event throws an exception`() {
        val sessionState = SessionState().apply {
            sessionId = SESSION_ID
        }
        val anotherSessionState = SessionState().apply {
            sessionId = ANOTHER_SESSION_ID
        }

        whenever(sessionManager.getNextReceivedEvent(sessionState)).thenReturn(SessionEvent().apply {
            sessionId = SESSION_ID
            payload = SessionData(ByteBuffer.wrap(DATA.toByteArray()))
            sequenceNum = 1
        })
        whenever(sessionManager.getNextReceivedEvent(anotherSessionState)).thenReturn(SessionEvent().apply {
            sessionId = ANOTHER_SESSION_ID
            payload = SessionClose()
            sequenceNum = 1
        })

        val inputContext = FlowEventContext(
            checkpoint = Checkpoint().apply {
                flowKey = FLOW_KEY
                sessions = listOf(sessionState, anotherSessionState)
            },
            inputEvent = FlowEvent(FLOW_KEY, Unit),
            inputEventPayload = Unit,
            outputRecords = emptyList()
        )

        assertThrows<FlowProcessingException> {
            sessionDataWaitingForHandler.runOrContinue(
                inputContext,
                net.corda.data.flow.state.waiting.SessionData(listOf(SESSION_ID, ANOTHER_SESSION_ID))
            )
        }
    }

    @Test
    fun `Throws an exception if there is no checkpoint`() {
        val inputContext = FlowEventContext(
            checkpoint = null,
            inputEvent = FlowEvent(FLOW_KEY, Unit),
            inputEventPayload = Unit,
            outputRecords = emptyList()
        )

        assertThrows<FlowProcessingException> {
            sessionDataWaitingForHandler.runOrContinue(inputContext, net.corda.data.flow.state.waiting.SessionData())
        }
    }
}