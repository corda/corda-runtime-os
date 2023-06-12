package net.corda.flow.metrics.impl

import com.fasterxml.jackson.databind.ObjectMapper
import net.corda.flow.metrics.FlowMetricsRecorder
import net.corda.flow.state.FlowCheckpoint
import net.corda.utilities.time.UTCClock
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

internal class FlowMetricsImplTest {

    private companion object {
        val objectMapper = ObjectMapper()
        val clock = UTCClock()
    }

    @Test
    fun `handles replay`() {
        val sessionId = "sessionId"
        val flowEventType = "SessionData"
        val flowName = "myFlow"
        val isSubFlow = false
        val sequenceNumber = 1L
        val highestSeenSequenceNumber = 3L
        val recordTimestamp = 100L

        val flowMetricsStateString: String
        val checkpoint = mock<FlowCheckpoint>()
        val flowMetricStateObject = FlowMetricsImpl.FlowMetricState()
        val sessionMetricStateObject = FlowMetricsImpl.SessionMetricState()

        sessionMetricStateObject.highestSeenSequenceNumber = highestSeenSequenceNumber
        flowMetricStateObject.sessionMetricStateBySessionId[sessionId] = sessionMetricStateObject

        val flowMetricsRecorder = mock<FlowMetricsRecorder>()
        flowMetricsStateString = objectMapper.writeValueAsString(flowMetricStateObject)

        whenever(checkpoint.flowMetricsState).thenReturn(flowMetricsStateString)

        val flowMetricsImpl = FlowMetricsImpl(clock, flowMetricsRecorder, checkpoint, recordTimestamp)
        flowMetricsImpl.flowSessionMessageSent(flowEventType, sessionId, sequenceNumber)

        verify(flowMetricsRecorder, times(1)).recordFlowSessionMessagesReplayed(flowName, isSubFlow, flowEventType)
        verify(flowMetricsRecorder, times(1)).recordFlowSessionMessagesSent(flowName, isSubFlow, flowEventType)
    }

    @Test
    fun `handles out-of-order`() {
        val sessionId = "sessionId"
        val flowEventType = "SessionData"
        val flowName = "myFlow"
        val isSubFlow = false
        val sequenceNumber = 3L
        val highestSeenSequenceNumber = 1L
        val recordTimestamp = 100L

        val flowMetricsStateString: String
        val checkpoint = mock<FlowCheckpoint>()
        val flowMetricStateObject = FlowMetricsImpl.FlowMetricState()
        val sessionMetricStateObject = FlowMetricsImpl.SessionMetricState()

        sessionMetricStateObject.highestSeenSequenceNumber = highestSeenSequenceNumber
        flowMetricStateObject.sessionMetricStateBySessionId[sessionId] = sessionMetricStateObject

        val flowMetricsRecorder = mock<FlowMetricsRecorder>()
        flowMetricsStateString = objectMapper.writeValueAsString(flowMetricStateObject)

        whenever(checkpoint.flowMetricsState).thenReturn(flowMetricsStateString)

        val flowMetricsImpl = FlowMetricsImpl(clock, flowMetricsRecorder, checkpoint, recordTimestamp)
        flowMetricsImpl.flowSessionMessageSent(flowEventType, sessionId, sequenceNumber)

        verify(flowMetricsRecorder, times(0)).recordFlowSessionMessagesReplayed(flowName, isSubFlow, flowEventType)
        verify(flowMetricsRecorder, times(1)).recordFlowSessionMessagesSent(flowName, isSubFlow, flowEventType)
    }

    @Test
    fun `handles in-order non-replay`() {
        val sessionId = "sessionId"
        val flowEventType = "SessionData"
        val flowName = "myFlow"
        val isSubFlow = false
        var sequenceNumber = 1L
        val highestSeenSequenceNumber = 0L
        val recordTimestamp = 100L

        val flowMetricsStateString: String
        val checkpoint = mock<FlowCheckpoint>()
        val flowMetricStateObject = FlowMetricsImpl.FlowMetricState()
        val sessionMetricStateObject = FlowMetricsImpl.SessionMetricState()

        sessionMetricStateObject.highestSeenSequenceNumber = highestSeenSequenceNumber
        flowMetricStateObject.sessionMetricStateBySessionId[sessionId] = sessionMetricStateObject

        val flowMetricsRecorder = mock<FlowMetricsRecorder>()
        flowMetricsStateString = objectMapper.writeValueAsString(flowMetricStateObject)

        whenever(checkpoint.flowMetricsState).thenReturn(flowMetricsStateString)

        val flowMetricsImpl = FlowMetricsImpl(clock, flowMetricsRecorder, checkpoint, recordTimestamp)
        flowMetricsImpl.flowSessionMessageSent(flowEventType, sessionId, sequenceNumber)

        verify(flowMetricsRecorder, times(0)).recordFlowSessionMessagesReplayed(flowName, isSubFlow, flowEventType)
        verify(flowMetricsRecorder, times(1)).recordFlowSessionMessagesSent(flowName, isSubFlow, flowEventType)
    }

    @Test
    fun `handles replay where both sequence numbers and HSSN are the same`() {
        val sessionId = "sessionId"
        val flowEventType = "SessionData"
        val flowName = "myFlow"
        val isSubFlow = false
        val sequenceNumber = 3L
        val highestSeenSequenceNumber = 3L
        val recordTimestamp = 100L

        val flowMetricsStateString: String
        val checkpoint = mock<FlowCheckpoint>()
        val flowMetricStateObject = FlowMetricsImpl.FlowMetricState()
        val sessionMetricStateObject = FlowMetricsImpl.SessionMetricState()

        sessionMetricStateObject.highestSeenSequenceNumber = highestSeenSequenceNumber
        flowMetricStateObject.sessionMetricStateBySessionId[sessionId] = sessionMetricStateObject

        val flowMetricsRecorder = mock<FlowMetricsRecorder>()
        flowMetricsStateString = objectMapper.writeValueAsString(flowMetricStateObject)

        whenever(checkpoint.flowMetricsState).thenReturn(flowMetricsStateString)

        val flowMetricsImpl = FlowMetricsImpl(clock, flowMetricsRecorder, checkpoint, recordTimestamp)
        flowMetricsImpl.flowSessionMessageSent(flowEventType, sessionId, sequenceNumber)

        verify(flowMetricsRecorder, times(1)).recordFlowSessionMessagesReplayed(flowName, isSubFlow, flowEventType)
        verify(flowMetricsRecorder, times(1)).recordFlowSessionMessagesSent(flowName, isSubFlow, flowEventType)
    }

    @Test
    fun `handles out-of-order non-replay multiple messages`() {
        val sessionId = "sessionId"
        val flowEventType = "SessionData"
        val flowName = "myFlow"
        val isSubFlow = false
        var sequenceNumber = 6L
        val highestSeenSequenceNumber = 3L
        val recordTimestamp = 100L

        val flowMetricsStateString: String
        val checkpoint = mock<FlowCheckpoint>()
        val flowMetricStateObject = FlowMetricsImpl.FlowMetricState()
        val sessionMetricStateObject = FlowMetricsImpl.SessionMetricState()

        sessionMetricStateObject.highestSeenSequenceNumber = highestSeenSequenceNumber
        flowMetricStateObject.sessionMetricStateBySessionId[sessionId] = sessionMetricStateObject

        val flowMetricsRecorder = mock<FlowMetricsRecorder>()
        flowMetricsStateString = objectMapper.writeValueAsString(flowMetricStateObject)

        whenever(checkpoint.flowMetricsState).thenReturn(flowMetricsStateString)

        val flowMetricsImpl = FlowMetricsImpl(clock, flowMetricsRecorder, checkpoint, recordTimestamp)
        flowMetricsImpl.flowSessionMessageSent(flowEventType, sessionId, sequenceNumber)

        verify(flowMetricsRecorder, times(0)).recordFlowSessionMessagesReplayed(flowName, isSubFlow, flowEventType)
        verify(flowMetricsRecorder, times(1)).recordFlowSessionMessagesSent(flowName, isSubFlow, flowEventType)

        sequenceNumber = 5L
        flowMetricsImpl.flowSessionMessageSent(flowEventType, sessionId, sequenceNumber)

        verify(flowMetricsRecorder, times(0)).recordFlowSessionMessagesReplayed(flowName, isSubFlow, flowEventType)
        verify(flowMetricsRecorder, times(2)).recordFlowSessionMessagesSent(flowName, isSubFlow, flowEventType)
    }
}