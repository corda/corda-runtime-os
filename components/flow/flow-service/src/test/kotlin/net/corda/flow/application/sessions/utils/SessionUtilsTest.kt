package net.corda.flow.application.sessions.utils

import net.corda.data.flow.state.session.SessionState
import net.corda.data.flow.state.session.SessionStateType
import net.corda.flow.fiber.FlowFiber
import net.corda.flow.fiber.FlowFiberExecutionContext
import net.corda.flow.fiber.FlowFiberService
import net.corda.flow.state.FlowCheckpoint
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.flow.pipeline.exceptions.FlowPlatformException
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.mockito.Mockito.mock
import org.mockito.kotlin.whenever

class SessionUtilsTest {

    private val flowFiberService: FlowFiberService = mock()
    private val flowFiber: FlowFiber = mock()
    private val executionContext: FlowFiberExecutionContext = mock()
    private val checkpoint: FlowCheckpoint = mock()
    private val sessionState: SessionState = mock()
    private val SESSION_ID = "SESSION_ID"

    @BeforeEach
    fun setup() {
        whenever(flowFiberService.getExecutingFiber()).thenReturn(flowFiber)
        whenever(flowFiber.getExecutionContext()).thenReturn(executionContext)
        whenever(executionContext.flowCheckpoint).thenReturn(checkpoint)
        whenever(checkpoint.getSessionState(SESSION_ID)).thenReturn(sessionState)
    }
    @ParameterizedTest
    @EnumSource(value = SessionStateType::class, names = ["ERROR", "CLOSED"])
    fun `verifySessionStatusNotErrorOrClose throws on error or closed`(sessionStateType: SessionStateType) {

        whenever(sessionState.status).thenReturn(sessionStateType)
        assertThrows<CordaRuntimeException> {
            SessionUtils.verifySessionStatusNotErrorOrClose(SESSION_ID, flowFiberService)
        }
    }

    @ParameterizedTest
    @EnumSource(value = SessionStateType::class, names = ["CREATED", "CONFIRMED", "CLOSING"])
    fun `verifySessionStatusNotErrorOrClose doesnt throw on all types`(sessionStateType: SessionStateType) {
        whenever(sessionState.status).thenReturn(sessionStateType)
        assertDoesNotThrow {
            SessionUtils.verifySessionStatusNotErrorOrClose(SESSION_ID, flowFiberService)
        }
    }

    @Test
    fun `checkPayloadMaxSize does not throw an exception when payload size is equal to max allowed size`() {
        val maxPayloadSize = 1024
        whenever(checkpoint.maxPayloadSize).thenReturn(maxPayloadSize.toLong())
        val payload = ByteArray(maxPayloadSize)

        assertDoesNotThrow {
            SessionUtils.checkPayloadMaxSize(payload, flowFiberService)
        }
    }

    @Test
    fun `checkPayloadMaxSize does not throw an exception when payload size is less than max allowed size`() {
        val maxPayloadSize = 1024
        whenever(checkpoint.maxPayloadSize).thenReturn(maxPayloadSize.toLong())
        val payload = ByteArray(maxPayloadSize - 1)

        assertDoesNotThrow {
            SessionUtils.checkPayloadMaxSize(payload, flowFiberService)
        }
    }

    @Test
    fun `checkPayloadMaxSize throws an exception when payload size exceeds max allowed size`() {
        val maxPayloadSize = 1024
        whenever(checkpoint.maxPayloadSize).thenReturn(maxPayloadSize.toLong())
        val payload = ByteArray(maxPayloadSize + 1)

        assertThrows<FlowPlatformException> {
            SessionUtils.checkPayloadMaxSize(payload, flowFiberService)
        }
    }
}