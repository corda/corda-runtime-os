package net.corda.flow.pipeline.converters.impl

import net.corda.data.flow.state.checkpoint.Checkpoint
import net.corda.flow.pipeline.converters.FlowEventContextConverter
import net.corda.flow.pipeline.events.FlowEventContext
import net.corda.flow.state.impl.CheckpointMetadataKeys
import net.corda.libs.statemanager.api.Metadata
import net.corda.messaging.api.processor.StateAndEventProcessor
import net.corda.messaging.api.processor.StateAndEventProcessor.State
import org.osgi.service.component.annotations.Component

@Suppress("Unused")
@Component(service = [FlowEventContextConverter::class])
class FlowEventContextConverterImpl : FlowEventContextConverter {
    override fun convert(flowContext: FlowEventContext<*>): StateAndEventProcessor.Response<Checkpoint> {
        return StateAndEventProcessor.Response(
            State(flowContext.checkpoint.toAvro(), metadata = getMetaData(flowContext)),
            flowContext.outputRecords,
            flowContext.sendToDlq
        )
    }

    private fun getMetaData(flowContext: FlowEventContext<*>): Metadata {
        val newMeta = if (flowContext.checkpoint.isCompleted) {
            mapOf(CheckpointMetadataKeys.STATE_META_CHECKPOINT_TERMINATED_KEY to true)
        } else emptyMap()
        return flowContext.metadata?.let {
            Metadata(it + newMeta)
        } ?: Metadata(newMeta)
    }
}
