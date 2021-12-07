package net.corda.flow.mapper.executor

import net.corda.flow.mapper.FlowMapperResult

/**
 * Execute Flow Mapper Events
 */
interface FlowMapperEventExecutor{
    fun execute(): FlowMapperResult
}
