package net.corda.flow.mapper.factory

import net.corda.data.flow.event.mapper.FlowMapperEvent
import net.corda.data.flow.state.mapper.FlowMapperState
import net.corda.flow.mapper.FlowMapperTopics
import net.corda.flow.mapper.executor.FlowMapperEventExecutor

interface FlowMapperEventExecutorFactory {

    /**
     * Create [FlowMapperEventExecutor] from the:
     * - [eventKey] Record Key
     * - [flowMapperEvent] Record event
     * - [state] Current state for the [eventKey]
     * - [flowMapperTopics] All possible output topics
     */
    fun create(eventKey: String,
               flowMapperEvent: FlowMapperEvent,
               state: FlowMapperState?,
               flowMapperTopics: FlowMapperTopics
    ): FlowMapperEventExecutor
}
