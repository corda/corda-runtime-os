package net.corda.messaging.subscription

import com.google.common.util.concurrent.ThreadFactoryBuilder
import net.corda.avro.serialization.CordaAvroSerializer
import net.corda.data.deadletter.StateAndEventDeadLetterRecord
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleStatus
import net.corda.messagebus.api.consumer.CordaConsumer
import net.corda.messagebus.api.consumer.CordaConsumerRecord
import net.corda.messagebus.api.producer.CordaProducer
import net.corda.messagebus.api.producer.CordaProducerRecord
import net.corda.messaging.api.chunking.ChunkSerializerService
import net.corda.messaging.api.exception.CordaMessageAPIFatalException
import net.corda.messaging.api.exception.CordaMessageAPIIntermittentException
import net.corda.messaging.api.processor.StateAndEventProcessor
import net.corda.messaging.api.records.Record
import net.corda.messaging.api.subscription.StateAndEventSubscription
import net.corda.messaging.api.subscription.listener.StateAndEventListener
import net.corda.messaging.config.ResolvedSubscriptionConfig
import net.corda.messaging.constants.MetricsConstants
import net.corda.messaging.subscription.consumer.StateAndEventConsumer
import net.corda.messaging.subscription.consumer.builder.StateAndEventBuilder
import net.corda.messaging.subscription.consumer.listener.StateAndEventConsumerRebalanceListener
import net.corda.messaging.utils.getEventsByBatch
import net.corda.messaging.utils.toCordaProducerRecords
import net.corda.messaging.utils.toRecord
import net.corda.messaging.utils.tryGetResult
import net.corda.metrics.CordaMetrics
import net.corda.schema.Schemas.getDLQTopic
import net.corda.schema.Schemas.getStateAndEventStateTopic
import net.corda.utilities.debug
import org.slf4j.LoggerFactory
import java.nio.ByteBuffer
import java.time.Clock
import java.time.Duration
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

@Suppress("LongParameterList")
internal class FlowStateAndEventSubscriptionImpl<K : Any, S : Any, E : Any>(
    private val config: ResolvedSubscriptionConfig,
    private val builder: StateAndEventBuilder,
    private val processor: StateAndEventProcessor<K, S, E>,
    private val cordaAvroSerializer: CordaAvroSerializer<Any>,
    lifecycleCoordinatorFactory: LifecycleCoordinatorFactory,
    private val chunkSerializerService: ChunkSerializerService,
    private val stateAndEventListener: StateAndEventListener<K, S>? = null,
    private val clock: Clock = Clock.systemUTC(),
) : StateAndEventSubscription<K, S, E> {

    private val log = LoggerFactory.getLogger("${this.javaClass.name}-${config.clientId}")

    private val processingThreads = ThreadPoolExecutor(
        8,
        8,
        0L,
        TimeUnit.MILLISECONDS,
        LinkedBlockingQueue(),
        ThreadFactoryBuilder().setNameFormat("state-and-event-processing-thread-%d").setDaemon(false).build()
    )

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

    private val processorMeter = CordaMetrics.Metric.Messaging.MessageProcessorTime.builder()
        .withTag(CordaMetrics.Tag.MessagePatternType, MetricsConstants.STATE_AND_EVENT_PATTERN_TYPE)
        .withTag(CordaMetrics.Tag.MessagePatternClientId, config.clientId)
        .withTag(CordaMetrics.Tag.OperationName, MetricsConstants.ON_NEXT_OPERATION)
        .build()

    private val commitTimer = CordaMetrics.Metric.Messaging.MessageCommitTime.builder()
        .withTag(CordaMetrics.Tag.MessagePatternType, MetricsConstants.STATE_AND_EVENT_PATTERN_TYPE)
        .withTag(CordaMetrics.Tag.MessagePatternClientId, config.clientId)
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
        val outputRecords = mutableListOf<Record<*, *>>()
//        val updatedStates: MutableMap<Int, MutableMap<K, S?>> = ConcurrentHashMap()
        // maps shouldn't need to be concurrent since we never clash on keys
        // seems that you can;t have null in concurrent map so gone back to mutable map
        val updatedStates: MutableMap<Int, MutableMap<K, S?>> = mutableMapOf()
        // Pre-populate the updated states with the current in-memory state.
        events.forEach {
//            val partitionMap = updatedStates.computeIfAbsent(it.partition) { ConcurrentHashMap() }
            val partitionMap = updatedStates.computeIfAbsent(it.partition) { mutableMapOf() }
            partitionMap.computeIfAbsent(it.key) { key ->
                stateAndEventConsumer.getInMemoryStateValue(key)
            }
        }

        log.debug { "Processing events(keys: ${events.joinToString { it.key.toString() }}, size: ${events.size})" }
        try {
            processorMeter.recordCallable {
                stateAndEventConsumer.resetPollInterval()
                processEvents(
                    groupedEvents = events.groupBy { event -> event.key },
                    outputRecords,
                    updatedStates
                )
            }
        } catch (ex: StateAndEventConsumer.RebalanceInProgressException) {
            log.warn ("Abandoning processing of events(keys: ${events.joinToString { it.key.toString() }}, " +
                    "size: ${events.size}) due to rebalance", ex)
            return true
        }

        commitTimer.recordCallable {
            producer.beginTransaction()
            producer.sendRecords(outputRecords.toCordaProducerRecords())
            if (deadLetterRecords.isNotEmpty()) {
                producer.sendRecords(deadLetterRecords.map {
                    CordaProducerRecord(
                        getDLQTopic(eventTopic),
                        UUID.randomUUID().toString(),
                        it
                    )
                })
                deadLetterRecords.clear()
            }
            producer.sendRecordOffsetsToTransaction(eventConsumer, events)
            producer.commitTransaction()
        }
        log.debug { "Processing events(keys: ${events.joinToString { it.key.toString() }}, size: ${events.size}) complete." }

        stateAndEventConsumer.updateInMemoryStatePostCommit(updatedStates, clock)
        return false
    }

    data class GroupedEventData<E, K, S: Any>(
        val key: K,
        val state: S?,
        val topic: String,
        val partitionId: Int,
        val future: CompletableFuture<List<Pair<CordaConsumerRecord<K, E>, StateAndEventProcessor.Response<S>?>>>
    )

    private fun processEvents(
        groupedEvents: Map<K, List<CordaConsumerRecord<K, E>>>,
        outputRecords: MutableList<Record<*, *>>,
        updatedStates: MutableMap<Int, MutableMap<K, S?>>
    ) {
        val future = stateAndEventConsumer.waitForFunctionToFinish(
            {
                val startTime = System.nanoTime()
                val groupedEventDatas: List<GroupedEventData<E, K, S>> = groupedEvents.map { (key, events) ->
                    log.debug { "Processing events: $events" }
                    val partitionId = events.first().partition
                    val state = updatedStates[partitionId]?.get(key)
                    GroupedEventData(
                        key,
                        state,
                        events.first().topic,
                        partitionId,
                        CompletableFuture.supplyAsync(
                            {
                                log.error("PROCESSING ON NEXT for key $key")
                                events.map { event ->
                                    event to processor.onNext(state, event.toRecord())
                                }
                            },
                            processingThreads
                        )
                    )
                }
                // we might want to put a timeout somewhere
                groupedEventDatas.forEach { it.future.join() }

                log.error("COMPLETED FUTURES: ${groupedEventDatas.size} IN TIME: ${Duration.ofNanos(System.nanoTime() - startTime)}")

                groupedEventDatas.map { (key, state, topic, partitionId, future) ->
                    val eventUpdatesFromFuture: List<Pair<CordaConsumerRecord<K, E>, StateAndEventProcessor.Response<S>?>> = future.get()

                    eventUpdatesFromFuture.map { (event, thisEventUpdates) ->
                        val updatedState = thisEventUpdates?.updatedState
                        when {
                            thisEventUpdates == null -> {
                                log.warn(
                                    "Sending state and event on key $key for topic $topic to dead letter queue. " +
                                            "Processor failed to complete."
                                )
                                generateChunkKeyCleanupRecords(key, state, null, outputRecords)
                                outputRecords.add(generateDeadLetterRecord(event, state))
                                outputRecords.add(Record(stateTopic, key, null))
                                updatedStates.computeIfAbsent(partitionId) { mutableMapOf() }[key] = null
                            }

                            thisEventUpdates.markForDLQ -> {
                                log.warn(
                                    "Sending state and event on key ${event.key} for topic ${event.topic} to dead letter queue. " +
                                            "Processor marked event for the dead letter queue"
                                )
                                generateChunkKeyCleanupRecords(key, state, null, outputRecords)
                                outputRecords.add(generateDeadLetterRecord(event, state))
                                outputRecords.add(Record(stateTopic, key, null))
                                updatedStates.computeIfAbsent(partitionId) { mutableMapOf() }[key] = null

                                // In this case the processor may ask us to publish some output records regardless, so make sure these
                                // are outputted.
                                outputRecords.addAll(thisEventUpdates.responseEvents)
                            }

                            else -> {
                                generateChunkKeyCleanupRecords(key, state, updatedState, outputRecords)
                                outputRecords.addAll(thisEventUpdates.responseEvents)
                                outputRecords.add(Record(stateTopic, key, updatedState))
                                updatedStates.computeIfAbsent(partitionId) { mutableMapOf() }[key] = updatedState
                                log.debug { "Completed event: $event" }
                            }
                        }
                    }
                }
            },
            maxTimeout = config.processorTimeout.toMillis(),
            timeoutErrorMessage = "Failed to finish within the time limit for events with keys ${groupedEvents.keys}"
        )

        future.tryGetResult()
    }

    /**
     * If the new state requires old chunk keys to be cleared then generate cleanup records to set those ChunkKeys to null
     */
    private fun generateChunkKeyCleanupRecords(key: K, state: S?, updatedState: S?, outputRecords: MutableList<Record<*, *>>) {
        chunkSerializerService.getChunkKeysToClear(key, state, updatedState)?.let { chunkKeys ->
            chunkKeys.map { chunkKey ->
                outputRecords.add(Record(stateTopic, chunkKey, null))
            }
        }
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
