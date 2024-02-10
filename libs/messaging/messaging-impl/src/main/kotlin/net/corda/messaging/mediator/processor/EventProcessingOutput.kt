package net.corda.messaging.mediator.processor

import net.corda.libs.statemanager.api.State
import net.corda.messaging.api.mediator.MediatorMessage

data class EventProcessingOutput(
    val asyncOutputs: List<MediatorMessage<Any>>,
    val stateChangeAndOperation: StateChangeAndOperation
)

sealed interface StateChangeAndOperation {
    companion object {
        fun create(
            oldState: State?,
            newState: State?
        ) = when {
            oldState == null && newState != null -> Create(newState)
            oldState != null && newState != null -> Update(newState)
            oldState != null && newState == null -> Delete(oldState)
            else -> Noop
        }
    }

    val outputState: State?

    class Create(override val outputState: State) : StateChangeAndOperation
    class Update(override val outputState: State) : StateChangeAndOperation
    class Delete(override val outputState: State) : StateChangeAndOperation

    // This can happen if both input and output are null. There may still be outputs in this case (for example a flow
    // status change).
    object Noop : StateChangeAndOperation {
        override val outputState: State? = null
    }
}
