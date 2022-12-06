package net.corda.flow.pipeline.factory.impl

import net.corda.data.flow.event.FlowEvent
import net.corda.data.flow.state.checkpoint.Checkpoint
import net.corda.flow.pipeline.FlowEventExceptionProcessor
import net.corda.flow.pipeline.FlowMDCService
import net.corda.flow.pipeline.converters.FlowEventContextConverter
import net.corda.flow.pipeline.factory.FlowEventPipelineFactory
import net.corda.flow.pipeline.factory.FlowEventProcessorFactory
import net.corda.flow.pipeline.impl.FlowEventProcessorImpl
import net.corda.libs.configuration.SmartConfig
import net.corda.messaging.api.processor.StateAndEventProcessor
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Component(service = [FlowEventProcessorFactory::class])
@Suppress("Unused")
class FlowEventProcessorFactoryImpl @Activate constructor(
    @Reference(service = FlowEventPipelineFactory::class)
    private val flowEventPipelineFactory: FlowEventPipelineFactory,
    @Reference(service = FlowEventExceptionProcessor::class)
    private val flowEventExceptionProcessor: FlowEventExceptionProcessor,
    @Reference(service = FlowEventContextConverter::class)
    private val flowEventContextConverter: FlowEventContextConverter,
    @Reference(service = FlowMDCService::class)
    private val flowMDCService: FlowMDCService
) : FlowEventProcessorFactory {

    override fun create(config: SmartConfig): StateAndEventProcessor<String, Checkpoint, FlowEvent> {
        return FlowEventProcessorImpl(
            flowEventPipelineFactory,
            flowEventExceptionProcessor,
            flowEventContextConverter,
            config,
            flowMDCService
        )
    }
}

