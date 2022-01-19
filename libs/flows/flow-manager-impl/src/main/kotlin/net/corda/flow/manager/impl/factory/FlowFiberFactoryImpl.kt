package net.corda.flow.manager.impl.factory

import co.paralleluniverse.fibers.FiberScheduler
import net.corda.data.flow.FlowKey
import net.corda.flow.manager.factory.FlowFiberFactory
import net.corda.flow.manager.fiber.FlowFiber
import net.corda.flow.manager.impl.fiber.FlowFiberImpl
import net.corda.v5.application.flows.Flow
import net.corda.v5.application.flows.FlowId
import org.osgi.service.component.annotations.Component
import java.util.*

@Component
@Suppress("Unused")
class FlowFiberFactoryImpl : FlowFiberFactory {

    override fun createFlowFiber(flowKey: FlowKey, logic: Flow<*>, scheduler: FiberScheduler): FlowFiber<Any?> {
        try {
            val id = FlowId(UUID.fromString(flowKey.flowId))
            return FlowFiberImpl(id, flowKey, logic, scheduler)
        } catch (e: Throwable) {
            throw IllegalArgumentException("Expected the flow key to have a UUID id found '${flowKey.flowId}' instead.", e)
        }
    }
}