package net.corda.flow.mapper.impl.executor

import net.corda.flow.mapper.FlowMapperResult
import net.corda.flow.mapper.executor.FlowMapperEventExecutor
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.debug
import java.util.Collections.emptyList

class ExecuteCleanupEventExecutor(private val key: String) : FlowMapperEventExecutor {

    private companion object {
        private val log = contextLogger()
    }

    override fun execute(): FlowMapperResult {
        log.debug {"Executing cleanup for flow mapper state on key $key"}
        return FlowMapperResult(null, emptyList())
    }
}