package net.corda.messaging.subscription

import net.corda.avro.serialization.CordaAvroSerializer
import net.corda.data.deadletter.StateAndEventDeadLetterRecord
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleStatus
import net.corda.messagebus.api.CordaTopicPartition
import net.corda.messagebus.api.configuration.ConsumerConfig
import net.corda.messagebus.api.configuration.ProducerConfig
import net.corda.messagebus.api.constants.ConsumerRoles
import net.corda.messagebus.api.constants.ProducerRoles
import net.corda.messagebus.api.consumer.CordaConsumerRebalanceListener
import net.corda.messagebus.api.consumer.CordaConsumerRecord
import net.corda.messagebus.api.processor.CordaProcessor
import net.corda.messagebus.api.processor.builder.CordaProcessorBuilder
import net.corda.messagebus.api.producer.CordaProducerRecord
import net.corda.messaging.api.chunking.ChunkSerializerService
import net.corda.messaging.api.processor.StateAndEventProcessor
import net.corda.messaging.api.records.Record
import net.corda.messaging.api.subscription.StateAndEventSubscription
import net.corda.messaging.config.ResolvedSubscriptionConfig
import net.corda.messaging.utils.toRecord
import net.corda.messaging.utils.tryGetResult
import net.corda.schema.Schemas.getDLQTopic
import net.corda.schema.Schemas.getStateAndEventStateTopic
import net.corda.tracing.wrapWithTracingExecutor
import net.corda.utilities.debug
import org.slf4j.LoggerFactory
import java.nio.ByteBuffer
import java.time.Clock
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit


@Suppress("LongParameterList", "TooManyFunctions")
internal class ParallelStateAndEventSubscriptionImpl<K : Any, S : Any, E : Any>(
    private val config: ResolvedSubscriptionConfig,
    private val builder: CordaProcessorBuilder,
    private val processor: StateAndEventProcessor<K, S, E>,
    private val cordaAvroSerializer: CordaAvroSerializer<Any>,
    lifecycleCoordinatorFactory: LifecycleCoordinatorFactory,
    private val chunkSerializerService: ChunkSerializerService,
    private val clock: Clock = Clock.systemUTC(),
) : StateAndEventSubscription<K, S, E>, CordaConsumerRebalanceListener {

    private val log = LoggerFactory.getLogger("${this.javaClass.name}-${config.clientId}")

    val states = mutableMapOf<Int, MutableMap<K, S>>()
    private var eventProcessor: CordaProcessor<K, E>? = null
    private val eventTopic = config.topic
    private val stateTopic = getStateAndEventStateTopic(config.topic)
    private lateinit var deadLetterRecords: MutableList<ByteArray>

    private val maxPollInterval = config.processorTimeout.toMillis()
    private val initialProcessorTimeout = maxPollInterval / 4
    private val lifecycleCoordinator =
        lifecycleCoordinatorFactory.createCoordinator(config.lifecycleCoordinatorName) { _, _ -> }

    private val errorMsg = "Failed to read and process records from topic $eventTopic, group ${config.group}, " +
            "producerClientId ${config.clientId}."

    override val subscriptionName: LifecycleCoordinatorName
        get() = config.lifecycleCoordinatorName

    override fun start() {
        log.debug { "Starting subscription with config: $config" }
        lifecycleCoordinator.start()
        lifecycleCoordinator.updateStatus(LifecycleStatus.UP)
        processEvents()
    }

    private fun createEventProcessor(): CordaProcessor<K, E> {
        val eventConsumerConfig =
            ConsumerConfig(config.group, "${config.clientId}-eventConsumer", ConsumerRoles.SAE_EVENT)
        val producerConfig = ProducerConfig(config.clientId, config.instanceId, true, ProducerRoles.SAE_PRODUCER, false)
        return builder.createProcessor(
            eventConsumerConfig,
            producerConfig,
            config.messageBusConfig,
            processor.keyClass,
            processor.eventValueClass,
            CordaProcessorBuilder.ProcessingOrder.KEY,
            { data ->
                log.error("Failed to deserialize event record from $eventTopic")
                deadLetterRecords.add(data)
            }
        )
    }

    private fun createStateProcessor(): CordaProcessor<K, S> {
        val stateConsumerConfig =
            ConsumerConfig(config.group, "${config.clientId}-stateConsumer", ConsumerRoles.SAE_STATE)
        return builder.createProcessor(
            stateConsumerConfig,
            config.messageBusConfig,
            processor.keyClass,
            processor.stateValueClass,
            CordaProcessorBuilder.ProcessingOrder.PARTITION,
            { data ->
                log.error("Failed to deserialize state record from $stateTopic")
                deadLetterRecords.add(data)
            }
        )
    }

    private fun getState(event: CordaConsumerRecord<K, E>) =
        states[event.partition]?.get(event.key)


    private fun updateState(record: CordaConsumerRecord<K, S>) = with (record) {
        updateState(partition, key, value)
    }

    private fun updateState(partition: Int, key: K, state: S?) {
        val partitionStates = states.computeIfAbsent(partition) { mutableMapOf() }
        state?.let {
            partitionStates[key] = it
        } ?: partitionStates.remove(key)
    }

    private fun processEvents() {
        val processor = createEventProcessor()
        log.info("Subscribing to topic $eventTopic")
        processor.subscribe(eventTopic, this)
        log.info("Polling topic $eventTopic")
        processor.pollAndProduceMany { event ->
            log.info("Processing event: $event")
            val key = event.key
            val state = getState(event)
            val partitionId = event.partition
            val thisEventUpdates = getUpdatesForEvent(state, event)
            val updatedState = thisEventUpdates?.updatedState
            val outputRecords = mutableListOf<Record<*, *>>()

            when {
                thisEventUpdates == null -> {
                    log.warn(
                        "Sending state and event on key ${event.key} for topic ${event.topic} to dead letter queue. " +
                                "Processor failed to complete."
                    )
                    generateChunkKeyCleanupRecords(key, state, null, outputRecords)
                    outputRecords.add(generateDeadLetterRecord(event, state))
                    outputRecords.add(Record(stateTopic, key, null))
                    updateState(partitionId, key, null)
                }

                thisEventUpdates.markForDLQ -> {
                    log.warn(
                        "Sending state and event on key ${event.key} for topic ${event.topic} to dead letter queue. " +
                                "Processor marked event for the dead letter queue"
                    )
                    outputRecords.add(generateDeadLetterRecord(event, state))
                    outputRecords.add(Record(stateTopic, key, null))
                    updateState(partitionId, key, null)

                    // In this case the processor may ask us to publish some output records regardless, so make sure these
                    // are outputted.
                    outputRecords.addAll(thisEventUpdates.responseEvents)
                }

                else -> {
                    generateChunkKeyCleanupRecords(key, state, updatedState, outputRecords)
                    outputRecords.addAll(thisEventUpdates.responseEvents)
                    outputRecords.add(Record(stateTopic, key, updatedState))
                    updateState(partitionId, key, updatedState)
                    log.debug { "Completed event: $event" }
                }
            }
            log.info("Output records from topic $eventTopic:\n $outputRecords")
            outputRecords.map { CordaProducerRecord(it.topic, it.key, it.value, it.headers) }
        }
        eventProcessor = processor
    }

    override fun onPartitionsRevoked(partitions: Collection<CordaTopicPartition>) {
        log.info("onPartitionsRevoked: $partitions")
        partitions.map(CordaTopicPartition::partition).forEach(states::remove)
    }

    override fun onPartitionsAssigned(partitions: Collection<CordaTopicPartition>) {
        log.info("onPartitionsAssigned: $partitions")
        if (partitions.isNotEmpty()) {
            readStates(partitions.map { CordaTopicPartition(stateTopic, it.partition) })
        }
    }

    private fun readStates(partitions: Collection<CordaTopicPartition>) {
        val processor = createStateProcessor()
        log.info("Assigning partitions $partitions")
        processor.assign(partitions)
        val beginningOffsets = processor.beginningOffsets(partitions)
        val endOffsets = processor.endOffsets(partitions)
            .filter { (partition, endOffset) ->
                beginningOffsets[partition]!! < endOffset
            }.map { (topic, offset) ->
                topic.partition to offset
            }.toMap(ConcurrentHashMap())
        if (endOffsets.isNotEmpty()) {
            val latch = CountDownLatch(1)
            log.info("Polling topic $stateTopic")
            processor.poll { record ->
                updateState(record)
                if (record.offset >= endOffsets[record.partition]!!) {
                    endOffsets.remove(record.partition)
                    if (endOffsets.isEmpty()) {
                        latch.countDown()
                    }
                }
            }
            latch.await()
        }
        processor.close()
        log.info("States read from topic $stateTopic")
    }

    /**
     * This method is for closing the loop/thread externally. From inside the loop use the private [stopConsumeLoop].
     */
    override fun close() {
        eventProcessor?.close()
        lifecycleCoordinator.close()
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
        val future = waitForFunctionToFinish(
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

    private val executor = Executors.newSingleThreadScheduledExecutor { runnable ->
        val thread = Thread(runnable)
        thread.isDaemon = true
        thread
    }

    fun waitForFunctionToFinish(function: () -> Any, maxTimeout: Long, timeoutErrorMessage: String): CompletableFuture<Any> {
        val future: CompletableFuture<Any> = CompletableFuture.supplyAsync(
            function,
            wrapWithTracingExecutor(executor)
        )
        future.tryGetResult(initialProcessorTimeout)

        if (!future.isDone) {
            future.get(maxTimeout, TimeUnit.MILLISECONDS)
        }

        if (!future.isDone) {
            future.cancel(true)
            log.error(timeoutErrorMessage)
        }

        return future
    }
}
