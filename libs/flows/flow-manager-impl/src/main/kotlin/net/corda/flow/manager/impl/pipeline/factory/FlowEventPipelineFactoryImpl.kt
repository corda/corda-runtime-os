package net.corda.flow.manager.impl.pipeline.factory

import net.corda.data.flow.event.FlowEvent
import net.corda.data.flow.state.Checkpoint
import net.corda.flow.manager.fiber.FlowIORequest
import net.corda.flow.manager.impl.FlowEventContext
import net.corda.flow.manager.impl.handlers.FlowProcessingException
import net.corda.flow.manager.impl.handlers.events.FlowEventHandler
import net.corda.flow.manager.impl.handlers.requests.FlowRequestHandler
import net.corda.flow.manager.impl.pipeline.FlowEventPipeline
import net.corda.flow.manager.impl.pipeline.FlowEventPipelineImpl
import net.corda.flow.manager.impl.runner.FlowRunner
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.osgi.service.component.annotations.ReferenceCardinality
import org.osgi.service.component.annotations.ReferencePolicy

@Component(service = [FlowEventPipelineFactory::class])
class FlowEventPipelineFactoryImpl(
    private val flowRunner: FlowRunner,
    flowEventHandlers: List<FlowEventHandler<Any>>,
    flowRequestHandlers: List<FlowRequestHandler<out FlowIORequest<*>>>
) : FlowEventPipelineFactory {

    @Reference(service = FlowEventHandler::class, cardinality = ReferenceCardinality.MULTIPLE, policy = ReferencePolicy.DYNAMIC)
    private val flowEventHandlers: List<FlowEventHandler<Any>> = flowEventHandlers

    @Reference(service = FlowRequestHandler::class, cardinality = ReferenceCardinality.MULTIPLE, policy = ReferencePolicy.DYNAMIC)
    private val flowRequestHandlers: List<FlowRequestHandler<out FlowIORequest<*>>> = flowRequestHandlers

    private val flowEventHandlerMap: Map<Any, FlowEventHandler<Any>> by lazy {
        flowEventHandlers.associateBy { it.type }
    }

    private val flowRequestHandlerMap: Map<Class<out FlowIORequest<*>>, FlowRequestHandler<out FlowIORequest<*>>> by lazy {
        flowRequestHandlers.associateBy { it.type }
    }

    @Activate
    constructor(
        @Reference(service = FlowRunner::class)
        flowRunner: FlowRunner
    ) : this(flowRunner, mutableListOf(), mutableListOf())

    override fun create(checkpoint: Checkpoint?, event: FlowEvent): FlowEventPipeline {
        val context = FlowEventContext<Any>(
            checkpoint = checkpoint,
            inputEvent = event,
            inputEventPayload = event.payload,
            outputRecords = emptyList()
        )
        return FlowEventPipelineImpl(getFlowEventHandler(event), flowRequestHandlerMap, flowRunner, context)
    }

    private fun getFlowEventHandler(event: FlowEvent): FlowEventHandler<Any> {
        return flowEventHandlerMap[event.payload::class.java]
            ?: throw FlowProcessingException("${event.payload::class.java.name} does not have an associated flow event handler")
    }
}