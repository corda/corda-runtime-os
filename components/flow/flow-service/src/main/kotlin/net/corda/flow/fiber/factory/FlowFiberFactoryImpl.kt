package net.corda.flow.fiber.factory

import co.paralleluniverse.fibers.FiberScheduler
import net.corda.data.flow.FlowKey
import net.corda.flow.fiber.FlowFiber
import net.corda.flow.fiber.FlowFiberImpl
import net.corda.v5.application.flows.Flow
import org.osgi.service.component.annotations.Component
import java.util.UUID

@Component
@Suppress("Unused")
class FlowFiberFactoryImpl : FlowFiberFactory {

    override fun createFlowFiber(flowKey: FlowKey, logic: Flow<*>, scheduler: FiberScheduler): FlowFiber<Any?> {
        try {
            val id = UUID.fromString(flowKey.flowId)
            return FlowFiberImpl(id, flowKey, logic, scheduler)
        } catch (e: Throwable) {
            throw IllegalArgumentException("Expected the flow key to have a UUID id found '${flowKey.flowId}' instead.", e)
        }
    }
}