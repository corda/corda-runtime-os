package net.corda.flow.pipeline.runner.impl

import net.corda.data.KeyValuePairList
import net.corda.data.flow.event.SessionEvent
import net.corda.data.flow.event.StartFlow
import net.corda.data.flow.event.session.SessionInit
import net.corda.data.flow.state.checkpoint.FlowStackItem
import net.corda.data.flow.state.checkpoint.FlowStackItemSession
import net.corda.flow.fiber.FiberFuture
import net.corda.flow.fiber.FlowContinuation
import net.corda.flow.fiber.FlowLogicAndArgs
import net.corda.flow.fiber.factory.FlowFiberFactory
import net.corda.flow.pipeline.events.FlowEventContext
import net.corda.flow.pipeline.factory.FlowFactory
import net.corda.flow.pipeline.factory.FlowFiberExecutionContextFactory
import net.corda.flow.pipeline.runner.FlowRunner
import net.corda.flow.utils.emptyKeyValuePairList
import net.corda.sandboxgroupcontext.SandboxGroupContext
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
            contextUserProperties = emptyKeyValuePairList(),
            contextPlatformProperties = startFlowEvent.startContext.contextPlatformProperties
        )
    }

    private fun startInitiatedFlow(
        context: FlowEventContext<Any>,
        sessionInitEvent: SessionInit
    ): FiberFuture {
        val flowStartContext = context.checkpoint.flowStartContext

        val localContext = remoteToLocalContextMapper(
            remoteUserContextProperties = sessionInitEvent.contextUserProperties,
            remotePlatformContextProperties = sessionInitEvent.contextPlatformProperties
        )

        return startFlow(
            context,
            createFlow = { sgc ->
                flowFactory.createInitiatedFlow(
                    flowStartContext,
                    sgc,
                    localContext.counterpartySessionProperties
                )
            },
            updateFlowStackItem = { fsi -> fsi.sessions.add(FlowStackItemSession(flowStartContext.statusKey.id, true)) },
            contextUserProperties = localContext.userProperties,
            contextPlatformProperties = localContext.platformProperties
        )
    }

    private fun startFlow(
        context: FlowEventContext<Any>,
        createFlow: (SandboxGroupContext) -> FlowLogicAndArgs,
        updateFlowStackItem: (FlowStackItem) -> Unit,
        contextUserProperties: KeyValuePairList,
        contextPlatformProperties: KeyValuePairList
    ): FiberFuture {
        val checkpoint = context.checkpoint
        val fiberContext = flowFiberExecutionContextFactory.createFiberExecutionContext(context)
        val flow = createFlow(fiberContext.sandboxGroupContext)
        val stackItem = fiberContext.flowStackService.pushWithContext(
            flow = flow.logic,
            contextUserProperties = contextUserProperties,
            contextPlatformProperties = contextPlatformProperties,
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
