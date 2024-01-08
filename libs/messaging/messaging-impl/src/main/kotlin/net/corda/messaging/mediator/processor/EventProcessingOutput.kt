package net.corda.messaging.mediator.processor

import net.corda.libs.statemanager.api.State
import net.corda.messaging.api.mediator.MediatorMessage

data class EventProcessingOutput(
    val asyncOutputs: List<MediatorMessage<Any>>,
    val stateUpdate: StateUpdate
)

sealed interface StateUpdate {
    val outputState: State?

    class Create(override val outputState: State) : StateUpdate
    class Update(override val outputState: State) : StateUpdate
    class Delete(override val outputState: State) : StateUpdate
    object Noop : StateUpdate {
        override val outputState: State? = null
    }
}
