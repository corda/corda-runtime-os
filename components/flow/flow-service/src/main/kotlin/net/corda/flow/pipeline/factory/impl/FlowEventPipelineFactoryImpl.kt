package net.corda.flow.pipeline.factory.impl

import net.corda.data.flow.event.FlowEvent
import net.corda.data.flow.state.checkpoint.Checkpoint
import net.corda.flow.fiber.FlowIORequest
import net.corda.flow.fiber.cache.FlowFiberCache
import net.corda.flow.metrics.FlowIORequestTypeConverter
import net.corda.flow.metrics.FlowMetricsFactory
import net.corda.flow.pipeline.FlowEventPipeline
import net.corda.flow.pipeline.FlowGlobalPostProcessor
import net.corda.flow.pipeline.events.FlowEventContext
import net.corda.flow.pipeline.factory.FlowEventPipelineFactory
import net.corda.flow.pipeline.handlers.events.FlowEventHandler
import net.corda.flow.pipeline.handlers.requests.FlowRequestHandler
import net.corda.flow.pipeline.handlers.waiting.FlowWaitingForHandler
import net.corda.flow.pipeline.impl.FlowEventPipelineImpl
import net.corda.flow.pipeline.impl.FlowExecutionPipelineStage
import net.corda.flow.pipeline.runner.FlowRunner
import net.corda.flow.state.impl.FlowCheckpointFactory
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.helper.getConfig
import net.corda.messaging.api.processor.StateAndEventProcessor.State
import net.corda.schema.configuration.ConfigKeys.FLOW_CONFIG
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
    private val flowFiberCache: FlowFiberCache,
    private val flowMetricsFactory: FlowMetricsFactory,
    private val flowIORequestTypeConverter: FlowIORequestTypeConverter,
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
        @Reference(service = FlowFiberCache::class)
        flowFiberCache: FlowFiberCache,
        @Reference(service = FlowMetricsFactory::class)
        flowMetricsFactory: FlowMetricsFactory,
        @Reference(service = FlowIORequestTypeConverter::class)
        flowIORequestTypeConverter: FlowIORequestTypeConverter
    ) : this(
        flowRunner,
        flowGlobalPostProcessor,
        flowCheckpointFactory,
        virtualNodeInfoReadService,
        flowFiberCache,
        flowMetricsFactory,
        flowIORequestTypeConverter,
        mutableListOf(),
        mutableListOf(),
        mutableListOf()
    )

    override fun create(
        state: State<Checkpoint>?,
        event: FlowEvent,
        configs: Map<String, SmartConfig>,
        mdcProperties: Map<String, String>,
        eventRecordTimestamp: Long,
        inputEventHash: String?
    ): FlowEventPipeline {
        val flowCheckpoint = flowCheckpointFactory.create(
            event.flowId,
            state?.value,
            configs.getConfig(FLOW_CONFIG)
        )

        val metrics = flowMetricsFactory.create(eventRecordTimestamp, flowCheckpoint)

        val context = FlowEventContext<Any>(
            checkpoint = flowCheckpoint,
            inputEvent = event,
            inputEventPayload = event.payload,
            configs = configs,
            flowConfig = configs.getConfig(FLOW_CONFIG),
            outputRecords = emptyList(),
            mdcProperties = mdcProperties,
            flowMetrics = metrics,
            metadata = state?.metadata,
            inputEventHash = inputEventHash
        )

        val flowExecutionPipelineStage = FlowExecutionPipelineStage(
            flowWaitingForHandlerMap,
            flowRequestHandlerMap,
            flowRunner,
            flowFiberCache,
            flowIORequestTypeConverter
        )

        return FlowEventPipelineImpl(
            flowEventHandlerMap,
            flowExecutionPipelineStage,
            flowGlobalPostProcessor,
            context,
            virtualNodeInfoReadService
        )
    }
}
