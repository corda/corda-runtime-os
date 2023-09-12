package net.corda.flow.pipeline.factory

import net.corda.data.flow.event.FlowEvent
import net.corda.data.flow.state.checkpoint.Checkpoint
import net.corda.libs.configuration.SmartConfig
import net.corda.messaging.api.processor.StateAndEventProcessor

/**
 * [FlowEventProcessorFactory] creates instances of [FlowEventProcessor].
 */
interface FlowEventProcessorFactory {

    /**
     * Creates a [FlowEventProcessor] instance.
     *
     * @param configs The configurations used within the flow event pipeline.
     *
     * @return A [StateAndEventProcessor] instance.
     */
    fun create(configs: Map<String, SmartConfig>): StateAndEventProcessor<String, Checkpoint, FlowEvent>
}

