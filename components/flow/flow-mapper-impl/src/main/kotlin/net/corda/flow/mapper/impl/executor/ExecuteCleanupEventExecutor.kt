package net.corda.flow.mapper.impl.executor

import net.corda.flow.mapper.FlowMapperResult
import net.corda.flow.mapper.executor.FlowMapperEventExecutor
import net.corda.metrics.CordaMetrics
import net.corda.utilities.debug
import org.slf4j.LoggerFactory
import java.util.Collections.emptyList

class ExecuteCleanupEventExecutor(private val key: String) : FlowMapperEventExecutor {

    private companion object {
        private val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    override fun execute(): FlowMapperResult {
        log.debug {"Executing cleanup for flow mapper state on key $key"}
        CordaMetrics.Metric.FlowMapperCleanupCount.builder()
            .build().increment()
        return FlowMapperResult(null, emptyList())
    }
}