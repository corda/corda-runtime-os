package net.corda.flow.pipeline.factory

import net.corda.data.flow.event.FlowEvent
import net.corda.data.flow.state.checkpoint.Checkpoint
import net.corda.libs.configuration.SmartConfig
import net.corda.messaging.api.processor.StateAndEventProcessor
import net.corda.schema.configuration.ConfigKeys.FLOW_CONFIG

/**
 * [FlowEventProcessorFactory] creates instances of [FlowEventProcessor].
 */
interface FlowEventProcessorFactory {

    /**
     * Creates a [FlowEventProcessor] instance.
     *
     * @param config The configuration used within the flow event pipeline. The configuration block under the [FLOW_CONFIG] key should be
     * used.
     *
     * @return A [StateAndEventProcessor] instance.
     */
    fun create(config: SmartConfig): StateAndEventProcessor<String, Checkpoint, FlowEvent>
}

