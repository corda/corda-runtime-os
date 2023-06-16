package net.corda.messaging.subscription

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
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
import java.util.*

@Suppress("LongParameterList")
internal class StateAndEventSubscriptionImpl<K : Any, S : Any, E : Any>(
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
        .withTag(CordaMetrics.Tag.OperationName, MetricsConstants.ON_NEXT_OPERATION)
        .build()

    private val batchSizeHistogram = CordaMetrics.Metric.MessageBatchSize.builder()
        .withTag(CordaMetrics.Tag.MessagePatternType, MetricsConstants.STATE_AND_EVENT_PATTERN_TYPE)
        .withTag(CordaMetrics.Tag.MessagePatternClientId, config.clientId)
        .build()

    private val commitTimer = CordaMetrics.Metric.MessageCommitTime.builder()
        .withTag(CordaMetrics.Tag.MessagePatternType, MetricsConstants.STATE_AND_EVENT_PATTERN_TYPE)
        .withTag(CordaMetrics.Tag.MessagePatternClientId, config.clientId)
        .build()

    // Pipeline POC
    data class StateAndEventData<K: Any, S: Any, E: Any>(val outputRecords: List<CordaProducerRecord<*, *>>, val event: CordaConsumerRecord<K, E>)
    // channels used for messages to process
    // capacity of the channel buffer TBD - producing function will suspend when buffer full
    // number of channels should be configurable and indicate level of parallelism
    val processingChannels = (1..5).map { Channel<CordaConsumerRecord<K, E>>(100) }

    // channel used for messages that need sending - buffer TBD
    val sendingChannel = Channel<StateAndEventData<K, S, E>>(1000)

    private fun CoroutineScope.messageFanOut(events: List<CordaConsumerRecord<K, E>>) = launch {
        // use hash of msg key to define which channel this goes into to avoid messages with the same ID going out-of-order
        for(event in events) {
            val channelId = event.key.hashCode().mod(processingChannels.size)
            processingChannels[channelId].send(event)
        }
    }

    private fun CoroutineScope.messageProcessor(id: Int, events: ReceiveChannel<CordaConsumerRecord<K, E>>) = launch {
        for (event in events) {
            log.info("Processor #$id is processing ${event.key} on ${Thread.currentThread().name}")

            // simplistic interpretation
            val key = event.key
            val state = stateAndEventConsumer.getInMemoryStateValue(key)
            val thisEventUpdates = getUpdatesForEvent(state, event)
            if(thisEventUpdates == null) {
                log.warn("TODO: handling null event updates")
                continue
            }
            if(thisEventUpdates.markForDLQ) {
                log.warn("TODO: handling DLQ")
                continue
            }
            val updatedState = thisEventUpdates.updatedState
            val outputRecords = mutableListOf<Record<*, *>>()
            outputRecords.addAll(thisEventUpdates.responseEvents)
            outputRecords.add(Record(stateTopic, key, updatedState))

            sendingChannel.send(StateAndEventData(outputRecords.toCordaProducerRecords(), event))
        }
    }

    private fun CoroutineScope.messageSender(msgs: Channel<StateAndEventData<K, S, E>>) = launch {
        var beginTx = 0L
        var txStarted = false
        var msgsInCommit = 0
        val eventsSinceCommit = mutableListOf<CordaConsumerRecord<*,*>>()
        val updatedStates: MutableMap<Int, MutableMap<K, S?>> = mutableMapOf()

        fun commitTx() {
            log.info("Commit tx - $msgsInCommit since last commit")
            producer.sendRecordOffsetsToTransaction(eventConsumer, eventsSinceCommit)
            producer.commitTransaction()
            txStarted = false
            msgsInCommit = 0
            stateAndEventConsumer.updateInMemoryStatePostCommit(updatedStates, clock)
        }

        for (msg in msgs) {
            if(!txStarted) {
                log.info("Begin tx")
                beginTx = System.nanoTime()
                txStarted = true
                producer.beginTransaction()
            }
            else if(System.nanoTime() - beginTx > 1_000_000 * 50) commitTx()
            msgsInCommit += msg.outputRecords.size
            producer.sendRecords(msg.outputRecords)
            eventsSinceCommit.add(msg.event)
            val state = updatedStates[msg.event.partition]?.get(msg.event.key)
            val thisEventUpdates = getUpdatesForEvent(state, msg.event)
            val updatedState = thisEventUpdates?.updatedState
            updatedStates.computeIfAbsent(msg.event.partition) { mutableMapOf() }[msg.event.key] = updatedState
        }
        if(txStarted) commitTx()
    }
    // ...

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
            // TODO - not sure runblocking here is correct.
            runBlocking {
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

                    messageSender(sendingChannel)
                    for(x in processingChannels.indices) {
                        messageProcessor(x, processingChannels[x])
                    }

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
                    coroutineContext.cancelChildren()
                }
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

    private fun CoroutineScope.processBatchOfEvents() {
        var attempts = 0
        var keepProcessing = true
        while (keepProcessing && !threadLooper.loopStopped) {
            try {
                log.debug { "Polling and processing events" }
                var rebalanceOccurred = false
                val records = stateAndEventConsumer.pollEvents()
                batchSizeHistogram.record(records.size.toDouble())
//                val batches = getEventsByBatch(records).iterator()
//                while (!rebalanceOccurred && batches.hasNext()) {
//                    val batch = batches.next()
//                    rebalanceOccurred = tryProcessBatchOfEvents(batch)
//                }
                while(!rebalanceOccurred) {
                        messageFanOut(records)
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
        val updatedStates: MutableMap<Int, MutableMap<K, S?>> = mutableMapOf()
        // Pre-populate the updated states with the current in-memory state.
        events.forEach {
            val partitionMap = updatedStates.computeIfAbsent(it.partition) { mutableMapOf() }
            partitionMap.computeIfAbsent(it.key) { key ->
                stateAndEventConsumer.getInMemoryStateValue(key)
            }
        }

        log.debug { "Processing events(keys: ${events.joinToString { it.key.toString() }}, size: ${events.size})" }
        try {
            processorMeter.recordCallable {
                for (event in events) {
                    stateAndEventConsumer.resetPollInterval()
                    processEvent(event, outputRecords, updatedStates)
                }
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

    private fun processEvent(
        event: CordaConsumerRecord<K, E>,
        outputRecords: MutableList<Record<*, *>>,
        updatedStates: MutableMap<Int, MutableMap<K, S?>>
    ) {
        log.debug { "Processing event: $event" }
        val key = event.key
        val state = updatedStates[event.partition]?.get(event.key)
        val partitionId = event.partition
        val thisEventUpdates = getUpdatesForEvent(state, event)
        val updatedState = thisEventUpdates?.updatedState


        when {
            thisEventUpdates == null -> {
                log.warn(
                    "Sending state and event on key ${event.key} for topic ${event.topic} to dead letter queue. " +
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
