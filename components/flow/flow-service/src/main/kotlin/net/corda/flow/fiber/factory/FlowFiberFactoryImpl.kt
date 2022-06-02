package net.corda.flow.fiber.factory

import co.paralleluniverse.concurrent.util.ScheduledSingleThreadExecutor
import co.paralleluniverse.fibers.FiberExecutorScheduler
import co.paralleluniverse.fibers.FiberScheduler
import net.corda.flow.fiber.FlowContinuation
import net.corda.flow.fiber.FlowFiber
import net.corda.flow.fiber.FlowFiberExecutionContext
import net.corda.flow.fiber.FlowFiberImpl
import net.corda.flow.fiber.FlowIORequest
import net.corda.v5.application.flows.Flow
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Deactivate
import java.util.UUID
import java.util.concurrent.ExecutorService
import java.util.concurrent.Future

@Component
@Suppress("Unused")
class FlowFiberFactoryImpl : FlowFiberFactory {

    private val currentScheduler: FiberScheduler = FiberExecutorScheduler(
        "Same thread scheduler",
        ScheduledSingleThreadExecutor()
    )

    override fun createFlowFiber(flowId: String, logic: Flow<*>, args: Any?): FlowFiber<Any?> {
        try {
            val id = UUID.fromString(flowId)
            return FlowFiberImpl(id, logic, args, currentScheduler)
        } catch (e: Throwable) {
            throw IllegalArgumentException("Expected the flow key to have a UUID id found '${flowId}' instead.", e)
        }
    }

    override fun createAndResumeFlowFiber(
        flowFiberExecutionContext: FlowFiberExecutionContext,
        suspensionOutcome: FlowContinuation
    ): Future<FlowIORequest<*>> {
        return flowFiberExecutionContext.sandboxGroupContext.checkpointSerializer.deserialize(
            flowFiberExecutionContext.flowCheckpoint.serializedFiber.array(),
            FlowFiberImpl::class.java
        ).resume(flowFiberExecutionContext,suspensionOutcome, currentScheduler)
    }

    @Deactivate
    fun shutdown() {
        currentScheduler.shutdown()
        (currentScheduler.executor as? ExecutorService)?.shutdownNow()
    }
}