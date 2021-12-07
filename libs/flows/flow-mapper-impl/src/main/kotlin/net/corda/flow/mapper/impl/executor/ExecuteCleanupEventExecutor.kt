package net.corda.flow.mapper.impl.executor

import net.corda.flow.mapper.FlowMapperResult
import net.corda.flow.mapper.executor.FlowMapperEventExecutor
import java.util.Collections.emptyList

class ExecuteCleanupEventExecutor : FlowMapperEventExecutor {

    override fun execute(): FlowMapperResult {
        return FlowMapperResult(null, emptyList())
    }
}