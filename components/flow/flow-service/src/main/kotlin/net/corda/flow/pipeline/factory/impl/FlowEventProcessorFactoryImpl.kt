package net.corda.flow.pipeline.factory.impl

import net.corda.data.flow.event.FlowEvent
import net.corda.data.flow.state.checkpoint.Checkpoint
import net.corda.flow.pipeline.FlowEventExceptionProcessor
import net.corda.flow.pipeline.FlowMDCService
import net.corda.flow.pipeline.converters.FlowEventContextConverter
import net.corda.flow.pipeline.factory.FlowEventPipelineFactory
import net.corda.flow.pipeline.factory.FlowEventProcessorFactory
import net.corda.flow.pipeline.handlers.FlowPostProcessingHandler
import net.corda.flow.pipeline.impl.FlowEventProcessorImpl
import net.corda.libs.configuration.SmartConfig
import net.corda.messaging.api.processor.StateAndEventProcessor
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.osgi.service.component.annotations.ReferenceCardinality
import org.osgi.service.component.annotations.ReferencePolicy

@Component(service = [FlowEventProcessorFactory::class])
@Suppress("Unused")
class FlowEventProcessorFactoryImpl (
    private val flowEventPipelineFactory: FlowEventPipelineFactory,
    private val flowEventExceptionProcessor: FlowEventExceptionProcessor,
    private val flowEventContextConverter: FlowEventContextConverter,
    private val flowMDCService: FlowMDCService,
    postProcessingHandlers: List<FlowPostProcessingHandler>
) : FlowEventProcessorFactory {

    // We cannot use constructor injection with DYNAMIC policy.
    @Reference(
        service = FlowPostProcessingHandler::class,
        cardinality = ReferenceCardinality.MULTIPLE,
        policy = ReferencePolicy.DYNAMIC
    )
    private val postProcessingHandlers: List<FlowPostProcessingHandler> = postProcessingHandlers

    @Activate constructor(
        @Reference(service = FlowEventPipelineFactory::class)
        flowEventPipelineFactory: FlowEventPipelineFactory,
        @Reference(service = FlowEventExceptionProcessor::class)
        flowEventExceptionProcessor: FlowEventExceptionProcessor,
        @Reference(service = FlowEventContextConverter::class)
        flowEventContextConverter: FlowEventContextConverter,
        @Reference(service = FlowMDCService::class)
        flowMDCService: FlowMDCService
    ): this(
        flowEventPipelineFactory,
        flowEventExceptionProcessor,
        flowEventContextConverter,
        flowMDCService,
        mutableListOf()
    )

    override fun create(configs: Map<String, SmartConfig>): StateAndEventProcessor<String, Checkpoint, FlowEvent> {
        return FlowEventProcessorImpl(
            flowEventPipelineFactory,
            flowEventExceptionProcessor,
            flowEventContextConverter,
            configs,
            flowMDCService,
            postProcessingHandlers
        )
    }
}

