package net.corda.flow.statemachine.impl.factory

import co.paralleluniverse.fibers.FiberScheduler
import net.corda.data.flow.FlowKey
import net.corda.flow.statemachine.FlowFiber
import net.corda.flow.statemachine.factory.FlowFiberFactory
import net.corda.flow.statemachine.impl.FlowFiberImpl
import net.corda.v5.application.flows.Flow
import org.osgi.service.component.annotations.Component

@Component
class FlowFiberFactoryImpl : FlowFiberFactory {

    override fun createFlowFiber(
        clientId: String?,
        id: FlowKey,
        logic: Flow<*>,
        cpiId: String,
        flowName: String,
        scheduler: FiberScheduler
    ): FlowFiber<Any?> {
        return FlowFiberImpl(
            clientId,
            id,
            logic,
            cpiId,
            flowName,
            scheduler
        )
    }
}
