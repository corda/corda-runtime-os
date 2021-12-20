package net.corda.flow.mapper.factory

import net.corda.data.flow.event.mapper.FlowMapperEvent
import net.corda.data.flow.state.mapper.FlowMapperState
import net.corda.flow.mapper.executor.FlowMapperEventExecutor

interface FlowMapperEventExecutorFactory {

    /**
     * Create [FlowMapperEventExecutor] from the:
     * - [eventKey] Record Key
     * - [flowMapperEvent] Record event
     * - [state] Current state for the [eventKey]
     */
    fun create(eventKey: String,
               flowMapperEvent: FlowMapperEvent,
               state: FlowMapperState?,
    ): FlowMapperEventExecutor
}
