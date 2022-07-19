package net.corda.flow.pipeline.runner.impl

import net.corda.data.flow.event.SessionEvent
import net.corda.data.flow.event.StartFlow
import net.corda.data.flow.event.session.SessionInit
import net.corda.data.flow.state.checkpoint.FlowStackItem
import net.corda.flow.fiber.FiberFuture
import net.corda.flow.fiber.FlowContinuation
import net.corda.flow.fiber.FlowIORequest
import net.corda.flow.fiber.FlowLogicAndArgs
import net.corda.flow.fiber.factory.FlowFiberFactory
import net.corda.flow.pipeline.FlowEventContext
import net.corda.flow.pipeline.factory.FlowFactory
import net.corda.flow.pipeline.factory.FlowFiberExecutionContextFactory
import net.corda.flow.pipeline.runner.FlowRunner
import net.corda.sandboxgroupcontext.SandboxGroupContext
import net.corda.v5.base.util.contextLogger
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import java.util.concurrent.Future

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
                if (receivedEvent.payload is SessionInit) {
                    startInitiatedFlow(context)
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
            updateFlowStackItem = { }
        )
    }

    private fun startInitiatedFlow(
        context: FlowEventContext<Any>
    ): FiberFuture {
        val flowStartContext = context.checkpoint.flowStartContext
        return startFlow(
            context,
            createFlow = { sgc -> flowFactory.createInitiatedFlow(flowStartContext, sgc) },
            updateFlowStackItem = { fsi -> fsi.sessionIds.add(flowStartContext.statusKey.id) }
        )
    }

    private fun startFlow(
        context: FlowEventContext<Any>,
        createFlow: (SandboxGroupContext) -> FlowLogicAndArgs,
        updateFlowStackItem: (FlowStackItem) -> Unit
    ): FiberFuture {
        val checkpoint = context.checkpoint
        val fiberContext = flowFiberExecutionContextFactory.createFiberExecutionContext(context)
        val flow = createFlow(fiberContext.sandboxGroupContext)
        val stackItem = fiberContext.flowStackService.push(flow.logic)
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
