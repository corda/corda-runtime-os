package net.corda.flow.mapper.factory

import net.corda.data.flow.event.mapper.FlowMapperEvent
import net.corda.data.flow.state.mapper.FlowMapperState
import net.corda.flow.mapper.executor.FlowMapperEventExecutor
import java.time.Instant

interface FlowMapperEventExecutorFactory {

    /**
     * Create [FlowMapperEventExecutor] from the:
     * - [eventKey] Record Key
     * - [flowMapperEvent] Record event
     * - [state] Current state for the [eventKey]
     * - [instant] Used for timestamps of any new events generated such as error events
     * @return A flow mapper executor based on the event type
     */
    fun create(eventKey: String,
               flowMapperEvent: FlowMapperEvent,
               state: FlowMapperState?,
               instant: Instant = Instant.now(),
    ): FlowMapperEventExecutor
}
