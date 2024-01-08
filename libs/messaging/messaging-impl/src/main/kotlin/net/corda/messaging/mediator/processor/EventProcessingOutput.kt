package net.corda.messaging.mediator.processor

import net.corda.libs.statemanager.api.State
import net.corda.messaging.api.mediator.MediatorMessage

data class EventProcessingOutput(
    val asyncOutputs: List<MediatorMessage<Any>>,
    val outputState: State?,
    val stateUpdateKind: StateUpdateKind
)

enum class StateUpdateKind {
    CREATE,
    UPDATE,
    DELETE,
    NOOP
}