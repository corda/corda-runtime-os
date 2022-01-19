package net.corda.flow.manager.impl.factory

import net.corda.flow.manager.FlowEventProcessor
import net.corda.flow.manager.factory.FlowEventProcessorFactory
import net.corda.flow.manager.impl.FlowEventProcessorImpl
import net.corda.flow.manager.impl.pipeline.factory.FlowEventPipelineFactory
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Component(service = [FlowEventProcessorFactory::class])
@Suppress("Unused")
class FlowEventProcessorFactoryImpl @Activate constructor(
    @Reference(service = FlowEventPipelineFactory::class)
    private val flowEventPipelineFactory: FlowEventPipelineFactory
) : FlowEventProcessorFactory {

    override fun create(): FlowEventProcessor {
        return FlowEventProcessorImpl(flowEventPipelineFactory)
    }
}

