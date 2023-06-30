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
import net.corda.flow.pipeline.runner.FlowRunner
import net.corda.flow.state.impl.FlowCheckpointFactory
import net.corda.libs.configuration.SmartConfig
import net.corda.tracing.TraceContext
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import org.osgi.service.component.ComponentContext
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.osgi.service.component.annotations.ReferenceCardinality.MULTIPLE
import org.osgi.service.component.annotations.ReferencePolicy.DYNAMIC

@Suppress("LongParameterList")
@Component(
    service = [FlowEventPipelineFactory::class],
    reference = [
        Reference(
            name = FlowEventPipelineFactoryImpl.FLOW_EVENT_HANDLER_NAME,
            service = FlowEventHandler::class,
            cardinality = MULTIPLE,
            policy = DYNAMIC
        ),
        Reference(
            name = FlowEventPipelineFactoryImpl.FLOW_WAITING_FOR_HANDLER_NAME,
            service = FlowWaitingForHandler::class,
            cardinality = MULTIPLE,
            policy = DYNAMIC
        ),
        Reference(
            name = FlowEventPipelineFactoryImpl.FLOW_REQUEST_HANDLER_NAME,
            service = FlowRequestHandler::class,
            cardinality = MULTIPLE,
            policy = DYNAMIC
        )
    ]
)
class FlowEventPipelineFactoryImpl @Activate constructor(
    @Reference(service = FlowRunner::class)
    private val flowRunner: FlowRunner,
    @Reference(service = FlowGlobalPostProcessor::class)
    private val flowGlobalPostProcessor: FlowGlobalPostProcessor,
    @Reference(service = FlowCheckpointFactory::class)
    private val flowCheckpointFactory: FlowCheckpointFactory,
    @Reference(service = VirtualNodeInfoReadService::class)
    private val virtualNodeInfoReadService: VirtualNodeInfoReadService,
    @Reference(service = FlowFiberCache::class)
    private val flowFiberCache: FlowFiberCache,
    @Reference(service = FlowMetricsFactory::class)
    private val flowMetricsFactory: FlowMetricsFactory,
    @Reference(service = FlowIORequestTypeConverter::class)
    private val flowIORequestTypeConverter: FlowIORequestTypeConverter,
    private val componentContext: ComponentContext
) : FlowEventPipelineFactory {
    companion object {
        const val FLOW_EVENT_HANDLER_NAME = "FlowEventHandler"
        const val FLOW_WAITING_FOR_HANDLER_NAME = "FlowWaitingForHandler"
        const val FLOW_REQUEST_HANDLER_NAME = "FlowRequestHandler"
    }

    private fun <T> getServices(name: String): List<T> {
        @Suppress("unchecked_cast")
        return (componentContext.locateServices(name) as? Array<T>)?.toList() ?: emptyList()
    }

    private fun getFlowEventHandlerMap(): Map<Class<*>, FlowEventHandler<out Any>> {
        return getServices<FlowEventHandler<out Any>>(FLOW_EVENT_HANDLER_NAME)
            .associateBy(FlowEventHandler<*>::type)
    }

    private fun getFlowWaitingForHandlerMap(): Map<Class<*>, FlowWaitingForHandler<out Any>> {
        return getServices<FlowWaitingForHandler<out Any>>(FLOW_WAITING_FOR_HANDLER_NAME)
            .associateBy(FlowWaitingForHandler<*>::type)
    }

    private fun getFlowRequestHandlerMap(): Map<Class<out FlowIORequest<*>>, FlowRequestHandler<out FlowIORequest<*>>> {
        return getServices<FlowRequestHandler<out FlowIORequest<*>>>(FLOW_REQUEST_HANDLER_NAME)
            .associateBy(FlowRequestHandler<*>::type)
    }

    override fun create(
        checkpoint: Checkpoint?,
        event: FlowEvent,
        config: SmartConfig,
        mdcProperties: Map<String, String>,
        traceContext:TraceContext,
        eventRecordTimestamp: Long
    ): FlowEventPipeline {
        val flowCheckpoint = flowCheckpointFactory.create(event.flowId, checkpoint, config)

        val metrics = flowMetricsFactory.create(eventRecordTimestamp, flowCheckpoint)

        val context = FlowEventContext<Any>(
            checkpoint = flowCheckpoint,
            inputEvent = event,
            inputEventPayload = event.payload,
            config = config,
            outputRecords = emptyList(),
            mdcProperties = mdcProperties,
            flowMetrics = metrics,
            flowTraceContext = traceContext
        )

        return FlowEventPipelineImpl(
            getFlowEventHandlerMap(),
            getFlowWaitingForHandlerMap(),
            getFlowRequestHandlerMap(),
            flowRunner,
            flowGlobalPostProcessor,
            context,
            virtualNodeInfoReadService,
            flowFiberCache,
            flowIORequestTypeConverter
        )
    }
}
