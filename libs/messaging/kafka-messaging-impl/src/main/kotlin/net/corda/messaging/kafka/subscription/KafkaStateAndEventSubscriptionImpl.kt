package net.corda.messaging.kafka.subscription

import net.corda.data.deadletter.StateAndEventDeadLetterRecord
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleStatus
import net.corda.messaging.api.exception.CordaMessageAPIFatalException
import net.corda.messaging.api.exception.CordaMessageAPIIntermittentException
import net.corda.messaging.api.processor.StateAndEventProcessor
import net.corda.messaging.api.records.Record
import net.corda.messaging.api.subscription.StateAndEventSubscription
import net.corda.messaging.api.subscription.listener.StateAndEventListener
import net.corda.messaging.kafka.producer.wrapper.CordaKafkaProducer
import net.corda.messaging.kafka.publisher.CordaAvroSerializer
import net.corda.messaging.kafka.subscription.consumer.builder.StateAndEventBuilder
import net.corda.messaging.kafka.subscription.consumer.wrapper.CordaKafkaConsumer
import net.corda.messaging.kafka.subscription.consumer.wrapper.StateAndEventConsumer
import net.corda.messaging.kafka.types.StateAndEventConfig
import net.corda.messaging.kafka.utils.getEventsByBatch
import net.corda.messaging.kafka.utils.toRecord
import net.corda.messaging.kafka.utils.tryGetResult
import net.corda.schema.registry.AvroSchemaRegistry
import net.corda.v5.base.util.debug
import net.corda.v5.base.util.uncheckedCast
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.consumer.OffsetResetStrategy
import org.slf4j.LoggerFactory
import java.nio.ByteBuffer
import java.time.Clock
import java.util.*
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.thread
import kotlin.concurrent.withLock

@Suppress("LongParameterList")
class KafkaStateAndEventSubscriptionImpl<K : Any, S : Any, E : Any>(
    private val config: StateAndEventConfig,
    private val builder: StateAndEventBuilder<K, S, E>,
    private val processor: StateAndEventProcessor<K, S, E>,
    private val avroSchemaRegistry: AvroSchemaRegistry,
    private val lifecycleCoordinatorFactory: LifecycleCoordinatorFactory,
    private val stateAndEventListener: StateAndEventListener<K, S>? = null,
    private val clock: Clock = Clock.systemUTC()
) : StateAndEventSubscription<K, S, E> {

    private val log = LoggerFactory.getLogger(config.loggerName)

    private lateinit var producer: CordaKafkaProducer
    private lateinit var stateAndEventConsumer: StateAndEventConsumer<K, S, E>
    private lateinit var eventConsumer: CordaKafkaConsumer<K, E>

    @Volatile
    private var stopped = false
    private val lock = ReentrantLock()
    private var consumeLoopThread: Thread? = null

    private val cordaAvroSerializer = CordaAvroSerializer<Any>(avroSchemaRegistry)

    private val eventTopic = config.eventTopic
    private val stateTopic = config.stateTopic
    private val groupName = config.eventGroupName
    private val producerClientId: String = config.producerClientId
    private val consumerThreadStopTimeout = config.consumerThreadStopTimeout
    private val producerCloseTimeout = config.producerCloseTimeout
    private val consumerPollAndProcessMaxRetries = config.consumerPollAndProcessMaxRetries
    private val processorTimeout = config.processorTimeout
    private val deadLetterQueueSuffix = config.deadLetterQueueSuffix
    private val lifecycleCoordinator = lifecycleCoordinatorFactory.createCoordinator(
        LifecycleCoordinatorName(
            "$groupName-KafkaStateAndEventSubscription-$stateTopic.$eventTopic",
            //we use instanceId here as transactionality is a concern in this subscription
            config.instanceId
        )
    ) { _, _ -> }

    private val errorMsg = "Failed to read and process records from topic $eventTopic, group $groupName, " +
            "producerClientId $producerClientId."

    /**
     * Is the subscription running.
     */
    override val isRunning: Boolean
        get() {
            return !stopped
        }

    override val subscriptionName: LifecycleCoordinatorName
        get() = lifecycleCoordinator.name

    override fun start() {
        log.debug { "Starting subscription with config:\n${config}" }
        lock.withLock {
            if (consumeLoopThread == null) {
                stopped = false
                lifecycleCoordinator.start()
                consumeLoopThread = thread(
                    start = true,
                    isDaemon = true,
                    contextClassLoader = null,
                    name = "state/event processing thread $groupName-($stateTopic.$eventTopic)",
                    priority = -1,
                    block = ::runConsumeLoop
                )
            }
        }
    }

    override fun stop() {
        if (!stopped) {
            stopConsumeLoop()
            lifecycleCoordinator.stop()
        }
    }

    override fun close() {
        if (!stopped) {
            stopConsumeLoop()
            lifecycleCoordinator.close()
        }
    }

    private fun stopConsumeLoop() {
        val thread = lock.withLock {
            stopped = true
            val threadTmp = consumeLoopThread
            consumeLoopThread = null
            threadTmp
        }
        thread?.join(consumerThreadStopTimeout)
    }

    fun runConsumeLoop() {
        var attempts = 0
        while (!stopped) {
            attempts++
            try {
                producer = builder.createProducer(config)
                val (stateAndEventConsumerTmp, rebalanceListener) = builder.createStateEventConsumerAndRebalanceListener(
                    config,
                    processor.keyClass,
                    processor.stateValueClass,
                    processor.eventValueClass,
                    stateAndEventListener,
                    { topic, data ->
                        log.error("Failed to deserialize state record from $topic")
                        producer.beginTransaction()
                        producer.sendRecords(
                            listOf(
                                Record(
                                    this.eventTopic + deadLetterQueueSuffix,
                                    UUID.randomUUID().toString(),
                                    data
                                )
                            )
                        )
                        producer.commitTransaction()
                    },
                    { topic, data ->
                        log.error("Failed to deserialize event record from $topic")
                        producer.beginTransaction()
                        producer.sendRecords(
                            listOf(
                                Record(
                                    this.eventTopic + deadLetterQueueSuffix,
                                    UUID.randomUUID().toString(),
                                    data
                                )
                            )
                        )
                        producer.commitTransaction()
                    }
                )
                stateAndEventConsumer = stateAndEventConsumerTmp
                eventConsumer = stateAndEventConsumer.eventConsumer
                eventConsumer.subscribeToTopic(rebalanceListener)
                lifecycleCoordinator.updateStatus(LifecycleStatus.UP)

                while (!stopped) {
                    stateAndEventConsumer.pollAndUpdateStates(true)
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
                        lifecycleCoordinator.updateStatus(LifecycleStatus.ERROR, errorMsg)
                        stop()
                    }
                }
            } finally {
                producer.close(producerCloseTimeout)
                stateAndEventConsumer.close()
            }
        }
        lifecycleCoordinator.updateStatus(LifecycleStatus.DOWN)
        producer.close(producerCloseTimeout)
        stateAndEventConsumer.close()
    }

    private fun processEvents() {
        var attempts = 0
        var pollAndProcessSuccessful = false
        while (!pollAndProcessSuccessful && !stopped) {
            try {
                for (batch in getEventsByBatch(eventConsumer.poll())) {
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
                            "Failed to process records from topic $eventTopic, group $groupName, producerClientId $producerClientId. " +
                                    "Fatal error occurred.", ex
                        )
                    }
                }
            }
        }
    }

    private fun tryProcessBatchOfEvents(events: List<ConsumerRecord<K, E>>) {
        val outputRecords = mutableListOf<Record<*, *>>()
        val updatedStates: MutableMap<Int, MutableMap<K, S?>> = mutableMapOf()

        log.debug { "Processing events(size: ${events.size})" }
        for (event in events) {
            stateAndEventConsumer.resetPollInterval()
            processEvent(event, outputRecords, updatedStates)
        }

        producer.beginTransaction()
        producer.sendRecords(outputRecords)
        producer.sendRecordOffsetsToTransaction(eventConsumer, events.map { it })
        producer.commitTransaction()
        log.debug { "Processing of events(size: ${events.size}) complete" }

        stateAndEventConsumer.updateInMemoryStatePostCommit(updatedStates, clock)
    }

    private fun processEvent(
        event: ConsumerRecord<K, E>,
        outputRecords: MutableList<Record<*, *>>,
        updatedStates: MutableMap<Int, MutableMap<K, S?>>
    ) {
        log.debug { "Processing event: $event" }
        val key = event.key()
        val state = stateAndEventConsumer.getInMemoryStateValue(key)
        val partitionId = event.partition()
        val thisEventUpdates = getUpdatesForEvent(state, event)

        if (thisEventUpdates == null) {
            log.warn("Sending event: $event, and state: $state to dead letter queue. Processor failed to complete.")
            outputRecords.add(generateDeadLetterRecord(event, state))
            outputRecords.add(Record(stateTopic, key, null))
            updatedStates.computeIfAbsent(partitionId) { mutableMapOf() }[key] = null
        } else {
            outputRecords.addAll(thisEventUpdates.responseEvents)
            val updatedState = thisEventUpdates.updatedState
            outputRecords.add(Record(stateTopic, key, updatedState))
            updatedStates.computeIfAbsent(partitionId) { mutableMapOf() }[key] = updatedState
            log.debug { "Completed event: $event" }
        }
    }

    private fun getUpdatesForEvent(state: S?, event: ConsumerRecord<K, E>): StateAndEventProcessor.Response<S>? {
        val future = stateAndEventConsumer.waitForFunctionToFinish(
            { processor.onNext(state, event.toRecord()) }, processorTimeout,
            "Failed to finish within the time limit for state: $state and event: $event"
        )
        return uncheckedCast(future.tryGetResult())
    }

    private fun generateDeadLetterRecord(event: ConsumerRecord<K, E>, state: S?): Record<*, *> {
        val keyBytes = ByteBuffer.wrap(cordaAvroSerializer.serialize(stateTopic, event.key()))
        val stateBytes =
            if (state != null) ByteBuffer.wrap(cordaAvroSerializer.serialize(stateTopic, state)) else null
        val eventBytes = ByteBuffer.wrap(cordaAvroSerializer.serialize(eventTopic, event.value()))
        return Record(
            eventTopic + deadLetterQueueSuffix, event.key(),
            StateAndEventDeadLetterRecord(clock.instant(), keyBytes, stateBytes, eventBytes)
        )
    }

    /**
     * Handle retries for event processing.
     * Reset [eventConsumer] position and retry poll and process of eventRecords
     * Retry a max of [consumerPollAndProcessMaxRetries] times.
     * If [consumerPollAndProcessMaxRetries] is exceeded then throw a [CordaMessageAPIIntermittentException]
     */
    private fun handleProcessEventRetries(
        attempts: Int,
        ex: Exception
    ) {
        if (attempts <= consumerPollAndProcessMaxRetries) {
            log.warn(
                "Failed to process record from topic $eventTopic, group $groupName, " +
                        "producerClientId $producerClientId. " +
                        "Retrying poll and process. Attempts: $attempts."
            )
            eventConsumer.resetToLastCommittedPositions(OffsetResetStrategy.EARLIEST)
        } else {
            val message = "Failed to process records from topic $eventTopic, group $groupName, " +
                    "producerClientId $producerClientId. " +
                    "Attempts: $attempts. Max reties exceeded."
            log.warn(message, ex)
            throw CordaMessageAPIIntermittentException(message, ex)
        }
    }
}
