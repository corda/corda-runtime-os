package net.corda.flow.pipeline.impl

import net.corda.data.flow.event.FlowEvent
import net.corda.flow.pipeline.FlowEventPipeline
import net.corda.flow.pipeline.FlowGlobalPostProcessor
import net.corda.flow.pipeline.events.FlowEventContext
import net.corda.flow.pipeline.exceptions.FlowFatalException
import net.corda.flow.pipeline.exceptions.FlowMarkedForKillException
import net.corda.flow.pipeline.exceptions.FlowTransientException
import net.corda.flow.pipeline.handlers.events.FlowEventHandler
import net.corda.tracing.TraceTag
import net.corda.utilities.trace
import net.corda.virtualnode.OperationalStatus
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import net.corda.virtualnode.toCorda
import org.slf4j.LoggerFactory

/**
 * [FlowEventPipelineImpl] encapsulates the pipeline steps that are executed when a [FlowEvent] is received by a [FlowEventProcessor].
 *
 * @param flowEventHandlers Map of available [FlowEventHandler]s where one is used for processing incoming events.
 * @param flowExecutionPipelineStage Stage of the pipeline responsible for executing user code and updating state after.
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
        private val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    override fun eventPreProcessing(): FlowEventPipelineImpl {
        log.trace { "Preprocessing of ${context.inputEventPayload::class.qualifiedName}" }

        val handler = getFlowEventHandler(context.inputEvent)

        context = handler.preProcess(context)

        // For now, we do this here as we need to be sure the flow start context exists, as for a
        // start flow event it won't exist until we have run the preProcess() for the start flow
        // event handler
        context.flowMetrics.flowEventReceived(context.inputEventPayload::class.java.name)

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

    override fun executeFlow(timeout: Long): FlowEventPipeline {
        context = flowExecutionPipelineStage.runFlow(context, timeout) {
            // Ensure the most up-to-date version of the context is visible in case an error occurs.
            context = it
        }
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
