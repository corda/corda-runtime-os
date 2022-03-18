package net.corda.flow.pipeline.handlers.requests.sessions

import net.corda.data.flow.event.FlowEvent
import net.corda.data.flow.event.SessionEvent
import net.corda.data.flow.event.Wakeup
import net.corda.data.flow.event.session.SessionData
import net.corda.data.flow.state.session.SessionState
import net.corda.flow.RequestHandlerTestContext
import net.corda.flow.fiber.FlowIORequest
import net.corda.messaging.api.records.Record
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.nio.ByteBuffer

class SendRequestHandlerTest {
    private val sessionId1 = "s1"
    private val sessionId2 = "s2"
    private val payload1 = ByteBuffer.wrap(byteArrayOf(1))
    private val payload2 = ByteBuffer.wrap(byteArrayOf(2))
    private val sessionState1 = SessionState().apply { this.sessionId = sessionId1 }
    private val sessionState2 = SessionState().apply { this.sessionId = sessionId2 }
    private val sessionEvent1 = SessionEvent().apply { this.sessionId = sessionId1 }
    private val sessionEvent2 = SessionEvent().apply { this.sessionId = sessionId2 }
    private val testContext = RequestHandlerTestContext(Any())
    private val ioRequest = FlowIORequest.Send(mapOf(sessionId1 to payload1.array(), sessionId2 to payload2.array()))
    private val handler =
        SendRequestHandler(testContext.sessionManager, testContext.recordFactory, testContext.sessionEventFactory)

    @Suppress("Unused")
    @BeforeEach
    fun setup() {
        val flowCheckpoint = testContext.flowCheckpoint
        val sessionEventFactory = testContext.sessionEventFactory

        whenever(flowCheckpoint.getSessionState(sessionId1)).thenReturn(sessionState1)
        whenever(flowCheckpoint.getSessionState(sessionId2)).thenReturn(sessionState2)

        whenever(sessionEventFactory.create(
            eq(sessionId1),
            any(),
            argThat<SessionData> { this.payload == payload1 }
        )).thenReturn(sessionEvent1)
        whenever(sessionEventFactory.create(
            eq(sessionId2),
            any(),
            argThat<SessionData> { this.payload == payload2 }
        )).thenReturn(sessionEvent2)

        whenever(
            testContext.sessionManager.processMessageToSend(
                eq(testContext.flowId),
                eq(sessionState1),
                eq(sessionEvent1),
                any()
            )
        ).thenReturn(sessionState1)
        whenever(
            testContext.sessionManager.processMessageToSend(
                eq(testContext.flowId),
                eq(sessionState2),
                eq(sessionEvent2),
                any()
            )
        ).thenReturn(sessionState2)
    }

    @Test
    fun `test waiting for wake-up event`() {
        val waitingFor = handler.getUpdatedWaitingFor(testContext.flowEventContext, ioRequest)
        assertThat(waitingFor.value).isInstanceOf(net.corda.data.flow.state.waiting.Wakeup()::class.java)
    }

    @Test
    fun `test all send events are sent to the manager and updated states are stored in the checkpoint`() {
        handler.postProcess(testContext.flowEventContext, ioRequest)
        verify(testContext.flowCheckpoint).putSessionState(sessionState1)
        verify(testContext.flowCheckpoint).putSessionState(sessionState2)
    }

    @Test
    fun `Adds a wakeup event to the output records`() {
        val eventRecord = Record("", "", FlowEvent())
        whenever(
            testContext
                .recordFactory
                .createFlowEventRecord(eq(testContext.flowId), argThat<Wakeup> { true })
        ).thenReturn(eventRecord)

        val outputContext = handler.postProcess(testContext.flowEventContext, ioRequest)

        assertThat(outputContext.outputRecords).containsOnly(eventRecord)
    }
}