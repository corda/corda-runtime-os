package net.corda.messaging.kafka.subscription

import com.typesafe.config.Config
import net.corda.data.deadletter.StateAndEventDeadLetterRecord
import net.corda.messaging.api.exception.CordaMessageAPIFatalException
import net.corda.messaging.api.exception.CordaMessageAPIIntermittentException
import net.corda.messaging.api.processor.StateAndEventProcessor
import net.corda.messaging.api.records.Record
import net.corda.messaging.api.subscription.StateAndEventSubscription
import net.corda.messaging.api.subscription.listener.StateAndEventListener
import net.corda.messaging.kafka.producer.wrapper.CordaKafkaProducer
import net.corda.messaging.kafka.properties.ConfigProperties.Companion.CONSUMER_PROCESSOR_TIMEOUT
import net.corda.messaging.kafka.properties.ConfigProperties.Companion.DEAD_LETTER_QUEUE_SUFFIX
import net.corda.messaging.kafka.publisher.CordaAvroSerializer
import net.corda.messaging.kafka.properties.ConfigProperties.Companion.EVENT_CONSUMER_POLL_AND_PROCESS_RETRIES
import net.corda.messaging.kafka.properties.ConfigProperties.Companion.EVENT_CONSUMER_THREAD_STOP_TIMEOUT
import net.corda.messaging.kafka.properties.ConfigProperties.Companion.EVENT_GROUP_ID
import net.corda.messaging.kafka.properties.ConfigProperties.Companion.PRODUCER_CLIENT_ID
import net.corda.messaging.kafka.properties.ConfigProperties.Companion.PRODUCER_CLOSE_TIMEOUT
import net.corda.messaging.kafka.properties.ConfigProperties.Companion.PRODUCER_TRANSACTIONAL_ID
import net.corda.messaging.kafka.properties.ConfigProperties.Companion.STATE_TOPIC_NAME
import net.corda.messaging.kafka.properties.ConfigProperties.Companion.TOPIC_NAME
import net.corda.messaging.kafka.properties.ConfigProperties.Companion.TOPIC_PREFIX
import net.corda.messaging.kafka.subscription.consumer.builder.StateAndEventBuilder
import net.corda.messaging.kafka.subscription.consumer.wrapper.ConsumerRecordAndMeta
import net.corda.messaging.kafka.subscription.consumer.wrapper.CordaKafkaConsumer
import net.corda.messaging.kafka.subscription.consumer.wrapper.StateAndEventConsumer
import net.corda.messaging.kafka.subscription.consumer.wrapper.asRecord
import net.corda.messaging.kafka.types.StateAndEventConfig
import net.corda.messaging.kafka.types.Topic
import net.corda.messaging.kafka.utils.getEventsByBatch
import net.corda.messaging.kafka.utils.render
import net.corda.messaging.kafka.utils.tryGetFutureResult
import net.corda.schema.registry.AvroSchemaRegistry
import net.corda.v5.base.util.debug
import net.corda.v5.base.util.trace
import net.corda.v5.base.util.uncheckedCast
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.consumer.OffsetResetStrategy
import org.slf4j.LoggerFactory
import java.nio.ByteBuffer
import java.time.Clock
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.thread
import kotlin.concurrent.withLock

class KafkaStateAndEventSubscriptionImpl<K : Any, S : Any, E : Any>(
    private val config: StateAndEventConfig,
    private val builder: StateAndEventBuilder<K, S, E>,
    private val processor: StateAndEventProcessor<K, S, E>,
    private val avroSchemaRegistry: AvroSchemaRegistry,
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

    private val topicPrefix = config.topicPrefix
    private val eventTopic = Topic(topicPrefix, config.eventTopic)
    private val stateTopic = Topic(topicPrefix, config.stateTopic)
    private val groupName = config.eventGroupName
    private val producerClientId: String = config.producerClientId
    private val consumerThreadStopTimeout = config.consumerThreadStopTimeout
    private val producerCloseTimeout = config.producerCloseTimeout
    private val consumerPollAndProcessMaxRetries = config.consumerPollAndProcessMaxRetries
    private val processorTimeout = config.processorTimeout
    private val deadLetterQueueSuffix = config.deadLetterQueueSuffix

    /**
     * Is the subscription running.
     */
    override val isRunning: Boolean
        get() {
            return !stopped
        }

    override fun start() {
        log.debug { "Starting subscription with config:\n${config}" }
        lock.withLock {
            if (consumeLoopThread == null) {
                stopped = false
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
            val thread = lock.withLock {
                stopped = true
                val threadTmp = consumeLoopThread
                consumeLoopThread = null
                threadTmp
            }
            thread?.join(consumerThreadStopTimeout)
        }
    }

    @Suppress("TooGenericExceptionCaught")
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
                    stateAndEventListener
                )
                stateAndEventConsumer = stateAndEventConsumerTmp
                eventConsumer = stateAndEventConsumer.eventConsumer
                eventConsumer.subscribeToTopic(rebalanceListener)

                while (!stopped) {
                    stateAndEventConsumer.pollAndUpdateStates(true)
                    processEvents()
                }
            } catch (ex: Exception) {
                when (ex) {
                    is CordaMessageAPIIntermittentException -> {
                        log.warn(
                            "Failed to read and process records from topic $eventTopic, group $groupName, " +
                                    "producerClientId $producerClientId. Attempts: $attempts. Recreating " +
                                    "consumer/producer and Retrying.", ex
                        )
                    }
                    else -> {
                        log.error(
                            "Failed to read and process records from topic $eventTopic, group $groupName, " +
                                    "producerClientId $producerClientId. Attempts: $attempts. Closing " +
                                    "subscription.", ex
                        )
                        stop()
                    }
                }
            } finally {
                producer.close(producerCloseTimeout)
                stateAndEventConsumer.close()
            }
        }
        producer.close(producerCloseTimeout)
        stateAndEventConsumer.close()
    }

    @Suppress("TooGenericExceptionCaught")
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

    private fun tryProcessBatchOfEvents(events: List<ConsumerRecordAndMeta<K, E>>) {
        val outputRecords = mutableListOf<Record<*, *>>()
        val updatedStates: MutableMap<Int, MutableMap<K, S?>> = mutableMapOf()

        log.trace { "Processing events(size: ${events.size})" }
        for (event in events) {
            stateAndEventConsumer.resetPollInterval()
            processEvent(event, outputRecords, updatedStates)
        }

        producer.beginTransaction()
        producer.sendRecords(outputRecords)
        producer.sendRecordOffsetsToTransaction(eventConsumer, events.map { it.record })
        producer.tryCommitTransaction()
        log.trace { "Processing of events(size: ${events.size}) complete" }

        stateAndEventConsumer.updateInMemoryStatePostCommit(updatedStates, clock)
    }

    private fun processEvent(
        event: ConsumerRecordAndMeta<K, E>,
        outputRecords: MutableList<Record<*, *>>,
        updatedStates: MutableMap<Int, MutableMap<K, S?>>
    ) {
        log.trace { "Processing event: $event" }
        val key = event.record.key()
        val state = stateAndEventConsumer.getInMemoryStateValue(key)
        val partitionId = event.record.partition()
        val thisEventUpdates = getUpdatesForEvent(state, event)

        if (thisEventUpdates == null) {
            log.error("Sending event: $event, and state: $state to dead letter queue. Processor failed to complete.")
            outputRecords.add(generateDeadLetterRecord(event.record, state))
            outputRecords.add(Record(stateTopic.suffix, key, null))
            updatedStates.computeIfAbsent(partitionId) { mutableMapOf() }[key] = null
        } else {
            outputRecords.addAll(thisEventUpdates.responseEvents)
            val updatedState = thisEventUpdates.updatedState
            outputRecords.add(Record(stateTopic.suffix, key, updatedState))
            updatedStates.computeIfAbsent(partitionId) { mutableMapOf() }[key] = updatedState
            log.trace { "Completed event: $event" }
        }
    }

    private fun getUpdatesForEvent(state: S?, event: ConsumerRecordAndMeta<K, E>): StateAndEventProcessor.Response<S>? {
        val future = stateAndEventConsumer.waitForFunctionToFinish({ processor.onNext(state, event.asRecord()) }, processorTimeout,
            "Failed to finish within the time limit for state: $state and event: $event")
        return uncheckedCast(tryGetFutureResult(future))
    }

    private fun generateDeadLetterRecord(event: ConsumerRecord<K, E>, state: S?): Record<*, *> {
        val stateBytes = if (state != null) ByteBuffer.wrap(cordaAvroSerializer.serialize(stateTopic.topic, state)) else null
        val eventBytes = ByteBuffer.wrap(cordaAvroSerializer.serialize(eventTopic.topic, event.value()))
        return Record(eventTopic.topic + deadLetterQueueSuffix, event.key(),
            StateAndEventDeadLetterRecord(clock.instant(), stateBytes, eventBytes)
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
