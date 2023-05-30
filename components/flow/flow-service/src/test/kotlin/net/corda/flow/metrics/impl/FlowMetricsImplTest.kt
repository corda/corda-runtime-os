package net.corda.flow.metrics.impl

import com.fasterxml.jackson.databind.ObjectMapper
import net.corda.data.flow.event.SessionEvent
import net.corda.flow.state.FlowCheckpoint
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.kotlin.whenever

internal class FlowMetricsImplTest {

    private companion object {
        val objectMapper = ObjectMapper()
    }

    private val flowMetricsState: String
    val checkpoint = mock<FlowCheckpoint>()
    val flowMetricsImpl = FlowMetricsImpl(mock(), mock(), checkpoint, mock())


    init {
        flowMetricsState = objectMapper.writeValueAsString(flowMetricsImpl.)
    }


    @Test
    fun flowSessionMessageSent() {
        val sessionEvent = mock<SessionEvent>()

        whenever(sessionEvent.sessionId).thenReturn()
        whenever(sessionEvent.payload).thenReturn()
        whenever(sessionEvent.sequenceNum).thenReturn()

        val sessionId = sessionEvent.sessionId
        val flowEventType = sessionEvent.payload::class.java.simpleName
        val sequenceNumber = sessionEvent.sequenceNum.toLong()

        whenever(checkpoint.flowMetricsState).thenReturn(flowMetricsState)


        flowMetricsImpl.flowSessionMessageSent(flowEventType, sessionId, sequenceNumber)

    }
}