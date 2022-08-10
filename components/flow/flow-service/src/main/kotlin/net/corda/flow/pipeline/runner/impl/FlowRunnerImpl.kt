package net.corda.flow.pipeline.runner.impl

import net.corda.data.KeyValuePairList
import net.corda.data.flow.event.SessionEvent
import net.corda.data.flow.event.StartFlow
import net.corda.data.flow.event.session.SessionInit
import net.corda.data.flow.state.checkpoint.FlowStackItem
import net.corda.flow.fiber.FiberFuture
import net.corda.flow.fiber.FlowContinuation
import net.corda.flow.fiber.FlowLogicAndArgs
import net.corda.flow.fiber.factory.FlowFiberFactory
import net.corda.flow.pipeline.FlowEventContext
import net.corda.flow.pipeline.factory.FlowFactory
import net.corda.flow.pipeline.factory.FlowFiberExecutionContextFactory
import net.corda.flow.pipeline.runner.FlowRunner
import net.corda.flow.utils.emptyKeyValuePairList
import net.corda.sandboxgroupcontext.SandboxGroupContext
import net.corda.v5.base.util.contextLogger
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Component(service = [FlowRunner::class])
class FlowRunnerImpl @Activate constructor(
    @Reference(service = FlowFiberFactory::class)
    private val flowFiberFactory: FlowFiberFactory,
    @Reference(service = FlowFactory::class)
    private val flowFactory: FlowFactory,
    @Reference(service = FlowFiberExecutionContextFactory::class)
    private val flowFiberExecutionContextFactory: FlowFiberExecutionContextFactory
) : FlowRunner {
    private companion object {
        val log = contextLogger()
    }

    override fun runFlow(
        context: FlowEventContext<Any>,
        flowContinuation: FlowContinuation
    ): FiberFuture {
        return when (val receivedEvent = context.inputEvent.payload) {
            is StartFlow -> startFlow(context, receivedEvent)
            is SessionEvent -> {
                val payload = receivedEvent.payload
                if (payload is SessionInit) {
                    startInitiatedFlow(context, payload)
                } else {
                    resumeFlow(context, flowContinuation)
                }
            }
            else -> resumeFlow(context, flowContinuation)
        }
    }

    private fun startFlow(
        context: FlowEventContext<Any>,
        startFlowEvent: StartFlow
    ): FiberFuture {
        return startFlow(
            context,
            createFlow = { sgc -> flowFactory.createFlow(startFlowEvent, sgc) },
            updateFlowStackItem = { },
            contextPlatformProperties = startFlowEvent.startContext.contextPlatformProperties,
            contextUserProperties = emptyKeyValuePairList()
        )
    }

    private fun startInitiatedFlow(
        context: FlowEventContext<Any>,
        sessionInitEvent: SessionInit
    ): FiberFuture {
        val flowStartContext = context.checkpoint.flowStartContext
        return startFlow(
            context,
            createFlow = { sgc -> flowFactory.createInitiatedFlow(flowStartContext, sgc) },
            updateFlowStackItem = { fsi -> fsi.sessionIds.add(flowStartContext.statusKey.id) },
            contextPlatformProperties = sessionInitEvent.contextPlatformProperties,
            contextUserProperties = sessionInitEvent.contextUserProperties
        )
    }

    private fun startFlow(
        context: FlowEventContext<Any>,
        createFlow: (SandboxGroupContext) -> FlowLogicAndArgs,
        updateFlowStackItem: (FlowStackItem) -> Unit,
        contextPlatformProperties: KeyValuePairList,
        contextUserProperties: KeyValuePairList,
    ): FiberFuture {
        val checkpoint = context.checkpoint
        val fiberContext = flowFiberExecutionContextFactory.createFiberExecutionContext(context)
        val flow = createFlow(fiberContext.sandboxGroupContext)
        val stackItem = fiberContext.flowStackService.pushWithContext(
            flow = flow.logic,
            contextPlatformProperties = contextPlatformProperties,
            contextUserProperties = contextUserProperties
        )
        updateFlowStackItem(stackItem)
        fiberContext.sandboxGroupContext.dependencyInjector.injectServices(flow.logic)
        return flowFiberFactory.createAndStartFlowFiber(fiberContext, checkpoint.flowId, flow)
    }

    private fun resumeFlow(
        context: FlowEventContext<Any>,
        flowContinuation: FlowContinuation
    ): FiberFuture {
        val fiberContext = flowFiberExecutionContextFactory.createFiberExecutionContext(context)
        return flowFiberFactory.createAndResumeFlowFiber(fiberContext, flowContinuation)
    }
}
