package net.corda.flow.fiber.impl.factory

import co.paralleluniverse.fibers.FiberScheduler
import net.corda.data.flow.FlowKey
import net.corda.flow.fiber.FlowFiber
import net.corda.flow.fiber.factory.FlowFiberFactory
import net.corda.flow.fiber.impl.FlowFiberImpl
import net.corda.v5.application.flows.Flow
import org.osgi.service.component.annotations.Component

@Component
class FlowFiberFactoryImpl : FlowFiberFactory {

    override fun createFlowFiber(id: FlowKey, logic: Flow<*>, scheduler: FiberScheduler): FlowFiber<Any?> {
        return FlowFiberImpl(id, logic, scheduler)
    }
}
