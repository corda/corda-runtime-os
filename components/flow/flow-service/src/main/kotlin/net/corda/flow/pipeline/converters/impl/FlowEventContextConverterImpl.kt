package net.corda.flow.pipeline.converters.impl

import net.corda.data.flow.state.Checkpoint
import net.corda.flow.pipeline.FlowEventContext
import net.corda.flow.pipeline.converters.FlowEventContextConverter
import net.corda.messaging.api.processor.StateAndEventProcessor
import org.osgi.service.component.annotations.Component

@Suppress("Unused")
@Component(service = [FlowEventContextConverter::class])
class FlowEventContextConverterImpl : FlowEventContextConverter {
    override fun convert(flowContext: FlowEventContext<Any>): StateAndEventProcessor.Response<Checkpoint> {
        return StateAndEventProcessor.Response(
            flowContext.checkpoint.toAvro(),
            flowContext.outputRecords,
            flowContext.sendToDlq
        )
    }
}