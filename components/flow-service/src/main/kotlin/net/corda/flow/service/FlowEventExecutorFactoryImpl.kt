package net.corda.flow.service

import co.paralleluniverse.concurrent.util.ScheduledSingleThreadExecutor
import co.paralleluniverse.fibers.FiberExecutorScheduler
import net.corda.data.flow.event.StartRPCFlow
import net.corda.data.flow.event.Wakeup
import net.corda.flow.manager.FlowEventExecutor
import net.corda.flow.manager.FlowEventExecutorFactory
import net.corda.flow.manager.FlowMetaData
import net.corda.flow.manager.FlowSandboxService
import net.corda.flow.manager.impl.StartRPCFlowExecutor
import net.corda.flow.manager.impl.WakeupExecutor
import net.corda.flow.statemachine.factory.FlowStateMachineFactory
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Component(service = [FlowEventExecutorFactory::class])
class FlowEventExecutorFactoryImpl @Activate constructor(
    @Reference(service = FlowSandboxService::class)
    private val flowSandboxService: FlowSandboxService,
    @Reference(service = FlowStateMachineFactory::class)
    private val flowStateMachineFactory: FlowStateMachineFactory
) : FlowEventExecutorFactory {

    private val fiberScheduler = FiberExecutorScheduler(
        "Flow fiber single threaded scheduler",
        ScheduledSingleThreadExecutor())

    override fun create(flowMetaData: FlowMetaData): FlowEventExecutor {
        return when (flowMetaData.payload) {
            is StartRPCFlow -> StartRPCFlowExecutor(
                flowMetaData,
                flowSandboxService,
                flowStateMachineFactory,
                fiberScheduler
            )

            is Wakeup -> WakeupExecutor(
                flowMetaData,
                flowSandboxService,
                fiberScheduler)

            else -> throw NotImplementedError(
                "The event type '${flowMetaData.payload.javaClass.name}' is not supported.")
        }
    }
}