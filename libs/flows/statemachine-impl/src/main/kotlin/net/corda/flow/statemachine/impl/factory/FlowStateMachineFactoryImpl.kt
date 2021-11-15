package net.corda.flow.statemachine.impl.factory

import co.paralleluniverse.fibers.FiberScheduler
import net.corda.data.flow.FlowKey
import net.corda.flow.statemachine.FlowStateMachine
import net.corda.flow.statemachine.factory.FlowStateMachineFactory
import net.corda.flow.statemachine.impl.FlowStateMachineImpl
import net.corda.v5.application.flows.Flow
import org.osgi.service.component.annotations.Component

@Component
class FlowStateMachineFactoryImpl : FlowStateMachineFactory {

    override fun createStateMachine(
        clientId: String?,
        id: FlowKey,
        logic: Flow<*>,
        cpiId: String,
        flowName: String,
        scheduler: FiberScheduler
    ): FlowStateMachine<Any?> {
        return FlowStateMachineImpl(
            clientId,
            id,
            logic,
            cpiId,
            flowName,
            scheduler
        )
    }
}
