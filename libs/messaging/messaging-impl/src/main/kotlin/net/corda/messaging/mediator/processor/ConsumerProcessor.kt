package net.corda.messaging.mediator.processor

import net.corda.libs.statemanager.api.State
import net.corda.messaging.api.exception.CordaMessageAPIIntermittentException
import net.corda.messaging.api.mediator.MediatorConsumer
import net.corda.messaging.api.mediator.config.EventMediatorConfig
import net.corda.messaging.api.mediator.config.MediatorConsumerConfig
import net.corda.messaging.api.mediator.factory.MediatorConsumerFactory
import net.corda.messaging.api.records.Record
import net.corda.messaging.mediator.MediatorSubscriptionState
import net.corda.messaging.mediator.MultiSourceEventMediatorImpl
import net.corda.messaging.mediator.metrics.EventMediatorMetrics
import net.corda.messaging.utils.toRecord
import net.corda.utilities.debug
import org.slf4j.LoggerFactory
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Class to construct a message bus consumer and begin processing its subscribed topic(s).
 * ConsumerProcessor will attempt to create message bus consumers and process records while the mediator is not stopped.
 * If any intermittent failures occur, the message bus consumer will reset to last committed position and retry poll and process loop.
 * If Fatal errors occur they will be throw back to the [MultiSourceEventMediatorImpl]
 * Polled records are divided into groups to process by the [groupAllocator].
 * An [eventProcessor] is used to process each group.
 * All asynchronous outputs (states and message bus events) are stored after all groups have been processed.
 * Any flows from groups that fail to save state to the [stateManager] are retried.
 */
@Suppress("LongParameterList")
class ConsumerProcessor<K : Any, S : Any, E : Any>(
    private val config: EventMediatorConfig<K, S, E>,
    private val mediatorSubscriptionState: MediatorSubscriptionState,
    private val eventProcessor: EventProcessor<K, S, E>,
) {
    private val log = LoggerFactory.getLogger("${this.javaClass.name}-${config.name}")

    private val metrics = EventMediatorMetrics(config.name)

    private val pollTimeout = config.pollTimeout

    private val stateManager = config.stateManager

    private val inFlightStates = ConcurrentHashMap<K, CompletableFuture<State?>>()

    /**
     * Creates a message bus consumer and begins processing records from the subscribed topic.
     * @param consumerFactory used to create a message bus consumer
     * @param consumerConfig used to configure a consumer
     */
    fun processTopic(consumerFactory: MediatorConsumerFactory, consumerConfig: MediatorConsumerConfig<K, E>) {
        var attempts = 0
        var consumer: MediatorConsumer<K, E>? = null
        while (!mediatorSubscriptionState.stopped()) {
            attempts++
            try {
                if (consumer == null) {
                    consumer = consumerFactory.create(consumerConfig)
                    consumer.subscribe()
                }
                pollAndProcessEventsAsync(consumer)
                attempts = 0
            } catch (exception: Exception) {
                val cause = if (exception is CompletionException) { exception.cause ?: exception} else exception
                when (cause) {
                    is CordaMessageAPIIntermittentException -> {
                        log.warn(
                            "Multi-source event mediator ${config.name} failed to process records, " +
                                    "Retrying poll and process. Attempts: $attempts."
                        )
                        consumer?.resetEventOffsetPosition()
                    }
                    else -> {
                        log.debug { "${exception.message} Attempts: $attempts. Fatal error occurred!: $exception"}
                        consumer?.close()
                        //rethrow to break out of processing topic
                        throw cause
                    }
                }
            }
        }
        consumer?.close()
    }

    private fun pollAndProcessEventsAsync(consumer: MediatorConsumer<K, E>) {
        val messages = consumer.poll(pollTimeout)
        val startTimestamp = System.nanoTime()
        val polledRecords = messages.map { it.toRecord() }.groupBy { it.key }
        if (messages.isNotEmpty()) {
            log.debug { "Polled ${messages.size} messages" }
            val events = polledRecords.map { (key, records) ->
                val stateSavedFuture = CompletableFuture<State?>()
                val stateFuture = inFlightStates.put(key, stateSavedFuture)
                stateSavedFuture.thenAccept {
                    inFlightStates.remove(key, stateSavedFuture)
                }
                EventData(key, records, stateSavedFuture, stateFuture)
            }
            val (eventsInFlightState, eventsWithoutState) = events.partition { it.stateFuture != null }

            eventsInFlightState.forEach {
                it.stateFuture!!.thenAccept { state ->
                    eventProcessor.processEvents(it.key, it.records, state, it.stateSavedFuture)
                }
            }

            val states = stateManager.get(eventsWithoutState.map { it.key.toString() })
            log.debug { "Retrieved ${states.size} states" }
            eventsWithoutState.forEach {
                val state = states[it.key.toString()]
                eventProcessor.processEvents(it.key, it.records, state, it.stateSavedFuture)
            }
            metrics.commitTimer.recordCallable {
                consumer.syncCommitOffsets()
            }
        }
        metrics.processorTimer.record(System.nanoTime() - startTimestamp, TimeUnit.NANOSECONDS)
    }

    private class EventData<K: Any, E: Any>(
        val key: K,
        val records: List<Record<K, E>>,
        val stateSavedFuture: CompletableFuture<State?>,
        val stateFuture: CompletableFuture<State?>?,
    )
}
