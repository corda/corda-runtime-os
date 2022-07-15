package net.corda.flow.external.events.handler

import net.corda.data.flow.event.external.ExternalEventContext
import net.corda.flow.state.FlowCheckpoint

interface ExternalEventHandler<PARAMETERS : Any, RESPONSE, RESUME> {

    fun suspending(
        checkpoint: FlowCheckpoint,
        flowExternalEventContext: ExternalEventContext,
        parameters: PARAMETERS
    ): ExternalEventRecord

    fun resuming(checkpoint: FlowCheckpoint, response: RESPONSE): RESUME
}