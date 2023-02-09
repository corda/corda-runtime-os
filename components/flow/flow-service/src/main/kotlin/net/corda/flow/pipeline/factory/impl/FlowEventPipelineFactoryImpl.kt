package net.corda.flow.pipeline.factory.impl

import net.corda.data.flow.event.FlowEvent
import net.corda.data.flow.state.checkpoint.Checkpoint
import net.corda.flow.fiber.FlowIORequest
import net.corda.flow.pipeline.FlowEventContext
import net.corda.flow.pipeline.FlowEventPipeline
import net.corda.flow.pipeline.FlowGlobalPostProcessor
import net.corda.flow.pipeline.factory.FlowEventPipelineFactory
import net.corda.flow.pipeline.handlers.events.FlowEventHandler
import net.corda.flow.pipeline.handlers.requests.FlowRequestHandler
import net.corda.flow.pipeline.handlers.waiting.FlowWaitingForHandler
import net.corda.flow.pipeline.impl.FlowEventPipelineImpl
import net.corda.flow.pipeline.runner.FlowRunner
import net.corda.flow.state.impl.FlowCheckpointFactory
import net.corda.libs.configuration.SmartConfig
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.osgi.service.component.annotations.ReferenceCardinality.MULTIPLE
import org.osgi.service.component.annotations.ReferencePolicy.DYNAMIC

@Suppress("CanBePrimaryConstructorProperty", "LongParameterList")
@Component(service = [FlowEventPipelineFactory::class])
class FlowEventPipelineFactoryImpl(
    private val flowRunner: FlowRunner,
    private val flowGlobalPostProcessor: FlowGlobalPostProcessor,
    private val flowCheckpointFactory: FlowCheckpointFactory,
    private val virtualNodeInfoReadService: VirtualNodeInfoReadService,
    flowEventHandlers: List<FlowEventHandler<out Any>>,
    flowWaitingForHandlers: List<FlowWaitingForHandler<out Any>>,
    flowRequestHandlers: List<FlowRequestHandler<out FlowIORequest<*>>>
) : FlowEventPipelineFactory {

    // We cannot use constructor injection with DYNAMIC policy.
    @Reference(service = FlowEventHandler::class, cardinality = MULTIPLE, policy = DYNAMIC)
    private val flowEventHandlers: List<FlowEventHandler<out Any>> = flowEventHandlers

    // We cannot use constructor injection with DYNAMIC policy.
    @Reference(service = FlowWaitingForHandler::class, cardinality = MULTIPLE, policy = DYNAMIC)
    private val flowWaitingForHandlers: List<FlowWaitingForHandler<out Any>> = flowWaitingForHandlers

    // We cannot use constructor injection with DYNAMIC policy.
    @Reference(service = FlowRequestHandler::class, cardinality = MULTIPLE, policy = DYNAMIC)
    private val flowRequestHandlers: List<FlowRequestHandler<out FlowIORequest<*>>> = flowRequestHandlers

    private val flowEventHandlerMap: Map<Class<*>, FlowEventHandler<out Any>> by lazy {
        flowEventHandlers.associateBy(FlowEventHandler<*>::type)
    }

    private val flowWaitingForHandlerMap: Map<Class<*>, FlowWaitingForHandler<out Any>> by lazy {
        flowWaitingForHandlers.associateBy(FlowWaitingForHandler<*>::type)
    }

    private val flowRequestHandlerMap: Map<Class<out FlowIORequest<*>>, FlowRequestHandler<out FlowIORequest<*>>> by lazy {
        flowRequestHandlers.associateBy(FlowRequestHandler<*>::type)
    }

    @Activate
    constructor(
        @Reference(service = FlowRunner::class)
        flowRunner: FlowRunner,
        @Reference(service = FlowGlobalPostProcessor::class)
        flowGlobalPostProcessor: FlowGlobalPostProcessor,
        @Reference(service = FlowCheckpointFactory::class)
        flowCheckpointFactory: FlowCheckpointFactory,
        @Reference(service = VirtualNodeInfoReadService::class)
        virtualNodeInfoReadService: VirtualNodeInfoReadService,
    ) : this(
        flowRunner,
        flowGlobalPostProcessor,
        flowCheckpointFactory,
        virtualNodeInfoReadService,
        mutableListOf(),
        mutableListOf(),
        mutableListOf()
    )

    override fun create(
        checkpoint: Checkpoint?,
        event: FlowEvent,
        config: SmartConfig,
        mdcProperties: Map<String, String>)
    : FlowEventPipeline {
        val context = FlowEventContext<Any>(
            checkpoint = flowCheckpointFactory.create(event.flowId, checkpoint, config),
            inputEvent = event,
            inputEventPayload = event.payload,
            config = config,
            outputRecords = emptyList(),
            mdcProperties = mdcProperties
        )
        return FlowEventPipelineImpl(
            flowEventHandlerMap,
            flowWaitingForHandlerMap,
            flowRequestHandlerMap,
            flowRunner,
            flowGlobalPostProcessor,
            context,
            virtualNodeInfoReadService
        )
    }
}
