package net.corda.messaging.kafka.subscription

import com.typesafe.config.Config
import net.corda.messaging.api.exception.CordaMessageAPIFatalException
import net.corda.messaging.api.exception.CordaMessageAPIIntermittentException
import net.corda.messaging.api.processor.StateAndEventProcessor
import net.corda.messaging.api.records.Record
import net.corda.messaging.api.subscription.StateAndEventSubscription
import net.corda.messaging.api.subscription.listener.StateAndEventListener
import net.corda.messaging.kafka.producer.wrapper.CordaKafkaProducer
import net.corda.messaging.kafka.properties.KafkaProperties.Companion.CONSUMER_POLL_AND_PROCESS_RETRIES
import net.corda.messaging.kafka.properties.KafkaProperties.Companion.CONSUMER_THREAD_STOP_TIMEOUT
import net.corda.messaging.kafka.properties.KafkaProperties.Companion.PRODUCER_CLIENT_ID
import net.corda.messaging.kafka.properties.KafkaProperties.Companion.PRODUCER_CLOSE_TIMEOUT
import net.corda.messaging.kafka.properties.KafkaProperties.Companion.PRODUCER_TRANSACTIONAL_ID
import net.corda.messaging.kafka.properties.KafkaProperties.Companion.TOPIC_NAME
import net.corda.messaging.kafka.properties.KafkaProperties.Companion.TOPIC_PREFIX
import net.corda.messaging.kafka.subscription.consumer.builder.StateAndEventBuilder
import net.corda.messaging.kafka.subscription.consumer.wrapper.ConsumerRecordAndMeta
import net.corda.messaging.kafka.subscription.consumer.wrapper.CordaKafkaConsumer
import net.corda.messaging.kafka.subscription.consumer.wrapper.asRecord
import net.corda.messaging.kafka.subscription.consumer.wrapper.StateAndEventConsumer
import net.corda.messaging.kafka.utils.getEventsByBatch
import net.corda.messaging.kafka.utils.render
import net.corda.v5.base.util.debug
import net.corda.v5.base.util.trace
import org.apache.kafka.clients.CommonClientConfigs.GROUP_ID_CONFIG
import org.apache.kafka.clients.consumer.OffsetResetStrategy
import org.slf4j.LoggerFactory
import java.time.Clock
import java.time.Duration
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.thread
import kotlin.concurrent.withLock

class Topic(val prefix: String, val suffix: String) {
    val topic
        get() = prefix + suffix
}

class KafkaStateAndEventSubscriptionImpl<K : Any, S : Any, E : Any>(
    private val config: Config,
    private val builder: StateAndEventBuilder<K, S, E>,
    private val processor: StateAndEventProcessor<K, S, E>,
    private val stateAndEventListener: StateAndEventListener<K, S>? = null,
    private val clock: Clock = Clock.systemUTC()
) : StateAndEventSubscription<K, S, E> {

    companion object {
        private const val STATE_CONSUMER = "stateConsumer"
        private const val EVENT_CONSUMER = "eventConsumer"
        private const val STATE_TOPIC_NAME = "$STATE_CONSUMER.$TOPIC_NAME"
        private const val EVENT_GROUP_ID = "$EVENT_CONSUMER.$GROUP_ID_CONFIG"
        private val EVENT_CONSUMER_THREAD_STOP_TIMEOUT = CONSUMER_THREAD_STOP_TIMEOUT.replace("consumer", "eventConsumer")
        private val EVENT_CONSUMER_POLL_AND_PROCESS_RETRIES = CONSUMER_POLL_AND_PROCESS_RETRIES.replace("consumer", "eventConsumer")
    }

    private val log = LoggerFactory.getLogger(
        "${config.getString(EVENT_GROUP_ID)}.${config.getString(PRODUCER_TRANSACTIONAL_ID)}"
    )

    private lateinit var producer: CordaKafkaProducer
    private lateinit var stateAndEventConsumer: StateAndEventConsumer<K, S, E>
    private lateinit var eventConsumer: CordaKafkaConsumer<K, E>

    @Volatile
    private var stopped = false
    private val lock = ReentrantLock()
    private var consumeLoopThread: Thread? = null

    private val topicPrefix = config.getString(TOPIC_PREFIX)
    private val eventTopic = Topic(topicPrefix, config.getString(TOPIC_NAME))
    private val stateTopic = Topic(topicPrefix, config.getString(STATE_TOPIC_NAME))
    private val groupName = config.getString(EVENT_GROUP_ID)
    private val producerClientId: String = config.getString(PRODUCER_CLIENT_ID)
    private val consumerThreadStopTimeout = config.getLong(EVENT_CONSUMER_THREAD_STOP_TIMEOUT)
    private val producerCloseTimeout = Duration.ofMillis(config.getLong(PRODUCER_CLOSE_TIMEOUT))
    private val consumerPollAndProcessMaxRetries = config.getLong(EVENT_CONSUMER_POLL_AND_PROCESS_RETRIES)

    /**
     * Is the subscription running.
     */
    override val isRunning: Boolean
        get() {
            return !stopped
        }

    override fun start() {
        log.debug { "Starting subscription with config:\n${config.render()}" }
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
                val stateAndEventConsumerAndListener = builder.createStateEventConsumerAndRebalanceListener(
                    config,
                    processor.keyClass,
                    processor.stateValueClass,
                    processor.eventValueClass,
                    stateAndEventListener
                )
                stateAndEventConsumer = stateAndEventConsumerAndListener.first
                eventConsumer = stateAndEventConsumer.eventConsumer

                eventConsumer.subscribeToTopic(stateAndEventConsumerAndListener.second)

                while (!stopped) {
                    stateAndEventConsumer.updateStatesAndSynchronizePartitions()
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
        while (!pollAndProcessSuccessful) {
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
            processEvent(event, outputRecords, updatedStates)
        }

        producer.beginTransaction()
        producer.sendRecords(outputRecords)
        producer.sendRecordOffsetsToTransaction(eventConsumer, events.map { it.record })
        producer.tryCommitTransaction()
        log.trace { "Processing of events(size: ${events.size}) complete" }

        stateAndEventConsumer.onProcessorStateUpdated(updatedStates, clock)
    }

    private fun processEvent(
        event: ConsumerRecordAndMeta<K, E>,
        outputRecords: MutableList<Record<*, *>>,
        updatedStates: MutableMap<Int, MutableMap<K, S?>>
    ) {
        log.trace { "Processing event: $event" }
        val key = event.record.key()
        val partitionId = event.record.partition()
        val thisEventUpdates = processor.onNext(stateAndEventConsumer.getValue(key), event.asRecord())
        outputRecords.addAll(thisEventUpdates.responseEvents)
        val updatedState = thisEventUpdates.updatedState
        outputRecords.add(Record(stateTopic.suffix, key, updatedState))
        updatedStates.computeIfAbsent(partitionId) { mutableMapOf() }[key] = updatedState
        log.trace { "Completed event: $event" }
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
