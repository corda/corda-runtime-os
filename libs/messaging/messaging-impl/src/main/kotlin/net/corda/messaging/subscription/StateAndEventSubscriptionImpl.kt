package net.corda.messaging.subscription

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
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
import net.corda.messaging.utils.getEventsByBatch
import net.corda.messaging.utils.toCordaProducerRecords
import net.corda.messaging.utils.toRecord
import net.corda.metrics.CordaMetrics
import net.corda.schema.Schemas.getDLQTopic
import net.corda.schema.Schemas.getStateAndEventStateTopic
import net.corda.utilities.debug
import org.slf4j.LoggerFactory
import java.nio.ByteBuffer
import java.time.Clock
import java.util.concurrent.Executors

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

    // @@@ This producer is only used to get stuff, not for producing
    private val utilityProducer: CordaProducer
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

    private val errorMsg =
        "Failed to read and process records from topic $eventTopic, group ${config.group}, " + "producerClientId ${config.clientId}."

    private val processorMeter = CordaMetrics.Metric.MessageProcessorTime.builder()
        .withTag(CordaMetrics.Tag.MessagePatternType, MetricsConstants.STATE_AND_EVENT_PATTERN_TYPE)
        .withTag(CordaMetrics.Tag.MessagePatternClientId, config.clientId)
        .withTag(CordaMetrics.Tag.OperationName, MetricsConstants.BATCH_PROCESS_OPERATION).build()

    private val batchSizeHistogram = CordaMetrics.Metric.MessageBatchSize.builder()
        .withTag(CordaMetrics.Tag.MessagePatternType, MetricsConstants.STATE_AND_EVENT_PATTERN_TYPE)
        .withTag(CordaMetrics.Tag.MessagePatternClientId, config.clientId).build()

    private val commitTimer = CordaMetrics.Metric.MessageCommitTime.builder()
        .withTag(CordaMetrics.Tag.MessagePatternType, MetricsConstants.STATE_AND_EVENT_PATTERN_TYPE)
        .withTag(CordaMetrics.Tag.MessagePatternClientId, config.clientId).build()

    private data class ProducerData(
        val offsets: CordaProducer.Offsets,
        val metaData: CordaProducer.Metadata,
        val outgoingRecords: List<CordaProducerRecord<*, *>>
    )

    private data class ProcessorData<K, E>(
        val metaData: CordaProducer.Metadata,
        val incomingRecords: List<CordaConsumerRecord<K, E>>
    )


    private val producerChannels = mutableMapOf<Int, Channel<ProducerData>>()
    private val processorChannels = mutableMapOf<Int, Channel<ProcessorData<K, E>>>()

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

        /*
         * This is a retry loop. Its purpose is to keep the subscription going if the polling/processing/producing part
         * throws an intermittent exception, that is an exception which indicates there is nothing fundamentally wrong
         * just that a problem occurred this time and perhaps would be fine on a retry. Corda regards most runtime errors
         * as intermittent, even ones which render the consumer instance unusable, and as such this loop handles recreating
         * the consumer to try again. Non-intermittent errors tends to be related to e.g. detecting a bad Kafka deployment.
         */
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
                    })
                nullableRebalanceListener = rebalanceListener
                val eventConsumerTmp = stateAndEventConsumerTmp.eventConsumer
                nullableStateAndEventConsumer = stateAndEventConsumerTmp
                nullableEventConsumer = eventConsumerTmp
                eventConsumerTmp.subscribe(eventTopic, rebalanceListener)
                threadLooper.updateLifecycleStatus(LifecycleStatus.UP)

                // Run the main poll loop continuously whilst there are no errors
                pollAndProcess(stateAndEventConsumerTmp)

            } catch (ex: Exception) {
                when (ex) {
                    is CordaMessageAPIIntermittentException -> {
                        log.warn(
                            "$errorMsg Attempts: $attempts. Recreating " + "consumer/producer and Retrying.", ex
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

    private fun pollAndProcess(stateAndEventConsumer: StateAndEventConsumer<K, S, E>) {
        runBlocking {
            val jobs = mutableListOf<Job>()

            /*
             * This is the main polling loop. The subscription's thread will sit in here forever (unless some Kafka error
             * is generated), polling, firing off requests to process events to an async processor.
             */
            while (!threadLooper.loopStopped) {
                stateAndEventConsumer.pollAndUpdateStates(true)
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
                            try {
                                /*
                                 * Batches of events are processed and the output published in buckets of the same partition.
                                 * This is because the producer writes the consumer offsets as well as any output records
                                 * from the processing. If we didn't batch by partition, a processor on a particular
                                 * partition processing a later event could beat another processor on the same partition
                                 * processing an older event, and the offsets would then be written in the wrong order.
                                 * This is a means to keep consumer offset processing order the same whilst fanning out
                                 * event processing in parallel in other words.
                                 */
                                val bucketedEventsByPartition = batches.next().groupBy({ it.partition }, { it })

                                for (partition in bucketedEventsByPartition.keys) {
                                    // Create the producer coroutines for any partition we've not yet encountered
                                    producerChannels.computeIfAbsent(partition) {
                                        val newConfig = config.copy(uniqueId = "${config.uniqueId}-${partition}")
                                        Channel<ProducerData>(1).also { channel ->
                                            launchProducer(channel, newConfig).also { job ->
                                                jobs.add(job)
                                            }
                                        }
                                    }

                                    // Create the processor coroutines for any partition we've not yet encountered
                                    val processorChannelForCurrentPartition = processorChannels.computeIfAbsent(partition) {
                                        Channel<ProcessorData<K, E>>(1).also { channel ->
                                            // A producer channel should always already exist at this point
                                            val producerChannel = checkNotNull(producerChannels[partition])
                                            // Launch processor, effectively binding the processor and producer channels
                                            // to create a pipeline of streamed event processing and output publishing.
                                            launchProcessor(
                                                processorChannel = channel, producerChannel = producerChannel
                                            ).also { job ->
                                                jobs.add(job)
                                            }
                                        }
                                    }

                                    val eventsForThisPartition = checkNotNull(bucketedEventsByPartition[partition])
                                    // Get the metadata from the consumer in the only thread it's safe to do so
                                    val metaData = utilityProducer.getMetadata(eventConsumer)
                                    /*
                                     * Send current bucket of events to the appropriate processor channel to kick of processing
                                     * asynchronously. Whilst processing is async, the attempt we make to add to the channel is
                                     * synchronous, such that we can reset the poll interval whilst we are waiting to add it.
                                     * If we blocked here, Kafka could eventually consider us a candidate for fencing as we'd
                                     * appear to be unresponsive to it.
                                     */
                                    var logged: Boolean = false
                                    while (processorChannelForCurrentPartition.trySend(ProcessorData(metaData, eventsForThisPartition)).isFailure) {
                                        if (!logged) {
                                            log.info("@@@ Processor queue full, suspending briefly before retry")
                                            logged = true
                                        }
                                        delay(10) // ms
                                        // resetPollInterval is cheap if we haven't exceeded the poll interval time
                                        stateAndEventConsumer.resetPollInterval()
                                    }
                                }
                            } catch (ex: StateAndEventConsumer.RebalanceInProgressException) {
//                                log.info(
//                                    "Abandoning processing of events(keys: ${eventsForThisPartition.joinToString { it.key.toString() }}, " +
//                                            "size: ${eventsForThisPartition.size}) due to rebalance", ex
//                                )
                                log.info("@@@ REBALANCE")
                                // @@@ need to check the rebalance logic here, this will drop up out of the current polling loop
                                // for another go at pollAndUpdateStates, is that enough or do we need to reset the consumer some
                                // other way (e.g. stateAndEventConsumer.resetEventOffsetPosition())?

                                // @@@ - possible bug here
                                // Some processing and producing events will already be in the channels and in some time
                                // in the future end up writing records and consumer offsets. When we restart as a
                                // result of this rebalance, those events could be processed again
                                rebalanceOccurred = true
                            }
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
                                    "Failed to process records from topic $eventTopic, group ${config.group}, " + "producerClientId ${config.clientId}. " + "Fatal error occurred.",
                                    ex
                                )
                            }
                        }
                    }
                }
            }
            log.info("@@@ cancelling job")
            jobs.forEach {
                it.cancel()
            }
        }
    }

    private fun closeStateAndEventProducerConsumer() {
        nullableProducer?.close()
        nullableStateAndEventConsumer?.close()
        nullableProducer = null
        nullableStateAndEventConsumer = null
    }

    // @@@ exception handling in the coroutines to consider

    private fun CoroutineScope.launchProcessor(
        producerChannel: Channel<ProducerData>, processorChannel: Channel<ProcessorData<K, E>>
    ) = launch(Executors.newSingleThreadExecutor().asCoroutineDispatcher()) {
        log.info("@@@ creating processor")
        for (processorData in processorChannel) {
            log.info("@@@ processing")
            val outputRecords = mutableListOf<Record<*, *>>()
            var lastEvent: CordaConsumerRecord<K, E>? = null
            val events = processorData.incomingRecords

            val updatedStates: MutableMap<Int, MutableMap<K, S?>> = mutableMapOf()
            // Pre-populate the updated states with the current in-memory state.
            events.forEach {
                val partitionMap = updatedStates.computeIfAbsent(it.partition) { mutableMapOf() }
                partitionMap.computeIfAbsent(it.key) { key ->
                    // @@@ this is probably not thread safe
                    stateAndEventConsumer.getInMemoryStateValue(key)
                }
            }

            log.debug { "Processing events(keys: ${events.joinToString { it.key.toString() }}, size: ${events.size})" }

            processorMeter.recordCallable {
                for (event in events) {
                    processEvent(event, outputRecords, updatedStates)
                    // And the last event
                    lastEvent = event
                }
            }

            // If we processed something, send the output records and offsets to the producer channel
            lastEvent?.let {
                val producerData = ProducerData(
                    utilityProducer.getOffsets(listOf(it)),
                    processorData.metaData,
                    outputRecords.toCordaProducerRecords()
                )
                producerChannel.send(producerData)
            }

            // @@@ this is probably not thread safe
            stateAndEventConsumer.updateInMemoryStatePostCommit(updatedStates, clock)

            log.debug { "Processing events(keys: ${events.joinToString { it.key.toString() }}, size: ${events.size}) complete." }
        }
    }

    private fun CoroutineScope.launchProducer(
        producerChannel: Channel<ProducerData>, config: ResolvedSubscriptionConfig
    ) = launch(Executors.newSingleThreadExecutor().asCoroutineDispatcher()) {
        log.info("@@@ creating producer")
        val producer = builder.createProducer(config)
        for (producerData in producerChannel) {
            log.info("@@@ producing")
            producer.beginTransaction()
            producer.sendRecords(producerData.outgoingRecords)
            // @@@ no DLQ support at present
            //            if (deadLetterRecords.isNotEmpty()) {
            //                producer.sendRecords(deadLetterRecords.map {
            //                    CordaProducerRecord(
            //                        getDLQTopic(eventTopic),
            //                        UUID.randomUUID().toString(),
            //                        it
            //                    )
            //                })
            //                deadLetterRecords.clear()
            //            }
            producer.sendRecordOffsetsToTransaction(producerData.offsets, producerData.metaData)
            producer.commitTransaction()
        }
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
                    "Sending state and event on key ${event.key} for topic ${event.topic} to dead letter queue. " + "Processor failed to complete."
                )
                generateChunkKeyCleanupRecords(key, state, null, outputRecords)
                outputRecords.add(generateDeadLetterRecord(event, state))
                outputRecords.add(Record(stateTopic, key, null))
                updatedStates.computeIfAbsent(partitionId) { mutableMapOf() }[key] = null
            }

            thisEventUpdates.markForDLQ -> {
                log.warn(
                    "Sending state and event on key ${event.key} for topic ${event.topic} to dead letter queue. " + "Processor marked event for the dead letter queue"
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
    private fun generateChunkKeyCleanupRecords(
        key: K, state: S?, updatedState: S?, outputRecords: MutableList<Record<*, *>>
    ) {
        chunkSerializerService.getChunkKeysToClear(key, state, updatedState)?.let { chunkKeys ->
            chunkKeys.map { chunkKey ->
                outputRecords.add(Record(stateTopic, chunkKey, null))
            }
        }
    }

    private fun getUpdatesForEvent(
        state: S?, event: CordaConsumerRecord<K, E>
    ): StateAndEventProcessor.Response<S>? {
        return processor.onNext(state, event.toRecord())

    // @@@ get rid of all the poll interval anti-fencing stuff
//        val future = stateAndEventConsumer.waitForFunctionToFinish(
//            { processor.onNext(state, event.toRecord()) },
//            config.processorTimeout.toMillis(),
//            "Failed to finish within the time limit for state: $state and event: $event"
//        )
//        @Suppress("unchecked_cast") return future.tryGetResult() as? StateAndEventProcessor.Response<S>
    }

    private fun generateDeadLetterRecord(event: CordaConsumerRecord<K, E>, state: S?): Record<*, *> {
        val keyBytes = ByteBuffer.wrap(cordaAvroSerializer.serialize(event.key))
        val stateBytes = if (state != null) ByteBuffer.wrap(cordaAvroSerializer.serialize(state)) else null
        val eventValue = event.value
        val eventBytes = if (eventValue != null) ByteBuffer.wrap(cordaAvroSerializer.serialize(eventValue)) else null
        return Record(
            getDLQTopic(eventTopic),
            event.key,
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
        attempts: Int, ex: Exception
    ) {
        if (attempts <= config.processorRetries) {
            log.warn(
                "Failed to process record from topic $eventTopic, group ${config.group}, " + "producerClientId ${config.clientId}. " + "Retrying poll and process. Attempts: $attempts."
            )
            stateAndEventConsumer.resetEventOffsetPosition()
        } else {
            val message =
                "Failed to process records from topic $eventTopic, group ${config.group}, " + "producerClientId ${config.clientId}. " + "Attempts: $attempts. Max reties exceeded."
            log.warn(message, ex)
            throw CordaMessageAPIIntermittentException(message, ex)
        }
    }
}
