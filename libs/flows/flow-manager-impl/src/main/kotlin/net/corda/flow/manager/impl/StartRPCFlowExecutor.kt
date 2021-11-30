package net.corda.flow.manager.impl

import co.paralleluniverse.fibers.FiberScheduler
import net.corda.dependency.injection.FlowDependencyInjector
import net.corda.flow.manager.FlowEventExecutor
import net.corda.flow.manager.FlowMetaData
import net.corda.flow.manager.FlowResult
import net.corda.flow.manager.FlowSandboxContextTypes
import net.corda.flow.manager.FlowSandboxService
import net.corda.flow.manager.impl.FlowExecutorUtilities.Companion.setupFlow
import net.corda.flow.manager.impl.FlowExecutorUtilities.Companion.toRecordsWithKey
import net.corda.flow.statemachine.HousekeepingState
import net.corda.flow.statemachine.factory.FlowStateMachineFactory
import net.corda.sandbox.SandboxGroup
import net.corda.serialization.CheckpointSerializer
import net.corda.v5.application.flows.Flow
import net.corda.v5.base.util.uncheckedCast

class StartRPCFlowExecutor(
    private val flowMetaData: FlowMetaData,
    private val flowSandboxService: FlowSandboxService,
    private val flowStateMachineFactory: FlowStateMachineFactory,
    private val fiberScheduler: FiberScheduler
) : FlowEventExecutor {
    override fun execute(): FlowResult {
        val sandbox = flowSandboxService.get(flowMetaData.holdingIdentity, flowMetaData.cpi)
        val flow = getOrCreate(sandbox.sandboxGroup, flowMetaData.flowName, flowMetaData.jsonArg)
        val checkpointSerializer = sandbox.get<CheckpointSerializer>(FlowSandboxContextTypes.CHECKPOINT_SERIALIZER) !!
        val dependencyInjector = sandbox.get<FlowDependencyInjector>(FlowSandboxContextTypes.DEPENDENCY_INJECTOR) !!
        val stateMachine = flowStateMachineFactory.createStateMachine(
            flowMetaData.clientId,
            flowMetaData.flowKey,
            flow,
            flowMetaData.cpiId,
            flowMetaData.flowName,
            fiberScheduler,
        )

        stateMachine.housekeepingState(
            HousekeepingState(
                0,
                false,
                mutableListOf()
            )
        )

        setupFlow(stateMachine, dependencyInjector, checkpointSerializer)
        stateMachine.startFlow()

        val (checkpoint, events) = stateMachine.waitForCheckpoint()

        return FlowResult(
            checkpoint,
            events.toRecordsWithKey(flowMetaData.flowEventTopic)
        )
    }

    @Suppress("SpreadOperator")
    private fun getOrCreate(sandboxGroup: SandboxGroup, flowName: String, jsonArg: String?): Flow<*> {
        val flowClazz: Class<Flow<*>> =
            uncheckedCast(sandboxGroup.loadClassFromMainBundles(flowName, Flow::class.java))
        val constructor = flowClazz.getDeclaredConstructor(String::class.java)
        return constructor.newInstance(jsonArg)
    }
}

