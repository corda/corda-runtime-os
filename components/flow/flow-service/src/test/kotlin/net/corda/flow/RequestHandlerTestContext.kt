package net.corda.flow

import net.corda.data.flow.FlowKey
import net.corda.data.flow.FlowStartContext
import net.corda.data.flow.event.FlowEvent
import net.corda.flow.pipeline.FlowEventContext
import net.corda.flow.pipeline.factory.FlowMessageFactory
import net.corda.flow.pipeline.factory.RecordFactory
import net.corda.flow.pipeline.sessions.FlowSessionManager
import net.corda.flow.state.FlowCheckpoint
import net.corda.flow.state.FlowStack
import net.corda.messaging.api.records.Record
import net.corda.session.manager.SessionManager
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class RequestHandlerTestContext<PAYLOAD>(val payload: PAYLOAD) {
    val flowId = "flow id"
    val flowEvent = FlowEvent()
    val flowMessageFactory = mock<FlowMessageFactory>()
    val flowSessionManager = mock<FlowSessionManager>()
    val sessionManager = mock<SessionManager>()
    val recordFactory = mock<RecordFactory>()
    val recordList = mutableListOf<Record<*, *>>()
    val flowStack = mock<FlowStack>()
    val holdingIdentity = BOB_X500_HOLDING_IDENTITY
    val flowStartContext = FlowStartContext()
    val flowCheckpoint: FlowCheckpoint = mock()

    init {
        flowStartContext.identity = holdingIdentity
        flowStartContext.statusKey = FlowKey("request id", holdingIdentity)

        whenever(flowCheckpoint.flowId).thenReturn(flowId)
        whenever(flowCheckpoint.flowStack).thenReturn(flowStack)
        whenever(flowCheckpoint.flowStartContext).thenReturn(flowStartContext)
        whenever(flowCheckpoint.holdingIdentity).thenReturn(holdingIdentity)
    }

    val flowEventContext = FlowEventContext(flowCheckpoint, flowEvent, payload, mock(), recordList)
}

