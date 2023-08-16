package net.corda.flow.pipeline.impl

import net.corda.data.flow.event.FlowEvent
import net.corda.data.flow.event.Wakeup
import net.corda.flow.fiber.FlowContinuation
import net.corda.flow.fiber.FlowIORequest
import net.corda.flow.fiber.cache.FlowFiberCache
import net.corda.flow.metrics.FlowIORequestTypeConverter
import net.corda.flow.pipeline.FlowEventPipeline
import net.corda.flow.pipeline.FlowGlobalPostProcessor
import net.corda.flow.pipeline.events.FlowEventContext
import net.corda.flow.pipeline.exceptions.FlowFatalException
import net.corda.flow.pipeline.exceptions.FlowMarkedForKillException
import net.corda.flow.pipeline.exceptions.FlowTransientException
import net.corda.flow.pipeline.handlers.events.FlowEventHandler
import net.corda.flow.pipeline.handlers.requests.FlowRequestHandler
import net.corda.flow.pipeline.handlers.waiting.FlowWaitingForHandler
import net.corda.flow.pipeline.runner.FlowRunner
import net.corda.tracing.TraceTag
import net.corda.utilities.trace
import net.corda.virtualnode.OperationalStatus
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import net.corda.virtualnode.toCorda
import org.slf4j.LoggerFactory
import java.nio.ByteBuffer
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * [FlowEventPipelineImpl] encapsulates the pipeline steps that are executed when a [FlowEvent] is received by a [FlowEventProcessor].
 *
 * @param flowEventHandlers Map of available [FlowEventHandler]s where one is used for processing incoming events.
 * @param flowGlobalPostProcessor The [FlowGlobalPostProcessor] applied to all events .
 * @param context The [FlowEventContext] that should be modified by the pipeline steps.
 * @param virtualNodeInfoReadService The [VirtualNodeInfoReadService] is responsible for reading virtual node information.
 */
@Suppress("LongParameterList")
internal class FlowEventPipelineImpl(
    private val flowEventHandlers: Map<Class<*>, FlowEventHandler<out Any>>,
    private val flowExecutionPipelineStage: FlowExecutionPipelineStage,
    private val flowGlobalPostProcessor: FlowGlobalPostProcessor,
    override var context: FlowEventContext<Any>,
    private val virtualNodeInfoReadService: VirtualNodeInfoReadService
) : FlowEventPipeline {

    private companion object {
        val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    override fun eventPreProcessing(): FlowEventPipelineImpl {
        log.trace { "Preprocessing of ${context.inputEventPayload::class.qualifiedName}" }

        /**
         * If the checkpoint is in a retry step and we receive a Wakeup then we
         * should re-write the event the pipeline should process the event to be retried, in place of the default
         * wakeup behavior
         */
        val updatedContext = if (context.checkpoint.inRetryState && context.inputEventPayload is Wakeup) {
            log.debug(
                "Flow is in retry state, using retry event " +
                        "${context.checkpoint.retryEvent.payload::class.qualifiedName} for the pipeline processing."
            )
            context.copy(
                inputEvent = context.checkpoint.retryEvent,
                inputEventPayload = context.checkpoint.retryEvent.payload,
                isRetryEvent = true
            )
        } else {
            context
        }

        val handler = getFlowEventHandler(updatedContext.inputEvent)

        context = handler.preProcess(updatedContext)

        // For now, we do this here as we need to be sure the flow start context exists, as for a
        // start flow event it won't exist until we have run the preProcess() for the start flow
        // event handler
        context.flowMetrics.flowEventReceived(updatedContext.inputEventPayload::class.java.name)

        val checkpoint = context.checkpoint

        context.flowTraceContext.apply {
            val flowStartContext = checkpoint.flowStartContext
            traceTag(TraceTag.FLOW_ID, checkpoint.flowId)
            traceTag(TraceTag.FLOW_CLASS, flowStartContext.flowClassName)
            traceTag(TraceTag.FLOW_REQUEST_ID, flowStartContext.requestId)
            traceTag(TraceTag.FLOW_VNODE, checkpoint.holdingIdentity.shortHash.toString())
            traceTag(TraceTag.FLOW_INITIATOR, flowStartContext.initiatedBy.toCorda().shortHash.toString())
        }

        return this
    }

    override fun virtualNodeFlowOperationalChecks(): FlowEventPipeline {
        if (!context.checkpoint.doesExist) {
            log.warn("Could not perform flow operational validation as the checkpoint does not exist.")
            return this
        }
        val holdingIdentity = context.checkpoint.holdingIdentity
        val virtualNode = virtualNodeInfoReadService.get(holdingIdentity)
            ?: throw FlowTransientException(
                "Failed to find the virtual node info for holder " +
                        "'HoldingIdentity(x500Name=${holdingIdentity.x500Name}, groupId=${holdingIdentity.groupId})'"
            )

        if (virtualNode.flowOperationalStatus == OperationalStatus.INACTIVE) {
            throw FlowMarkedForKillException("Flow operational status is ${virtualNode.flowOperationalStatus.name}")
        }
        return this
    }

    override fun executeFlow(): FlowEventPipeline {
        context = flowExecutionPipelineStage.runFlow(context)
        return this
    }

    override fun globalPostProcessing(): FlowEventPipelineImpl {
        context = flowGlobalPostProcessor.postProcess(context)
        return this
    }

    private fun getFlowEventHandler(event: FlowEvent): FlowEventHandler<Any> {
        @Suppress("unchecked_cast")
        return flowEventHandlers[event.payload::class.java] as? FlowEventHandler<Any>
            ?: throw FlowFatalException("${event.payload::class.java.name} does not have an associated flow event handler")
    }
}
