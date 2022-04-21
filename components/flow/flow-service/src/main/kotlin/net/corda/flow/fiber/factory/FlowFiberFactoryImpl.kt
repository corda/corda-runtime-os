package net.corda.flow.fiber.factory

import co.paralleluniverse.fibers.FiberScheduler
import net.corda.flow.fiber.FlowFiber
import net.corda.flow.fiber.FlowFiberExecutionContext
import net.corda.flow.fiber.FlowFiberImpl
import net.corda.v5.application.flows.Flow
import org.osgi.service.component.annotations.Component
import java.util.UUID

@Component
@Suppress("Unused")
class FlowFiberFactoryImpl : FlowFiberFactory {

    override fun createFlowFiber(flowId: String, logic: Flow<*>, scheduler: FiberScheduler): FlowFiber<Any?> {
        try {
            val id = UUID.fromString(flowId)
            return FlowFiberImpl(id, logic, scheduler)
        } catch (e: Throwable) {
            throw IllegalArgumentException("Expected the flow key to have a UUID id found '${flowId}' instead.", e)
        }
    }

    override fun createFlowFiber(flowFiberExecutionContext: FlowFiberExecutionContext): FlowFiber<*> {
        return flowFiberExecutionContext.checkpointSerializer.deserialize(
            flowFiberExecutionContext.flowCheckpoint.serializedFiber.array(),
            FlowFiberImpl::class.java
        )
    }
}