package net.corda.messaging.mediator

import net.corda.data.flow.event.FlowEvent
import net.corda.data.flow.event.mapper.FlowMapperEvent
import net.corda.libs.statemanager.api.State
import net.corda.messaging.api.processor.StateAndEventProcessor
import net.corda.messaging.api.records.Record
import org.slf4j.Logger
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
    private companion object {
        private val log: Logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    class Result<K : Any, S : Any, E : Any>(
        val processorTask: ProcessorTask<K, S, E>,
        val outputEvents: List<Record<*, *>>,
        val updatedState: State?,
    ) {
        val key get() = processorTask.key
    }

    override fun call(): Result<K, S, E> {
        var state = stateManagerHelper.deserializeValue(persistedState)?.let { stateValue ->
            StateAndEventProcessor.State(
                stateValue,
                persistedState?.metadata
            )
        }

        val outputEvents = events.map { event ->
            when (val eventValue = event.value) {
                is FlowEvent -> {
                    val eventType = eventValue.payload::class.java.simpleName
                    log.info("Processing event: FlowEvent:$eventType [${key}], event [$event], hasState[${state?.value != null}]")
                }

                is FlowMapperEvent -> {
                    val eventType = eventValue.payload
                    val eventTypeName = eventType::class.java.simpleName
                    val eventSubtypeName = if (eventType is FlowEvent) ":${eventType::class.java.simpleName}" else ""
                    log.info("Processing event: FlowMapperEvent:$eventTypeName$eventSubtypeName [${key}], event [$event]," +
                            " hasState[${state?.value != null}]")
                }

                else -> {
                    val eventType = eventValue?.let { it::class.java.simpleName }
                    log.info("Processing event: $eventType [${key}], state[${state?.value != null}]")
                }
            }
            val response = processor.onNext(state, event)
            log.info("Processing event resulted with [${response.responseEvents.size}] output events")
            state = response.updatedState
            response.responseEvents
        }.flatten()

        val updatedState = stateManagerHelper.createOrUpdateState(
            key.toString(),
            persistedState,
            state,
        )

        return Result(this, outputEvents, updatedState)
    }
}
