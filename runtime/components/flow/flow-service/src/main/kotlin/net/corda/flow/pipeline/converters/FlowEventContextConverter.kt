package net.corda.flow.pipeline.converters

import net.corda.data.flow.state.checkpoint.Checkpoint
import net.corda.flow.pipeline.events.FlowEventContext
import net.corda.messaging.api.processor.StateAndEventProcessor

/**
 * The [FlowEventContextConverter] converts the values in a [FlowEventContext] to a [StateAndEventProcessor.Response]
 */
interface FlowEventContextConverter {

    /**
     * Creates an instance of the [StateAndEventProcessor.Response] from a [FlowEventContext]
     *
     * @param flowContext The [FlowEventContext] used to generate the state and event output
     *
     * @return the populated [StateAndEventProcessor.Response] instance.
     */
    fun convert(flowContext: FlowEventContext<*>): StateAndEventProcessor.Response<Checkpoint>
}
