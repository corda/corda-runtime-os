package net.corda.flow.manager.impl.runner

import co.paralleluniverse.concurrent.util.ScheduledSingleThreadExecutor
import co.paralleluniverse.fibers.FiberExecutorScheduler
import net.corda.application.internal.flow.session.FlowSessionFactory
import net.corda.data.flow.event.SessionEvent
import net.corda.data.flow.event.StartFlow
import net.corda.data.flow.event.session.SessionInit
import net.corda.data.flow.state.Checkpoint
import net.corda.flow.manager.FlowSandboxContextTypes
import net.corda.flow.manager.FlowSandboxService
import net.corda.flow.manager.SandboxDependencyInjector
import net.corda.flow.manager.factory.FlowFactory
import net.corda.flow.manager.factory.FlowFiberFactory
import net.corda.flow.manager.factory.FlowStackServiceFactory
import net.corda.flow.manager.fiber.FlowContinuation
import net.corda.flow.manager.fiber.FlowFiberExecutionContext
import net.corda.flow.manager.fiber.FlowIORequest
import net.corda.flow.manager.impl.FlowEventContext
import net.corda.flow.manager.impl.fiber.FlowFiberImpl
import net.corda.flow.manager.impl.handlers.FlowProcessingException
import net.corda.sandboxgroupcontext.SandboxGroupContext
import net.corda.sandboxgroupcontext.getObjectByKey
import net.corda.serialization.checkpoint.CheckpointSerializer
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.base.util.contextLogger
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.toCorda
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Deactivate
import org.osgi.service.component.annotations.Reference
import java.util.concurrent.ExecutorService
import java.util.concurrent.Future

@Suppress("unused")
@Component(service = [FlowRunner::class])
class FlowRunnerImpl @Activate constructor(
    @Reference(service = FlowFiberFactory::class)
    private val flowFiberFactory: FlowFiberFactory,
    @Reference(service = FlowSandboxService::class)
    private val flowSandboxService: FlowSandboxService,
    @Reference(service = FlowStackServiceFactory::class)
    private val flowStackServiceFactory: FlowStackServiceFactory,
    @Reference(service = FlowFactory::class)
    private val flowFactory: FlowFactory,
    // Don't think the flow session factory should live here, try refactor out later
    @Reference(service = FlowSessionFactory::class)
    private val flowSessionFactory: FlowSessionFactory
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
        val checkpoint = context.checkpoint!!

        log.info(
            "start new flow clientId: ${checkpoint.flowStartContext.requestId} " +
                    "flowClassName: ${startFlowEvent.startContext.flowClassName} args ${startFlowEvent.flowStartArgs}"
        )

        val sandbox = getSandbox(checkpoint)
        val fiberContext = createFiberExecutionContext(sandbox, checkpoint)
        val flow = flowFactory.createFlow(startFlowEvent, sandbox)
        val flowFiber = flowFiberFactory.createFlowFiber(checkpoint.flowKey, flow, scheduler)

        fiberContext.flowStackService.push(flowFiber.flowLogic)
        fiberContext.sandboxDependencyInjector.injectServices(flow)

        return flowFiber.startFlow(fiberContext)
    }

    private fun startInitiatedFlow(
        context: FlowEventContext<Any>,
        sessionInit: SessionInit,
        sessionEvent: SessionEvent
    ): Future<FlowIORequest<*>> {
        val checkpoint = context.checkpoint!!

        log.info("start new initiated flow flowClassName: ${sessionInit.flowName}")

        val sandbox = getSandbox(checkpoint)
        val fiberContext = createFiberExecutionContext(sandbox, checkpoint)

        val initiatedFlowClassName =
            getInitiatingToInitiatedFlowsFromSandbox(sessionInit.initiatedIdentity.toCorda())[sessionInit.cpiId to sessionInit.flowName]
                ?: throw FlowProcessingException("No initiated flow that can be started from ${sessionInit.flowName}")

        val flowSession = flowSessionFactory.create(
            sessionEvent.sessionId,
            MemberX500Name.parse(sessionInit.initiatingIdentity.x500Name),
            initiated = true
        )

        val flow = flowFactory.createInitiatedFlow(sandbox, initiatedFlowClassName, flowSession)

        val flowFiber = flowFiberFactory.createFlowFiber(checkpoint.flowKey, flow, scheduler)

        val stackItem = fiberContext.flowStackService.push(flowFiber.flowLogic)
        stackItem.sessionIds.add(sessionEvent.sessionId)

        fiberContext.sandboxDependencyInjector.injectServices(flow)

        return flowFiber.startFlow(fiberContext)
    }

    private fun createFiberExecutionContext(
        sandboxGroupContext: SandboxGroupContext,
        checkpoint: Checkpoint
    ): FlowFiberExecutionContext {
        return FlowFiberExecutionContext(
            sandboxGroupContext.getDependencyInjector(),
            flowStackServiceFactory.create(checkpoint),
            sandboxGroupContext.getCheckpointSerializer(),
            sandboxGroupContext,
            checkpoint.flowStartContext.identity
        )
    }

    private fun resumeFlow(
        context: FlowEventContext<Any>,
        flowContinuation: FlowContinuation
    ): Future<FlowIORequest<*>> {
        val checkpoint = context.checkpoint!!
        val sandbox = getSandbox(checkpoint)
        val fiberContext = createFiberExecutionContext(sandbox, checkpoint)

        val flowFiber = fiberContext.checkpointSerializer.deserialize(
            checkpoint.fiber.array(),
            FlowFiberImpl::class.java
        )

        return flowFiber.resume(fiberContext, flowContinuation, scheduler)
    }

    private fun getSandbox(checkpoint: Checkpoint): SandboxGroupContext {
        return flowSandboxService.get(HoldingIdentity(checkpoint.flowKey.identity.x500Name, checkpoint.flowKey.identity.groupId))
    }

    private fun SandboxGroupContext.getCheckpointSerializer(): CheckpointSerializer {
        return checkNotNull(get(FlowSandboxContextTypes.CHECKPOINT_SERIALIZER, CheckpointSerializer::class.java)) {
            "Failed to find the CheckpointSerializer in the Sandbox. key='${FlowSandboxContextTypes.CHECKPOINT_SERIALIZER}'"
        }
    }

    private fun SandboxGroupContext.getDependencyInjector(): SandboxDependencyInjector {
        return checkNotNull(get(FlowSandboxContextTypes.DEPENDENCY_INJECTOR, SandboxDependencyInjector::class.java)) {
            "Failed to find the SandboxDependencyInjector in the Sandbox. key='${FlowSandboxContextTypes.CHECKPOINT_SERIALIZER}'"
        }
    }

    private fun getInitiatingToInitiatedFlowsFromSandbox(holdingIdentity: HoldingIdentity): Map<Pair<String, String>, String> {
        return flowSandboxService.get(holdingIdentity).getObjectByKey(FlowSandboxContextTypes.INITIATING_TO_INITIATED_FLOWS)
            ?: throw FlowProcessingException("Sandbox for identity: $holdingIdentity has not been initialised correctly")
    }
}