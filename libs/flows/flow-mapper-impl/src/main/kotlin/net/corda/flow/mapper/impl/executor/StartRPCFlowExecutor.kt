package net.corda.flow.mapper.impl.executor

import net.corda.data.flow.event.FlowEvent
import net.corda.data.flow.state.mapper.FlowMapperState
import net.corda.data.flow.state.mapper.FlowMapperStateType
import net.corda.flow.mapper.FlowKeyGenerator
import net.corda.flow.mapper.FlowMapperMetaData
import net.corda.flow.mapper.FlowMapperResult
import net.corda.flow.mapper.executor.FlowMapperEventExecutor
import net.corda.messaging.api.records.Record
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.base.util.contextLogger

class StartRPCFlowExecutor(
    private val flowMapperMetaData: FlowMapperMetaData
) : FlowMapperEventExecutor {

    private companion object {
        private val log = contextLogger()
    }

    private val flowKeyGenerator = FlowKeyGenerator()

    override fun execute(): FlowMapperResult {
        val flowMapperState = flowMapperMetaData.flowMapperState
        return if (flowMapperState == null) {
            val outputTopic = flowMapperMetaData.outputTopic ?: throw CordaRuntimeException("Output topic should not be null for " +
                    "StartRPCFlow on key ${flowMapperMetaData.flowMapperEventKey}")
            val identity = flowMapperMetaData.holdingIdentity ?: throw CordaRuntimeException("Holding Identity should not be null for " +
                    "StartRPCFlow on key ${flowMapperMetaData.flowMapperEventKey}")
            val flowKey = flowKeyGenerator.generateFlowKey(identity)
            val updatedState = FlowMapperState(flowKey, null, FlowMapperStateType.OPEN)
            val flowEvent = FlowEvent(flowKey, flowMapperMetaData.payload)
            FlowMapperResult(
                updatedState,
                mutableListOf(Record(outputTopic, flowKey, flowEvent))
            )
        } else {
            //duplicate
            log.warn("Duplicate StartRPCFlow event received. Key: ${flowMapperMetaData.flowMapperEventKey}, " +
                    "Event: ${flowMapperMetaData.flowMapperEvent} ")
            FlowMapperResult(flowMapperState, mutableListOf())
        }
    }
}
