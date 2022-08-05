package net.corda.flow.mapper.factory

import java.time.Instant
import net.corda.data.flow.event.mapper.FlowMapperEvent
import net.corda.data.flow.state.mapper.FlowMapperState
import net.corda.flow.mapper.executor.FlowMapperEventExecutor
import net.corda.libs.configuration.SmartConfig

interface FlowMapperEventExecutorFactory {

    /**
     * Create [FlowMapperEventExecutor] from the:
     * - [eventKey] Record Key
     * - [flowMapperEvent] Record event
     * - [state] Current state for the [eventKey]
     * - [flowConfig] flow config
     * - [instant] Used for timestamps of any new events generated such as error events
     * @return A flow mapper executor based on the event type
     */
    fun create(
        eventKey: String,
        flowMapperEvent: FlowMapperEvent,
        state: FlowMapperState?,
        flowConfig: SmartConfig,
        instant: Instant = Instant.now(),
    ): FlowMapperEventExecutor
}
