package net.corda.messaging.subscription

import net.corda.data.CordaAvroSerializer
import net.corda.data.deadletter.StateAndEventDeadLetterRecord
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleStatus
import net.corda.messagebus.api.consumer.CordaConsumer
import net.corda.messagebus.api.consumer.CordaConsumerRecord
import net.corda.messagebus.api.consumer.CordaOffsetResetStrategy
import net.corda.messagebus.api.producer.CordaProducer
import net.corda.messagebus.api.producer.CordaProducerRecord
import net.corda.messaging.api.exception.CordaMessageAPIFatalException
import net.corda.messaging.api.exception.CordaMessageAPIIntermittentException
import net.corda.messaging.api.processor.StateAndEventProcessor
import net.corda.messaging.api.records.Record
import net.corda.messaging.api.subscription.StateAndEventSubscription
import net.corda.messaging.api.subscription.listener.StateAndEventListener
import net.corda.messaging.config.ResolvedSubscriptionConfig
import net.corda.messaging.subscription.consumer.StateAndEventConsumer
import net.corda.messaging.subscription.consumer.builder.StateAndEventBuilder
import net.corda.messaging.subscription.consumer.listener.StateAndEventConsumerRebalanceListener
import net.corda.messaging.utils.getEventsByBatch
import net.corda.messaging.utils.toCordaProducerRecords
import net.corda.messaging.utils.toRecord
import net.corda.messaging.utils.tryGetResult
import net.corda.metrics.CordaMetrics
import net.corda.schema.Schemas.Companion.getStateAndEventDLQTopic
import net.corda.schema.Schemas.Companion.getStateAndEventStateTopic
import net.corda.v5.base.util.debug
import net.corda.v5.base.util.uncheckedCast
import org.slf4j.LoggerFactory
import java.nio.ByteBuffer
import java.time.Clock
import java.time.Duration
import java.util.*

@Suppress("LongParameterList")
internal class StateAndEventSubscriptionImpl<K : Any, S : Any, E : Any>(
    private val config: ResolvedSubscriptionConfig,
    private val builder: StateAndEventBuilder,
    private val processor: StateAndEventProcessor<K, S, E>,
    private val cordaAvroSerializer: CordaAvroSerializer<Any>,
    lifecycleCoordinatorFactory: LifecycleCoordinatorFactory,
    private val stateAndEventListener: StateAndEventListener<K, S>? = null,
    private val clock: Clock = Clock.systemUTC()
) : StateAndEventSubscription<K, S, E> {

    companion object {
        private val EVENT_POLL_TIMEOUT = Duration.ofMillis(100)
    }

    private val log = LoggerFactory.getLogger(config.loggerName)

    private var nullableProducer: CordaProducer? = null
    private var nullableStateAndEventConsumer: StateAndEventConsumer<K, S, E>? = null
    private var nullableEventConsumer: CordaConsumer<K, E>? = null
    private var threadLooper =
        ThreadLooper(log, config, lifecycleCoordinatorFactory, "state/event processing thread", ::runConsumeLoop)

    private val producer: CordaProducer
        get() {
            return nullableProducer ?: throw IllegalStateException("Unexpected access to null producer.")
        }

    private val stateAndEventConsumer: StateAndEventConsumer<K, S, E>
        get() {
            return nullableStateAndEventConsumer
                ?: throw IllegalStateException("Unexpected access to null stateAndEventConsumer.")
        }

    private val eventConsumer: CordaConsumer<K, E>
        get() {
            return nullableEventConsumer ?: throw IllegalStateException("Unexpected access to null eventConsumer.")
        }

    private val eventTopic = config.topic
    private val stateTopic = getStateAndEventStateTopic(config.topic)
    private lateinit var deadLetterRecords: MutableList<ByteArray>

    private val errorMsg = "Failed to read and process records from topic $eventTopic, group ${config.group}, " +
            "producerClientId ${config.clientId}."

    private val processorMeter = CordaMetrics.Metric.MessageProcessorTime.builder()
        .withTag(CordaMetrics.Tag.MessagePatternType, "StateAndEvent")
        .withTag(CordaMetrics.Tag.MessagePatternClientId, config.clientId)
        .withTag(CordaMetrics.Tag.OperationName, "onNext")
        .build()

    /**
     * Is the subscription running.
     */
    val isRunning: Boolean
        get() = threadLooper.isRunning

    override val subscriptionName: LifecycleCoordinatorName
        get() = threadLooper.lifecycleCoordinatorName

    override fun start() {
        log.debug { "Starting subscription with config: $config" }
        threadLooper.start()
    }

    /**
     * This method is for closing the loop/thread externally. From inside the loop use the private [stopConsumeLoop].
     */
    override fun close() {
        threadLooper.close()
    }

    private fun runConsumeLoop() {
        var attempts = 0
        var nullableRebalanceListener: StateAndEventConsumerRebalanceListener? = null

        while (!threadLooper.loopStopped) {
            attempts++
            try {
                deadLetterRecords = mutableListOf()
                nullableProducer = builder.createProducer(config)
                val (stateAndEventConsumerTmp, rebalanceListener) = builder.createStateEventConsumerAndRebalanceListener(
                    config,
                    processor.keyClass,
                    processor.stateValueClass,
                    processor.eventValueClass,
                    stateAndEventListener,
                    { data ->
                        log.error("Failed to deserialize state record from $stateTopic")
                        deadLetterRecords.add(data)
                    },
                    { data ->
                        log.error("Failed to deserialize event record from $eventTopic")
                        deadLetterRecords.add(data)
                    }
                )
                nullableRebalanceListener = rebalanceListener
                val eventConsumerTmp = stateAndEventConsumerTmp.eventConsumer
                nullableStateAndEventConsumer = stateAndEventConsumerTmp
                nullableEventConsumer = eventConsumerTmp
                eventConsumerTmp.subscribe(eventTopic, rebalanceListener)
                threadLooper.updateLifecycleStatus(LifecycleStatus.UP)

                while (!threadLooper.loopStopped) {
                    stateAndEventConsumerTmp.pollAndUpdateStates(true)
                    processEvents()
                }

            } catch (ex: Exception) {
                when (ex) {
                    is CordaMessageAPIIntermittentException -> {
                        log.warn(
                            "$errorMsg Attempts: $attempts. Recreating " +
                                    "consumer/producer and Retrying.", ex
                        )
                    }

                    else -> {
                        log.error(
                            "$errorMsg Attempts: $attempts. Closing subscription.", ex
                        )
                        threadLooper.updateLifecycleStatus(LifecycleStatus.ERROR, errorMsg)
                        threadLooper.stopLoop()
                    }
                }
            } finally {
                closeStateAndEventProducerConsumer()
            }
        }
        nullableRebalanceListener?.close()
        closeStateAndEventProducerConsumer()
    }

    private fun closeStateAndEventProducerConsumer() {
        nullableProducer?.close()
        nullableStateAndEventConsumer?.close()
        nullableProducer = null
        nullableStateAndEventConsumer = null
    }

    private fun processEvents() {
        var attempts = 0
        var pollAndProcessSuccessful = false
        while (!pollAndProcessSuccessful && !threadLooper.loopStopped) {
            try {
                for (batch in getEventsByBatch(eventConsumer.poll(EVENT_POLL_TIMEOUT))) {
                    tryProcessBatchOfEvents(batch)
                }
                pollAndProcessSuccessful = true
            } catch (ex: Exception) {
                when (ex) {
                    is CordaMessageAPIIntermittentException -> {
                        attempts++
                        handleProcessEventRetries(attempts, ex)
                    }

                    else -> {
                        throw CordaMessageAPIFatalException(
                            "Failed to process records from topic $eventTopic, group ${config.group}, " +
                                    "producerClientId ${config.clientId}. " +
                                    "Fatal error occurred.", ex
                        )
                    }
                }
            }
        }
    }

    private fun tryProcessBatchOfEvents(events: List<CordaConsumerRecord<K, E>>) {
        val outputRecords = mutableListOf<Record<*, *>>()
        val updatedStates: MutableMap<Int, MutableMap<K, S?>> = mutableMapOf()

        log.debug { "Processing events(size: ${events.size})" }
        for (event in events) {
            stateAndEventConsumer.resetPollInterval()
            processEvent(event, outputRecords, updatedStates)
        }

        producer.beginTransaction()
        producer.sendRecords(outputRecords.toCordaProducerRecords())
        if (deadLetterRecords.isNotEmpty()) {
            producer.sendRecords(deadLetterRecords.map {
                CordaProducerRecord(
                    getStateAndEventDLQTopic(eventTopic),
                    UUID.randomUUID().toString(),
                    it
                )
            })
        }
        producer.sendRecordOffsetsToTransaction(eventConsumer, events.map { it })
        producer.commitTransaction()
        log.debug { "Processing of events(size: ${events.size}) complete" }

        stateAndEventConsumer.updateInMemoryStatePostCommit(updatedStates, clock)
    }

    private fun processEvent(
        event: CordaConsumerRecord<K, E>,
        outputRecords: MutableList<Record<*, *>>,
        updatedStates: MutableMap<Int, MutableMap<K, S?>>
    ) {
        log.debug { "Processing event: $event" }
        val key = event.key
        val state = stateAndEventConsumer.getInMemoryStateValue(key)
        val partitionId = event.partition
        val thisEventUpdates = getUpdatesForEvent(state, event)

        when {
            thisEventUpdates == null -> {
                log.warn("Sending state and event on key ${event.key} for topic ${event.topic} to dead letter queue. Processor failed to complete.")
                outputRecords.add(generateDeadLetterRecord(event, state))
                outputRecords.add(Record(stateTopic, key, null))
                updatedStates.computeIfAbsent(partitionId) { mutableMapOf() }[key] = null
            }

            thisEventUpdates.markForDLQ -> {
                log.warn("Sending state and event on key ${event.key} for topic ${event.topic} to dead letter queue. Processor marked event for the dead letter queue")
                outputRecords.add(generateDeadLetterRecord(event, state))
                outputRecords.add(Record(stateTopic, key, null))
                updatedStates.computeIfAbsent(partitionId) { mutableMapOf() }[key] = null

                // In this case the processor may ask us to publish some output records regardless, so make sure these
                // are outputted.
                outputRecords.addAll(thisEventUpdates.responseEvents)
            }

            else -> {
                outputRecords.addAll(thisEventUpdates.responseEvents)
                val updatedState = thisEventUpdates.updatedState
                outputRecords.add(Record(stateTopic, key, updatedState))
                updatedStates.computeIfAbsent(partitionId) { mutableMapOf() }[key] = updatedState
                log.debug { "Completed event: $event" }
            }
        }
    }

    private fun getUpdatesForEvent(state: S?, event: CordaConsumerRecord<K, E>): StateAndEventProcessor.Response<S>? {
        val future = processorMeter.recordCallable {
            stateAndEventConsumer.waitForFunctionToFinish(
                { processor.onNext(state, event.toRecord()) }, config.processorTimeout.toMillis(),
                "Failed to finish within the time limit for state: $state and event: $event"
            )
        }!!
        return uncheckedCast(future.tryGetResult())
    }

    private fun generateDeadLetterRecord(event: CordaConsumerRecord<K, E>, state: S?): Record<*, *> {
        val keyBytes = ByteBuffer.wrap(cordaAvroSerializer.serialize(event.key))
        val stateBytes =
            if (state != null) ByteBuffer.wrap(cordaAvroSerializer.serialize(state)) else null
        val eventValue = event.value
        val eventBytes =
            if (eventValue != null) ByteBuffer.wrap(cordaAvroSerializer.serialize(eventValue)) else null
        return Record(
            getStateAndEventDLQTopic(eventTopic), event.key,
            StateAndEventDeadLetterRecord(clock.instant(), keyBytes, stateBytes, eventBytes)
        )
    }

    /**
     * Handle retries for event processing.
     * Reset [nullableEventConsumer] position and retry poll and process of eventRecords
     * Retry a max of [consumerPollAndProcessMaxRetries] times.
     * If [consumerPollAndProcessMaxRetries] is exceeded then throw a [CordaMessageAPIIntermittentException]
     */
    private fun handleProcessEventRetries(
        attempts: Int,
        ex: Exception
    ) {
        if (attempts <= config.processorRetries) {
            log.warn(
                "Failed to process record from topic $eventTopic, group ${config.group}, " +
                        "producerClientId ${config.clientId}. " +
                        "Retrying poll and process. Attempts: $attempts."
            )
            eventConsumer.resetToLastCommittedPositions(CordaOffsetResetStrategy.EARLIEST)
        } else {
            val message = "Failed to process records from topic $eventTopic, group ${config.group}, " +
                    "producerClientId ${config.clientId}. " +
                    "Attempts: $attempts. Max reties exceeded."
            log.warn(message, ex)
            throw CordaMessageAPIIntermittentException(message, ex)
        }
    }
}
