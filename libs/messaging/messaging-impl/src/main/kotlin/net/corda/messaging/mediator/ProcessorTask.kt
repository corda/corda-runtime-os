package net.corda.messaging.mediator

import net.corda.libs.statemanager.api.State
import net.corda.messaging.api.processor.StateAndEventProcessor
import net.corda.messaging.api.records.Record
import org.slf4j.LoggerFactory
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
    companion object {
        private val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

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
            log.info("Processing event: [$event]")
            val response = processor.onNext(stateValue, event)
            log.info("Output events: [${response.responseEvents}]")
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
