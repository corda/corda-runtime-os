package net.corda.flow.mapper.impl.executor

import net.corda.data.flow.event.FlowEvent
import net.corda.data.flow.event.StartRPCFlow
import net.corda.data.flow.state.mapper.FlowMapperState
import net.corda.data.flow.state.mapper.FlowMapperStateType
import net.corda.flow.mapper.FlowMapperResult
import net.corda.flow.mapper.executor.FlowMapperEventExecutor
import net.corda.messaging.api.records.Record
import net.corda.v5.base.util.contextLogger

class StartRPCFlowExecutor(
    private val eventKey: String,
    private val outputTopic: String,
    private val startRPCFlow: StartRPCFlow,
    private val flowMapperState: FlowMapperState?,
) : FlowMapperEventExecutor {

    private companion object {
        private val log = contextLogger()
    }


    override fun execute(): FlowMapperResult {
        return if (flowMapperState == null) {
            val identity = startRPCFlow.rpcUsername
            val flowKey = generateFlowKey(identity)
            val newState = FlowMapperState(flowKey, null, FlowMapperStateType.OPEN)
            val flowEvent = FlowEvent(flowKey, startRPCFlow)
            FlowMapperResult(
                newState,
                mutableListOf(Record(outputTopic, flowKey, flowEvent))
            )
        } else {
            //duplicate
            log.warn("Duplicate StartRPCFlow event received. Key: $eventKey, " +
                    "Event: $startRPCFlow ")
            FlowMapperResult(flowMapperState, mutableListOf())
        }
    }
}
