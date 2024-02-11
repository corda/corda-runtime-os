package net.corda.messaging.mediator.processor

import net.corda.libs.statemanager.api.State
import net.corda.messaging.api.mediator.MediatorMessage
import net.corda.messaging.api.processor.StateAndEventProcessor
import java.util.concurrent.CompletableFuture

data class EventContext<K: Any, S: Any>(
    val key: K,
    val inputState: State?,
    val stateSavedFuture: CompletableFuture<State?>,
    var processorState: StateAndEventProcessor.State<S>? = null,
    val asyncOutputs: MutableList<MediatorMessage<Any>> = mutableListOf(),
)