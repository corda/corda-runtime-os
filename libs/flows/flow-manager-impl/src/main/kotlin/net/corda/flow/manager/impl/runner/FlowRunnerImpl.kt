package net.corda.flow.manager.impl.runner

import co.paralleluniverse.concurrent.util.ScheduledSingleThreadExecutor
import co.paralleluniverse.fibers.FiberExecutorScheduler
import net.corda.data.flow.event.StartRPCFlow
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
import net.corda.packaging.CPI
import net.corda.sandboxgroupcontext.SandboxGroupContext
import net.corda.serialization.CheckpointSerializer
import net.corda.v5.base.util.contextLogger
import net.corda.virtualnode.HoldingIdentity
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
    private val flowFactory: FlowFactory
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
            is StartRPCFlow -> startFlow(context, payload)
            else -> resumeFlow(context, flowContinuation)
        }
    }

    private fun startFlow(
        context: FlowEventContext<Any>,
        startFlowEvent: StartRPCFlow
    ): Future<FlowIORequest<*>> {
        val checkpoint = context.checkpoint!!

        log.info(
            "start new flow clientId: ${checkpoint.flowState.clientId} " +
                    "flowClassName: ${startFlowEvent.flowClassName} args ${startFlowEvent.jsonArgs}"
        )

        val sandbox = getSandbox(checkpoint)
        val fiberContext = createFiberExecutionContext(sandbox,checkpoint)
        val flow = flowFactory.createFlow(startFlowEvent, sandbox)
        val flowFiber = flowFiberFactory.createFlowFiber(checkpoint.flowKey, flow, scheduler)

        fiberContext.sandboxDependencyInjector.injectServices(flow)

        return  flowFiber.startFlow(fiberContext)
    }

    private fun createFiberExecutionContext(
        sandboxGroupContext: SandboxGroupContext,
        checkpoint: Checkpoint
    ): FlowFiberExecutionContext {
        return FlowFiberExecutionContext(
            sandboxGroupContext.getDependencyInjector(),
            flowStackServiceFactory.create(checkpoint),
            sandboxGroupContext.getCheckpointSerializer(),
            sandboxGroupContext
        )
    }

    private fun resumeFlow(
        context: FlowEventContext<Any>,
        flowContinuation: FlowContinuation
    ): Future<FlowIORequest<*>> {
        val checkpoint = context.checkpoint!!
        val sandbox = getSandbox(checkpoint)
        val fiberContext = createFiberExecutionContext(sandbox,checkpoint)

        val flowFiber = fiberContext.checkpointSerializer.deserialize(
            checkpoint.fiber.array(),
            FlowFiberImpl::class.java
        )

        return flowFiber.resume(fiberContext, flowContinuation, scheduler)
    }

    private fun getSandbox(checkpoint: Checkpoint): SandboxGroupContext {
        return flowSandboxService.get(
            HoldingIdentity(checkpoint.flowKey.identity.x500Name, checkpoint.flowKey.identity.groupId),
            CPI.Identifier.newInstance(checkpoint.cpiId, "1", null)
        )
    }

    private fun SandboxGroupContext.getCheckpointSerializer(): CheckpointSerializer {
        return checkNotNull(get(
            FlowSandboxContextTypes.CHECKPOINT_SERIALIZER,
            CheckpointSerializer::class.java
        )){"Failed to find the CheckpointSerializer in the Sandbox. key='${FlowSandboxContextTypes.CHECKPOINT_SERIALIZER}'"}
    }

    private fun SandboxGroupContext.getDependencyInjector(): SandboxDependencyInjector {
        return checkNotNull(get(
            FlowSandboxContextTypes.DEPENDENCY_INJECTOR,
            SandboxDependencyInjector::class.java
        )){"Failed to find the SandboxDependencyInjector in the Sandbox. key='${FlowSandboxContextTypes.CHECKPOINT_SERIALIZER}'"}
    }
}