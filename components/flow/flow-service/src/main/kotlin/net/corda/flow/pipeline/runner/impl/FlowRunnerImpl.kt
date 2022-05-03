package net.corda.flow.pipeline.runner.impl

import net.corda.data.flow.FlowStackItem
import net.corda.data.flow.event.SessionEvent
import net.corda.data.flow.event.StartFlow
import net.corda.data.flow.event.session.SessionInit
import net.corda.flow.fiber.FlowContinuation
import net.corda.flow.fiber.FlowIORequest
import net.corda.flow.fiber.factory.FlowFiberFactory
import net.corda.flow.pipeline.FlowEventContext
import net.corda.flow.pipeline.factory.FlowFactory
import net.corda.flow.pipeline.factory.FlowFiberExecutionContextFactory
import net.corda.flow.pipeline.runner.FlowRunner
import net.corda.sandboxgroupcontext.SandboxGroupContext
import net.corda.v5.application.flows.Flow
import net.corda.v5.base.util.contextLogger
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import java.util.concurrent.Future

@Suppress("unused", "LongParameterList")
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
    ): Future<FlowIORequest<*>> {
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
    ): Future<FlowIORequest<*>> {
        return startFlow(
            context,
            createFlow = { sgc -> flowFactory.createFlow(startFlowEvent, sgc) },
            updateFlowStackItem = { }
        )
    }

    private fun startInitiatedFlow(
        context: FlowEventContext<Any>
    ): Future<FlowIORequest<*>> {
        val flowStartContext = context.checkpoint.flowStartContext
        return startFlow(
            context,
            createFlow = { sgc -> flowFactory.createInitiatedFlow(flowStartContext, sgc) },
            updateFlowStackItem = { fsi -> fsi.sessionIds.add(flowStartContext.statusKey.id) }
        )
    }

    private fun startFlow(
        context: FlowEventContext<Any>,
        createFlow: (SandboxGroupContext) -> Flow<*>,
        updateFlowStackItem: (FlowStackItem) -> Unit
    ): Future<FlowIORequest<*>> {
        val checkpoint = context.checkpoint
        val fiberContext = flowFiberExecutionContextFactory.createFiberExecutionContext(context)
        val flow = createFlow(fiberContext.sandboxGroupContext)
        val flowFiber = flowFiberFactory.createFlowFiber(checkpoint.flowId, flow)
        val stackItem = fiberContext.flowStackService.push(flow)
        updateFlowStackItem(stackItem)
        fiberContext.sandboxDependencyInjector.injectServices(flow)

        return flowFiber.startFlow(fiberContext)
    }

    private fun resumeFlow(
        context: FlowEventContext<Any>,
        flowContinuation: FlowContinuation
    ): Future<FlowIORequest<*>> {
        val fiberContext = flowFiberExecutionContextFactory.createFiberExecutionContext(context)
        val flowFiber = flowFiberFactory.createFlowFiber(fiberContext)

        return flowFiber.resume(fiberContext, flowContinuation, flowFiberFactory.currentScheduler)
    }
}