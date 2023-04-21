package net.corda.flow.mapper.impl.executor

import net.corda.data.flow.event.FlowEvent
import net.corda.data.flow.event.StartFlow
import net.corda.data.flow.state.mapper.FlowMapperState
import net.corda.data.flow.state.mapper.FlowMapperStateType
import net.corda.flow.mapper.FlowMapperResult
import net.corda.flow.mapper.executor.FlowMapperEventExecutor
import net.corda.messaging.api.records.Record
import net.corda.utilities.debug
import org.slf4j.LoggerFactory

class StartFlowExecutor(
    private val eventKey: String,
    private val outputTopic: String,
    private val startRPCFlow: StartFlow,
    private val flowMapperState: FlowMapperState?,
) : FlowMapperEventExecutor {

    private companion object {
        private val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    override fun execute(): FlowMapperResult {
        return if (flowMapperState == null) {
            val flowId = generateFlowId()
            val newState = FlowMapperState(flowId, null, FlowMapperStateType.OPEN)
            val flowEvent = FlowEvent(flowId, startRPCFlow)
            FlowMapperResult(
                newState,
                mutableListOf(Record(outputTopic, flowId, flowEvent))
            )
        } else {
            log.debug { "Duplicate StartRPCFlow event received. Key: $eventKey, Event: $startRPCFlow " }
            FlowMapperResult(flowMapperState, mutableListOf())
        }
    }
}
