package net.corda.messaging.mediator

import net.corda.libs.statemanager.api.State
import net.corda.messaging.api.processor.StateAndEventProcessor
import net.corda.messaging.api.records.Record
import java.util.concurrent.Callable

/**
 * [ProcessorTask] uses [StateAndEventProcessor] to process input events (that have the same key) and related states.
 * Events are processed sequentially and updated state of the current event is used as the input state of the next
 * event. Result of processing are output events and final updated state.
 */
@Suppress("LongParameterList")
data class ProcessorTask<K : Any, S : Any, E : Any>(
    val key: K,
    val persistedState: State?,
    val events: Collection<Record<K, E>>,
    private val processor: StateAndEventProcessor<K, S, E>,
    private val stateManagerHelper: StateManagerHelper<K, S, E>,
) : Callable<ProcessorTask.Result<K, S, E>> {

    class Result<K : Any, S : Any, E : Any>(
        val processorTask: ProcessorTask<K, S, E>,
        val outputEvents: List<Record<*, *>>,
        val updatedState: State?,
    ) {
        val key get() = processorTask.key
    }

    override fun call(): Result<K, S, E> {
        var stateValue = stateManagerHelper.deserializeValue(persistedState)

        val outputEvents = events.map { event ->
            val response = processor.onNext(stateValue, event)
            stateValue = response.updatedState
            response.responseEvents
        }.flatten()

        val updatedState = stateManagerHelper.createOrUpdateState(
            key.toString(),
            persistedState,
            stateValue
        )

        return Result(this, outputEvents, updatedState)
    }
}
