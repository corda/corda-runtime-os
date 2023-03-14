package net.corda.flow.fiber.factory

import co.paralleluniverse.concurrent.util.ScheduledSingleThreadExecutor
import co.paralleluniverse.fibers.FiberExecutorScheduler
import co.paralleluniverse.fibers.FiberScheduler
import net.corda.flow.fiber.FiberFuture
import net.corda.flow.fiber.FlowContinuation
import net.corda.flow.fiber.FlowFiberExecutionContext
import net.corda.flow.fiber.FlowFiberImpl
import net.corda.flow.fiber.FlowLogicAndArgs
import net.corda.flow.fiber.FiberExceptionConstants
import net.corda.flow.pipeline.exceptions.FlowFatalException
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
        val id = try {
            UUID.fromString(flowId)
        } catch (e: IllegalArgumentException) {
            throw FlowFatalException(FiberExceptionConstants.INVALID_FLOW_KEY.format(flowId), e)
        }
        try {
            val flowFiber = FlowFiberImpl(id, logic, currentScheduler)
            return FiberFuture(flowFiber, flowFiber.startFlow(flowFiberExecutionContext))
        } catch (e: Throwable) {
            throw FlowFatalException(FiberExceptionConstants.UNABLE_TO_EXECUTE.format(e.message ?: "No exception message provided."), e)
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