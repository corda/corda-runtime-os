package net.corda.flow.manager.impl

import net.corda.data.flow.FlowKey
import net.corda.data.flow.event.FlowEvent
import net.corda.dependency.injection.FlowDependencyInjector
import net.corda.flow.statemachine.FlowStateMachine
import net.corda.flow.statemachine.NonSerializableState
import net.corda.messaging.api.records.Record
import net.corda.serialization.CheckpointSerializer
import java.time.Clock

class FlowExecutorUtilities{
    companion object {
        fun setupFlow(
            flow: FlowStateMachine<*>,
            dependencyInjector: FlowDependencyInjector,
            checkpointSerializer: CheckpointSerializer
        ) {
            with(flow) {
                nonSerializableState(
                    NonSerializableState(
                        checkpointSerializer,
                        Clock.systemUTC()
                    )
                )
            }

            dependencyInjector.injectServices(flow.getFlowLogic(), flow)
        }

        fun List<FlowEvent>.toRecordsWithKey(flowEventTopic: String): List<Record<FlowKey, FlowEvent>> {
            return this.map { event ->
                Record(
                    flowEventTopic,
                    event.flowKey,
                    event
                )
            }
        }
    }
}