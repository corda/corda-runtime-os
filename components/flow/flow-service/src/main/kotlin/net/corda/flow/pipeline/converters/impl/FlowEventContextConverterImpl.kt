package net.corda.flow.pipeline.converters.impl

import net.corda.data.flow.state.checkpoint.Checkpoint
import net.corda.flow.pipeline.FlowEventContext
import net.corda.flow.pipeline.converters.FlowEventContextConverter
import net.corda.messaging.api.processor.StateAndEventProcessor
import org.osgi.service.component.annotations.Component

@Suppress("Unused")
@Component(service = [FlowEventContextConverter::class])
class FlowEventContextConverterImpl : FlowEventContextConverter {
    override fun convert(flowContext: FlowEventContext<*>): StateAndEventProcessor.Response<Checkpoint> {
        return StateAndEventProcessor.Response(
            flowContext.checkpoint.toAvro(),
            flowContext.outputRecords,
            processingStatus = when(flowContext.processingStatus) {
                FlowEventContext.ProcessingStatus.SUCCESS -> StateAndEventProcessor.Response.ProcessingStatus.SUCCESS
                FlowEventContext.ProcessingStatus.SEND_TO_DLQ -> StateAndEventProcessor.Response.ProcessingStatus.SEND_TO_DLQ
                FlowEventContext.ProcessingStatus.STRAY_EVENT -> StateAndEventProcessor.Response.ProcessingStatus.STRAY_EVENT
            }
        )
    }
}