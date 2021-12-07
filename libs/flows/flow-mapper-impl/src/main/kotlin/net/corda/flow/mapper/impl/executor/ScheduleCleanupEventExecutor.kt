package net.corda.flow.mapper.impl.executor

import net.corda.data.flow.state.mapper.FlowMapperStateType
import net.corda.flow.mapper.FlowMapperMetaData
import net.corda.flow.mapper.FlowMapperResult
import net.corda.flow.mapper.executor.FlowMapperEventExecutor
import net.corda.v5.base.util.contextLogger
import java.util.Collections.emptyList

class ScheduleCleanupEventExecutor(
    private val flowMapperMetaData: FlowMapperMetaData
) : FlowMapperEventExecutor {

    private companion object {
        private val log = contextLogger()
    }

    override fun execute(): FlowMapperResult {
        val flowMapperState = flowMapperMetaData.flowMapperState

        return if (flowMapperState == null) {
            log.warn("Tried to cleanup mapper state which was already null on key ${flowMapperMetaData.flowMapperEventKey}")
            FlowMapperResult(flowMapperState, emptyList())
        } else {
            flowMapperState.status = FlowMapperStateType.CLOSING
            flowMapperState.expiryTime = flowMapperMetaData.expiryTime
            FlowMapperResult(flowMapperState, emptyList())
        }
    }
}
