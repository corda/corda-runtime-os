package net.corda.flow.manager.impl

import co.paralleluniverse.fibers.Fiber
import co.paralleluniverse.fibers.FiberScheduler
import net.corda.dependency.injection.FlowDependencyInjector
import net.corda.flow.manager.FlowEventExecutor
import net.corda.flow.manager.FlowMetaData
import net.corda.flow.manager.FlowResult
import net.corda.flow.manager.FlowSandboxService
import net.corda.flow.manager.FlowSandboxContextTypes
import net.corda.flow.manager.impl.FlowExecutorUtilities.Companion.setupFlow
import net.corda.flow.manager.impl.FlowExecutorUtilities.Companion.toRecordsWithKey
import net.corda.flow.statemachine.HousekeepingState
import net.corda.flow.statemachine.impl.FlowStateMachineImpl
import net.corda.serialization.CheckpointSerializer

class WakeupExecutor(
    val flowMetaData: FlowMetaData,
    val flowSandboxService: FlowSandboxService,
    val fiberScheduler: FiberScheduler
): FlowEventExecutor {
    override fun execute(): FlowResult {

        val sandbox = flowSandboxService.get(flowMetaData.holdingIdentity, flowMetaData.cpi)
        val checkpointSerializer = sandbox.get<CheckpointSerializer>(FlowSandboxContextTypes.CHECKPOINT_SERIALIZER) !!
        val dependencyInjector = sandbox.get<FlowDependencyInjector>(FlowSandboxContextTypes.DEPENDENCY_INJECTOR) !!
        val stateMachine = checkpointSerializer.deserialize(
            flowMetaData.checkpoint!!.fiber.array(),
            FlowStateMachineImpl::class.java
        )

        val flowState = flowMetaData.checkpoint!!.flowState
        val flowEvents = flowState.eventQueue
        flowEvents.add(flowMetaData.flowEvent)
        val housekeepingState = HousekeepingState(flowState.suspendCount, flowState.isKilled, flowEvents)
        stateMachine.housekeepingState(housekeepingState)

        setupFlow(stateMachine, dependencyInjector,checkpointSerializer )
        Fiber.unparkDeserialized(stateMachine, fiberScheduler)
        val (checkpoint, events) = stateMachine.waitForCheckpoint()
        return FlowResult(checkpoint, events.toRecordsWithKey(flowMetaData.flowEventTopic))

    }
}

