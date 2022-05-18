package net.corda.flow.pipeline.runner

import co.paralleluniverse.concurrent.util.ScheduledSingleThreadExecutor
import co.paralleluniverse.fibers.FiberExecutorScheduler
import net.corda.data.flow.event.SessionEvent
import net.corda.data.flow.event.StartFlow
import net.corda.data.flow.event.session.SessionInit
import net.corda.flow.application.sessions.factory.FlowSessionFactory
import net.corda.flow.fiber.FlowContinuation
import net.corda.flow.fiber.FlowFiberExecutionContext
import net.corda.flow.fiber.FlowIORequest
import net.corda.flow.fiber.factory.FlowFiberFactory
import net.corda.flow.pipeline.FlowEventContext
import net.corda.flow.pipeline.factory.FlowFactory
import net.corda.flow.pipeline.sandbox.FlowSandboxGroupContext
import net.corda.flow.pipeline.sandbox.FlowSandboxService
import net.corda.flow.state.FlowCheckpoint
import net.corda.membership.read.MembershipGroupReaderProvider
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.base.util.contextLogger
import net.corda.virtualnode.toCorda
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Deactivate
import org.osgi.service.component.annotations.Reference
import java.util.concurrent.ExecutorService
import java.util.concurrent.Future

@Suppress("unused", "LongParameterList")
@Component(service = [FlowRunner::class])
class FlowRunnerImpl @Activate constructor(
    @Reference(service = FlowFiberFactory::class)
    private val flowFiberFactory: FlowFiberFactory,
    @Reference(service = FlowSandboxService::class)
    private val flowSandboxService: FlowSandboxService,
    @Reference(service = FlowFactory::class)
    private val flowFactory: FlowFactory,
    // Don't think the flow session factory should live here, try refactor out later
    @Reference(service = FlowSessionFactory::class)
    private val flowSessionFactory: FlowSessionFactory,
    @Reference(service = MembershipGroupReaderProvider::class)
    private val membershipGroupReaderProvider: MembershipGroupReaderProvider
) : FlowRunner {
    private companion object {
        val log = contextLogger()
    }

    private val scheduler = FiberExecutorScheduler("Same thread scheduler", ScheduledSingleThreadExecutor())

    @Deactivate
    fun shutdown() {
        scheduler.shutdown()
        (scheduler.executor as? ExecutorService)?.shutdownNow()
    }

    override fun runFlow(
        context: FlowEventContext<Any>,
        flowContinuation: FlowContinuation
    ): Future<FlowIORequest<*>> {
        return when (val payload = context.inputEvent.payload) {
            is StartFlow -> startFlow(context, payload)
            is SessionEvent -> {
                val sessionInit = payload.payload
                if (sessionInit is SessionInit) {
                    startInitiatedFlow(context, sessionInit, payload)
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
        val checkpoint = context.checkpoint

        log.info(
            "start new flow Request ID: ${checkpoint.flowKey.id} " +
                    "flowClassName: ${startFlowEvent.startContext.flowClassName} args ${startFlowEvent.flowStartArgs}"
        )

        val sandbox = flowSandboxService.get(checkpoint.holdingIdentity.toCorda())
        val fiberContext = createFiberExecutionContext(sandbox, checkpoint)
        val flow = flowFactory.createFlow(startFlowEvent, sandbox)
        val flowFiber = flowFiberFactory.createFlowFiber(checkpoint.flowId, flow, scheduler)

        fiberContext.flowStackService.push(flowFiber.flowLogic)
        fiberContext.sandboxDependencyInjector.injectServices(flow)

        return flowFiber.startFlow(fiberContext)
    }

    private fun startInitiatedFlow(
        context: FlowEventContext<Any>,
        sessionInit: SessionInit,
        sessionEvent: SessionEvent
    ): Future<FlowIORequest<*>> {
        val checkpoint = context.checkpoint

        log.info("start new initiated flow for protocol name: ${sessionInit.protocol}")

        val sandbox = flowSandboxService.get(checkpoint.holdingIdentity.toCorda())
        val fiberContext = createFiberExecutionContext(sandbox, checkpoint)

        val initiatedFlowClassName =
            flowSandboxService.get(sessionEvent.initiatedIdentity.toCorda()).protocolStore.responderForProtocol(
                sessionInit.protocol, sessionInit.versions
            )

        val flowSession = flowSessionFactory.create(
            sessionEvent.sessionId,
            MemberX500Name.parse(sessionEvent.initiatingIdentity.x500Name),
            initiated = true
        )

        val flow = flowFactory.createInitiatedFlow(initiatedFlowClassName, flowSession, sandbox)

        val flowFiber = flowFiberFactory.createFlowFiber(checkpoint.flowId, flow, scheduler)

        val stackItem = fiberContext.flowStackService.push(flowFiber.flowLogic)
        stackItem.sessionIds.add(sessionEvent.sessionId)

        fiberContext.sandboxDependencyInjector.injectServices(flow)

        return flowFiber.startFlow(fiberContext)
    }

    private fun createFiberExecutionContext(
        sandboxGroupContext: FlowSandboxGroupContext,
        checkpoint: FlowCheckpoint
    ): FlowFiberExecutionContext {
        return FlowFiberExecutionContext(
            sandboxGroupContext.dependencyInjector,
            checkpoint,
            sandboxGroupContext.checkpointSerializer,
            sandboxGroupContext,
            checkpoint.holdingIdentity,
            membershipGroupReaderProvider.getGroupReader(checkpoint.holdingIdentity.toCorda())
        )
    }

    private fun resumeFlow(
        context: FlowEventContext<Any>,
        flowContinuation: FlowContinuation
    ): Future<FlowIORequest<*>> {
        val checkpoint = context.checkpoint
        val sandbox = flowSandboxService.get(checkpoint.holdingIdentity.toCorda())
        val fiberContext = createFiberExecutionContext(sandbox, checkpoint)
        val flowFiber = flowFiberFactory.createFlowFiber(fiberContext)

        return flowFiber.resume(fiberContext, flowContinuation, scheduler)
    }
}