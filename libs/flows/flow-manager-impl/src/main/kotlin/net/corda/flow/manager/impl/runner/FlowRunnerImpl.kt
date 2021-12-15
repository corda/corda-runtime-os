package net.corda.flow.manager.impl.runner

import co.paralleluniverse.concurrent.util.ScheduledSingleThreadExecutor
import co.paralleluniverse.fibers.Fiber
import co.paralleluniverse.fibers.FiberExecutorScheduler
import net.corda.data.flow.event.FlowEvent
import net.corda.data.flow.event.StartRPCFlow
import net.corda.data.flow.state.Checkpoint
import net.corda.dependency.injection.FlowDependencyInjector
import net.corda.flow.manager.FlowSandboxContextTypes
import net.corda.flow.manager.FlowSandboxService
import net.corda.flow.statemachine.FlowContinuation
import net.corda.flow.statemachine.FlowFiber
import net.corda.flow.statemachine.HousekeepingState
import net.corda.flow.statemachine.NonSerializableState
import net.corda.flow.statemachine.factory.FlowFiberFactory
import net.corda.flow.statemachine.impl.FlowFiberImpl
import net.corda.packaging.CPI
import net.corda.sandbox.SandboxGroup
import net.corda.serialization.CheckpointSerializer
import net.corda.v5.application.flows.Flow
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.uncheckedCast
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.sandboxgroup.SandboxGroupContext
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Component(service = [FlowRunner::class])
class FlowRunnerImpl @Activate constructor(
    @Reference(service = FlowFiberFactory::class)
    private val flowFiberFactory: FlowFiberFactory,
    @Reference(service = FlowSandboxService::class)
    private val flowSandboxService: FlowSandboxService
) : FlowRunner {

    private companion object {
        val log = contextLogger()
        private val scheduler = FiberExecutorScheduler("Same thread scheduler", ScheduledSingleThreadExecutor())
    }

    override fun runFlow(checkpoint: Checkpoint, inputEvent: FlowEvent, flowContinuation: FlowContinuation): FlowFiber<*> {
        return when (val payload = inputEvent.payload) {
            is StartRPCFlow -> startFlow(checkpoint, payload, flowContinuation)
            else -> resumeFlow(checkpoint, flowContinuation)
        }
    }

    private fun startFlow(checkpoint: Checkpoint, startFlowEvent: StartRPCFlow, flowContinuation: FlowContinuation): FlowFiber<*> {
        log.info("start new flow clientId: ${checkpoint.flowState.clientId} flowClassName: ${startFlowEvent.flowClassName} args ${startFlowEvent.jsonArgs}")
        val sandbox = getSandbox(checkpoint)
        val flow = getOrCreate(sandbox.sandboxGroup, startFlowEvent.flowClassName, startFlowEvent.jsonArgs)
        val flowFiber = flowFiberFactory.createFlowFiber(checkpoint.flowKey, flow, scheduler)
        return flowFiber.apply {
            setHousekeepingState(checkpoint, flowContinuation)
            setNonSerializableState(sandbox)
            injectDependencies(sandbox)
            startFlow()
        }
    }

    private fun resumeFlow(checkpoint: Checkpoint, flowContinuation: FlowContinuation): FlowFiber<*> {
        val sandbox = getSandbox(checkpoint)
        val flowFiber = sandbox.getCheckpointSerializer().deserialize(checkpoint.fiber.array(), FlowFiberImpl::class.java)
        flowFiber.apply {
            setHousekeepingState(checkpoint, flowContinuation)
            setNonSerializableState(sandbox)
        }
        Fiber.unparkDeserialized(flowFiber, scheduler)
        return flowFiber
    }

    private fun getSandbox(checkpoint: Checkpoint): SandboxGroupContext {
        return flowSandboxService.get(
            HoldingIdentity(checkpoint.flowKey.identity.x500Name, checkpoint.flowKey.identity.groupId),
            CPI.Identifier.newInstance(checkpoint.cpiId, "1", null)
        )
    }

    private fun FlowFiber<*>.setHousekeepingState(checkpoint: Checkpoint, flowContinuation: FlowContinuation) {
        housekeepingState(HousekeepingState(checkpoint, input = flowContinuation))
    }

    private fun FlowFiber<*>.setNonSerializableState(sandbox: SandboxGroupContext) {
        nonSerializableState(NonSerializableState(sandbox.getCheckpointSerializer()))
    }

    private fun SandboxGroupContext.getCheckpointSerializer(): CheckpointSerializer {
        return get<CheckpointSerializer>(FlowSandboxContextTypes.CHECKPOINT_SERIALIZER)!!
    }

    private fun FlowFiber<*>.injectDependencies(sandbox: SandboxGroupContext) {
        sandbox.get<FlowDependencyInjector>(FlowSandboxContextTypes.DEPENDENCY_INJECTOR)!!.injectServices(logic, this)
    }

    private fun getOrCreate(sandboxGroup: SandboxGroup, flowClassName: String, jsonArg: String?): Flow<*> {
        val flowClazz: Class<Flow<*>> = uncheckedCast(sandboxGroup.loadClassFromMainBundles(flowClassName, Flow::class.java))
        val constructor = flowClazz.getDeclaredConstructor(String::class.java)
        return constructor.newInstance(jsonArg)
    }
}