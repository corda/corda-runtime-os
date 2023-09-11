package net.corda.messaging.mediator

import net.corda.messagebus.api.consumer.CordaConsumerRecord
import net.corda.messaging.api.mediator.statemanager.State
import net.corda.messaging.api.mediator.statemanager.StateManager
import net.corda.messaging.api.processor.StateAndEventProcessor
import net.corda.messaging.api.records.Record
import net.corda.messaging.utils.toRecord

class ProcessorTask<K: Any, S: Any, E: Any>(
    private val key: String,
    private val stateClass: Class<S>,
    private val events: Collection<CordaConsumerRecord<K, E>>,
    private val processor: StateAndEventProcessor<K, S, E>,
    private val stateManager: StateManager,
) {

    private var responseEvents = emptyList<Record<*, *>>()
    fun run() {
        val state = stateManager.get(stateClass, setOf(key))[key]
            ?: State(null, key)
        var updatedState = state.state
        responseEvents = events.map { event ->
            val response = processor.onNext(updatedState, event.toRecord())
            response.updatedState?.let { updatedState = it }
            response.responseEvents
        }.flatten()

        val newState = State(
            updatedState!!,
            key,
            state.metadata,
            state.version + 1
        )
        stateManager.update(stateClass, setOf(newState))
    }
}