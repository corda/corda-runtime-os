package net.corda.flow.pipeline.factory.impl

import net.corda.flow.pipeline.FlowEventProcessor
import net.corda.flow.pipeline.impl.FlowEventProcessorImpl
import net.corda.flow.pipeline.factory.FlowEventPipelineFactory
import net.corda.flow.pipeline.factory.FlowEventProcessorFactory
import net.corda.libs.configuration.SmartConfig
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Component(service = [FlowEventProcessorFactory::class])
@Suppress("Unused")
class FlowEventProcessorFactoryImpl @Activate constructor(
    @Reference(service = FlowEventPipelineFactory::class)
    private val flowEventPipelineFactory: FlowEventPipelineFactory
) : FlowEventProcessorFactory {

    override fun create(config: SmartConfig): FlowEventProcessor {
        return FlowEventProcessorImpl(flowEventPipelineFactory, config)
    }
}

