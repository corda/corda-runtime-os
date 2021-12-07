package net.corda.flow.mapper.factory

import net.corda.flow.mapper.FlowMapperMetaData
import net.corda.flow.mapper.executor.FlowMapperEventExecutor

interface FlowMapperEventExecutorFactory {

    /**
     * Create [FlowMapperEventExecutor] using [flowMetaData]
     */
    fun create(flowMetaData: FlowMapperMetaData): FlowMapperEventExecutor
}
