package net.corda.flow.manager.impl.factory

import net.corda.flow.manager.FlowEventProcessor
import net.corda.flow.manager.factory.FlowEventProcessorFactory
import net.corda.flow.manager.impl.FlowEventPipelineImpl
import net.corda.flow.manager.impl.FlowEventProcessorImpl
import net.corda.flow.manager.impl.handlers.events.FlowEventHandler
import net.corda.flow.manager.impl.handlers.requests.FlowRequestHandler
import net.corda.flow.manager.impl.runner.FlowRunner
import net.corda.flow.statemachine.requests.FlowIORequest
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.osgi.service.component.annotations.ReferenceCardinality
import org.osgi.service.component.annotations.ReferencePolicy

@Component(service = [FlowEventProcessorFactory::class])
class FlowEventProcessorFactoryImpl(
    private val flowRunner: FlowRunner,
    flowEventHandlers: List<FlowEventHandler<Any>>,
    flowRequestHandlers: List<FlowRequestHandler<FlowIORequest<*>>>
) : FlowEventProcessorFactory {

    @Reference(service = FlowEventHandler::class, cardinality = ReferenceCardinality.MULTIPLE, policy = ReferencePolicy.DYNAMIC)
    private val flowEventHandlers: List<FlowEventHandler<Any>> = flowEventHandlers

    @Reference(service = FlowRequestHandler::class, cardinality = ReferenceCardinality.MULTIPLE, policy = ReferencePolicy.DYNAMIC)
    private val flowRequestHandlers: List<FlowRequestHandler<FlowIORequest<*>>> = flowRequestHandlers

    @Activate
    constructor(
        @Reference(service = FlowRunner::class)
        flowRunner: FlowRunner
    ) : this(flowRunner, mutableListOf(), mutableListOf())

    override fun create(): FlowEventProcessor {
        return FlowEventProcessorImpl(FlowEventPipelineImpl(flowRunner, flowEventHandlers, flowRequestHandlers))
    }
}