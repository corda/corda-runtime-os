package net.corda.messaging.subscription

import net.corda.avro.serialization.CordaAvroDeserializer
import net.corda.avro.serialization.CordaAvroSerializer
import net.corda.data.deadletter.StateAndEventDeadLetterRecord
import net.corda.data.flow.event.FlowEvent
import net.corda.data.flow.event.Wakeup
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleStatus
import net.corda.messagebus.api.consumer.CordaConsumer
import net.corda.messagebus.api.consumer.CordaConsumerRecord
import net.corda.messagebus.api.producer.CordaProducer
import net.corda.messaging.api.exception.CordaMessageAPIFatalException
import net.corda.messaging.api.exception.CordaMessageAPIIntermittentException
import net.corda.messaging.api.processor.StateAndEventProcessor
import net.corda.messaging.api.records.Record
import net.corda.messaging.api.subscription.StateAndEventSubscription
import net.corda.messaging.api.subscription.listener.StateAndEventListener
import net.corda.messaging.config.ResolvedSubscriptionConfig
import net.corda.messaging.constants.MetricsConstants
import net.corda.messaging.publisher.RestClient
import net.corda.messaging.subscription.consumer.StateAndEventConsumer
import net.corda.messaging.subscription.consumer.builder.StateAndEventBuilder
import net.corda.messaging.subscription.consumer.listener.StateAndEventConsumerRebalanceListener
import net.corda.messaging.utils.getEventsByBatch
import net.corda.messaging.utils.toCordaProducerRecords
import net.corda.messaging.utils.toRecord
import net.corda.messaging.utils.tryGetResult
import net.corda.metrics.CordaMetrics
import net.corda.schema.Schemas
import net.corda.schema.Schemas.getDLQTopic
import net.corda.schema.Schemas.getStateAndEventStateTopic
import net.corda.utilities.debug
import org.slf4j.LoggerFactory
import java.nio.ByteBuffer
import java.time.Clock

@Suppress("LongParameterList")
internal class StreamingStateAndEventSubscription<K : Any, S : Any, E : Any>(
    private val config: ResolvedSubscriptionConfig,
    private val builder: StateAndEventBuilder,
    private val processor: StateAndEventProcessor<K, S, E>,
    private val cordaAvroSerializer: CordaAvroSerializer<Any>,
    private val cordaAvroDeserializer: CordaAvroDeserializer<Any>,
    lifecycleCoordinatorFactory: LifecycleCoordinatorFactory,
    private val stateAndEventListener: StateAndEventListener<K, S>? = null,
    private val clock: Clock = Clock.systemUTC(),
) : StateAndEventSubscription<K, S, E> {

    private val log = LoggerFactory.getLogger("${this.javaClass.name}-${config.clientId}")

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
        .withTag(CordaMetrics.Tag.MessagePatternType, MetricsConstants.STATE_AND_EVENT_PATTERN_TYPE)
        .withTag(CordaMetrics.Tag.MessagePatternClientId, config.clientId)
        .withTag(CordaMetrics.Tag.OperationName, MetricsConstants.BATCH_PROCESS_OPERATION)
        .build()

    private val batchSizeHistogram = CordaMetrics.Metric.MessageBatchSize.builder()
        .withTag(CordaMetrics.Tag.MessagePatternType, MetricsConstants.STATE_AND_EVENT_PATTERN_TYPE)
        .withTag(CordaMetrics.Tag.MessagePatternClientId, config.clientId)
        .build()

    private val recordsAvoidedCount = CordaMetrics.Metric.RecordPublishAvoidedCount.builder()
        .withTag(CordaMetrics.Tag.MessagePatternType, MetricsConstants.STATE_AND_EVENT_PATTERN_TYPE)
        .withTag(CordaMetrics.Tag.MessagePatternClientId, config.clientId)
        .withTag(CordaMetrics.Tag.Topic, config.topic)
        .build()

    private val topicToRestClient = mapOf(
        Schemas.Crypto.FLOW_OPS_MESSAGE_TOPIC to RestClient("crypto.process", cordaAvroSerializer, cordaAvroDeserializer),
        Schemas.Persistence.PERSISTENCE_LEDGER_PROCESSOR_TOPIC to RestClient("ledger.process", cordaAvroSerializer, cordaAvroDeserializer),
        Schemas.Persistence.PERSISTENCE_ENTITY_PROCESSOR_TOPIC to RestClient("db.process", cordaAvroSerializer, cordaAvroDeserializer),
        Schemas.UniquenessChecker.UNIQUENESS_CHECK_TOPIC to RestClient("uniqueness.process", cordaAvroSerializer, cordaAvroDeserializer),
        Schemas.Verification.VERIFICATION_LEDGER_PROCESSOR_TOPIC to RestClient("verification.process", cordaAvroSerializer, cordaAvroDeserializer)
    )

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
                nullableProducer = builder.createProducer(config) { data ->
                    log.warn("Failed to serialize record from ${config.topic}")
                    deadLetterRecords.add(data)
                }
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
                    },
                    cordaAvroSerializer,
                    cordaAvroDeserializer
                )
                nullableRebalanceListener = rebalanceListener
                val eventConsumerTmp = stateAndEventConsumerTmp.eventConsumer
                nullableStateAndEventConsumer = stateAndEventConsumerTmp
                nullableEventConsumer = eventConsumerTmp
                eventConsumerTmp.subscribe(eventTopic, rebalanceListener)
                threadLooper.updateLifecycleStatus(LifecycleStatus.UP)

                while (!threadLooper.loopStopped) {
                    stateAndEventConsumerTmp.pollAndUpdateStates(true)
                    processBatchOfEvents()
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

    private fun processBatchOfEvents() {
        var attempts = 0
        var keepProcessing = true
        while (keepProcessing && !threadLooper.loopStopped) {
            try {
                log.debug { "Polling and processing events" }
                var rebalanceOccurred = false
                val records = stateAndEventConsumer.pollEvents()
                batchSizeHistogram.record(records.size.toDouble())
                val batches = getEventsByBatch(records).iterator()
                while (!rebalanceOccurred && batches.hasNext()) {
                    val batch = batches.next()
                    rebalanceOccurred = tryProcessBatchOfEvents(batch)
                }
                keepProcessing = false // We only want to do one batch at a time
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

    /**
     * Process a batch of events from the last poll and publish the outputs (including DLQd events)
     *
     * @return false if the batch had to be abandoned due to a rebalance
     */
    private fun tryProcessBatchOfEvents(events: List<CordaConsumerRecord<K, E>>): Boolean {
        log.debug { "Processing events(keys: ${events.joinToString { it.key.toString() }}, size: ${events.size})" }
        val eventsToProcess = ArrayDeque(events)
        try {
            while (eventsToProcess.isNotEmpty()) {
                val event = eventsToProcess.removeFirst()
                processEvent(event)
                log.debug { "Processed event of key '${event.key}' successfully." }
            }
        } catch (ex: StateAndEventConsumer.RebalanceInProgressException) {
            log.warn ("Abandoning processing of events(keys: ${events.joinToString { it.key.toString() }}, " +
                    "size: ${events.size}) due to rebalance", ex)
            return true
        }
        return false
    }

    private fun toCordaConsumerRecord(sourceRecord: CordaConsumerRecord<K, E>, newEvent : Record<K, E>) =
        CordaConsumerRecord(
            newEvent.topic,
            sourceRecord.partition,
            sourceRecord.offset,
            newEvent.key,
            newEvent.value,
            sourceRecord.timestamp,
            sourceRecord.headers
        )

    private fun isWakeup(record: Record<*, *>) : Boolean {
        return if ((record.value != null) && record.value is FlowEvent) {
            val flowEvent = record.value!! as FlowEvent
            flowEvent.payload is Wakeup
        } else {
            false
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun processEvent(
        event: CordaConsumerRecord<K, E>,
    ) {
        val subsequentEvents = ArrayDeque(listOf(event))
        while (subsequentEvents.isNotEmpty()) {
            processorMeter.recordCallable {
                val currentEvent = subsequentEvents.removeFirst()
                stateAndEventConsumer.resetPollInterval()
                log.debug { "Processing event: $currentEvent" }
                try {
                    val state = stateAndEventConsumer.getInMemoryStateValue(currentEvent.key)
                    val currentEventUpdates = getUpdatesForEvent(state, currentEvent)
                    val updatedState = currentEventUpdates?.updatedState
                    // Get wake-up calls
                    val (wakeups, outputEvents) = currentEventUpdates?.responseEvents?.partition {
                        isWakeup(it)
                    } ?: Pair(emptyList(), emptyList())
                    // Get REST calls
                    val (overRestEvents, overKafkaEvents) = outputEvents.partition {
                        topicToRestClient.containsKey(it.topic)
                    }
                    // Execute REST calls
                    val responseEvents = overRestEvents.mapNotNull {
                        topicToRestClient[it.topic]?.publish(listOf(it))
                    }.flatten()
                    // Add subsequent events to be processed next in the queue
                    val eventsToProcess = (wakeups + responseEvents) as List<Record<K, E>> // + restResponses
                    subsequentEvents.addAll(eventsToProcess.map {
                        val ret = toCordaConsumerRecord(currentEvent, it)
                        ret
                    })
                    recordsAvoidedCount.increment(eventsToProcess.size.toDouble())

                    when {
                        currentEventUpdates == null -> {
                            log.warn(
                                "Sending state and event on key ${currentEvent.key} for topic ${currentEvent.topic} to dead letter queue. " +
                                        "Processor failed to complete."
                            )
                            producer.sendRecords(
                                listOf(
                                    generateDeadLetterRecord(
                                        currentEvent,
                                        state
                                    )
                                ).toCordaProducerRecords()
                            )
                            stateAndEventConsumer.updateInMemoryStatePostCommit(currentEvent.key, null, clock)
                        }

                        currentEventUpdates.markForDLQ -> {
                            log.warn(
                                "Sending state and event on key ${currentEvent.key} for topic ${currentEvent.topic} to dead letter queue. " +
                                        "Processor marked event for the dead letter queue"
                            )
                            producer.sendRecords(
                                listOf(
                                    generateDeadLetterRecord(
                                        currentEvent,
                                        state
                                    )
                                ).toCordaProducerRecords()
                            )
                            stateAndEventConsumer.updateInMemoryStatePostCommit(currentEvent.key, null, clock)

                            // In this case the processor may ask us to publish some output records regardless, so make sure these
                            // are outputted.
                            producer.sendRecords(overKafkaEvents.toCordaProducerRecords())
                        }

                        else -> {
                            producer.sendRecords(overKafkaEvents.toCordaProducerRecords())
                            stateAndEventConsumer.updateInMemoryStatePostCommit(currentEvent.key, updatedState, clock)
                            log.debug { "Completed event: $currentEvent" }
                        }
                    }
                } finally {
                    stateAndEventConsumer.releaseStateKey(currentEvent.key)
                }
            }
        }
    }

    private fun getUpdatesForEvent(state: S?, event: CordaConsumerRecord<K, E>): StateAndEventProcessor.Response<S>? {
        val future = stateAndEventConsumer.waitForFunctionToFinish(
            { processor.onNext(state, event.toRecord()) }, config.processorTimeout.toMillis(),
            "Failed to finish within the time limit for state: $state and event: $event"
        )
        @Suppress("unchecked_cast")
        return future.tryGetResult() as? StateAndEventProcessor.Response<S>
    }

    private fun generateDeadLetterRecord(event: CordaConsumerRecord<K, E>, state: S?): Record<*, *> {
        val keyBytes = ByteBuffer.wrap(cordaAvroSerializer.serialize(event.key))
        val stateBytes =
            if (state != null) ByteBuffer.wrap(cordaAvroSerializer.serialize(state)) else null
        val eventValue = event.value
        val eventBytes =
            if (eventValue != null) ByteBuffer.wrap(cordaAvroSerializer.serialize(eventValue)) else null
        return Record(
            getDLQTopic(eventTopic), event.key,
            StateAndEventDeadLetterRecord(clock.instant(), keyBytes, stateBytes, eventBytes)
        )
    }

    /**
     * Handle retries for event processing.
     * Reset [nullableEventConsumer] position and retry poll and process of eventRecords
     * Retry a max of [ResolvedSubscriptionConfig.processorRetries] times.
     * If [ResolvedSubscriptionConfig.processorRetries] is exceeded then throw a [CordaMessageAPIIntermittentException]
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
            stateAndEventConsumer.resetEventOffsetPosition()
        } else {
            val message = "Failed to process records from topic $eventTopic, group ${config.group}, " +
                    "producerClientId ${config.clientId}. " +
                    "Attempts: $attempts. Max reties exceeded."
            log.warn(message, ex)
            throw CordaMessageAPIIntermittentException(message, ex)
        }
    }
}
