package net.corda.flow.metrics.impl

import com.fasterxml.jackson.databind.ObjectMapper
import net.corda.flow.metrics.FlowMetricsRecorder
import net.corda.flow.state.FlowCheckpoint
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

internal class FlowMetricsImplTest {

    private companion object {
        val objectMapper = ObjectMapper()
    }

    @Test
    fun flowSessionMessageSent() {
        val sessionId = "sessionId"
        val flowEventType = "SessionData"
        val sequenceNumber = 1L
        val HSSN = 3L

        val flowMetricsStateString: String
        val checkpoint = mock<FlowCheckpoint>()
        val flowMetricStateObject = FlowMetricsImpl.FlowMetricState()
        val sessionMetricStateObject = FlowMetricsImpl.SessionMetricState()

        sessionMetricStateObject.highestSeenSequenceNumber = HSSN
        flowMetricStateObject.sessionMetricStateBySessionId[sessionId] = sessionMetricStateObject

        val flowMetricsRecorder = mock<FlowMetricsRecorder>()
        flowMetricsStateString = objectMapper.writeValueAsString(flowMetricStateObject)

        whenever(checkpoint.flowMetricsState).thenReturn(flowMetricsStateString)

        val flowMetricsImpl = FlowMetricsImpl(mock(), flowMetricsRecorder, checkpoint, mock())
        flowMetricsImpl.flowSessionMessageSent(flowEventType, sessionId, sequenceNumber)

        verify(flowMetricsRecorder, times(1)).recordFlowSessionMessagesReplayed(flowEventType)
        verify(flowMetricsRecorder, times(1)).recordFlowSessionMessagesSent(flowEventType)
    }
}