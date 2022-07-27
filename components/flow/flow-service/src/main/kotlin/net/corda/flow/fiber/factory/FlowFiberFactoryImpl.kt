package net.corda.flow.fiber.factory

import co.paralleluniverse.concurrent.util.ScheduledSingleThreadExecutor
import co.paralleluniverse.fibers.FiberExecutorScheduler
import co.paralleluniverse.fibers.FiberScheduler
import net.corda.flow.fiber.FiberFuture
import net.corda.flow.fiber.FlowContinuation
import net.corda.flow.fiber.FlowFiberExecutionContext
import net.corda.flow.fiber.FlowFiberImpl
import net.corda.flow.fiber.FlowLogicAndArgs
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Deactivate
import java.util.UUID
import java.util.concurrent.ExecutorService

@Component
@Suppress("Unused")
class FlowFiberFactoryImpl : FlowFiberFactory {

    private val currentScheduler: FiberScheduler = FiberExecutorScheduler(
        "Same thread scheduler",
        ScheduledSingleThreadExecutor()
    )

    override fun createAndStartFlowFiber(
        flowFiberExecutionContext: FlowFiberExecutionContext,
        flowId: String,
        logic: FlowLogicAndArgs
    ): FiberFuture {
        try {
            val id = UUID.fromString(flowId)
            val flowFiber = FlowFiberImpl(id, logic, currentScheduler)
            return FiberFuture(flowFiber, flowFiber.startFlow(flowFiberExecutionContext))
        } catch (e: Throwable) {
            throw IllegalArgumentException("Expected the flow key to have a UUID id found '${flowId}' instead.", e)
        }
    }

    override fun createAndResumeFlowFiber(
        flowFiberExecutionContext: FlowFiberExecutionContext,
        suspensionOutcome: FlowContinuation
    ): FiberFuture {
        val fiber = flowFiberExecutionContext.sandboxGroupContext.checkpointSerializer.deserialize(
            flowFiberExecutionContext.flowCheckpoint.serializedFiber.array(),
            FlowFiberImpl::class.java
        )

        return FiberFuture(fiber, fiber.resume(flowFiberExecutionContext, suspensionOutcome, currentScheduler))
    }

    @Deactivate
    fun shutdown() {
        currentScheduler.shutdown()
        (currentScheduler.executor as? ExecutorService)?.shutdownNow()
    }
}