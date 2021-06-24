package net.corda.messaging.kafka.subscription

import com.typesafe.config.Config
import net.corda.messaging.api.exception.CordaMessageAPIIntermittentException
import net.corda.messaging.api.processor.StateAndEventProcessor
import net.corda.messaging.api.records.Record
import net.corda.messaging.api.subscription.StateAndEventSubscription
import net.corda.messaging.kafka.producer.wrapper.CordaKafkaProducer
import net.corda.messaging.kafka.properties.KafkaProperties
import net.corda.messaging.kafka.properties.KafkaProperties.Companion.KAFKA_PRODUCER
import net.corda.messaging.kafka.properties.KafkaProperties.Companion.PRODUCER_CLIENT_ID
import net.corda.messaging.kafka.properties.KafkaProperties.Companion.PRODUCER_TRANSACTIONAL_ID
import net.corda.messaging.kafka.properties.KafkaProperties.Companion.TOPIC_NAME
import net.corda.messaging.kafka.render
import net.corda.messaging.kafka.subscription.consumer.builder.StateAndEventBuilder
import net.corda.messaging.kafka.subscription.consumer.wrapper.CordaKafkaConsumer
import net.corda.messaging.kafka.subscription.consumer.wrapper.asRecord
import net.corda.messaging.kafka.subscription.factory.SubscriptionMapFactory
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.base.util.debug
import net.corda.v5.base.util.trace
import org.apache.kafka.clients.CommonClientConfigs
import org.apache.kafka.clients.consumer.ConsumerRebalanceListener
import org.apache.kafka.common.TopicPartition
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.Clock
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.thread
import kotlin.concurrent.withLock

class Topic(val prefix: String, val suffix: String) {
    val topic
        get() = prefix + suffix
}

@Suppress("TooManyFunctions")
class KafkaStateAndEventSubscriptionImpl<K : Any, S : Any, E : Any>(
    private val config: Config,
    private val mapFactory: SubscriptionMapFactory<K, Pair<Long, S>>,
    private val builder: StateAndEventBuilder<K, S, E>,
    private val processor: StateAndEventProcessor<K, S, E>,
    private val clock: Clock = Clock.systemUTC()
) : StateAndEventSubscription<K, S, E>, ConsumerRebalanceListener {

    companion object {
        private const val STATE_CONSUMER = "stateConsumer"
        private const val EVENT_CONSUMER = "eventConsumer"
        private const val STATE_TOPIC_NAME = "$STATE_CONSUMER.$TOPIC_NAME"
        private const val EVENT_GROUP_ID = "$EVENT_CONSUMER.${CommonClientConfigs.GROUP_ID_CONFIG}"
        private val CONSUMER_THREAD_STOP_TIMEOUT = KafkaProperties.CONSUMER_THREAD_STOP_TIMEOUT.replace("consumer", "eventConsumer")
        private val CONSUMER_CLOSE_TIMEOUT = KafkaProperties.CONSUMER_CLOSE_TIMEOUT.replace("consumer", "eventConsumer")
    }
    private val log: Logger = LoggerFactory.getLogger(
        "${config.getString(EVENT_GROUP_ID)}.${config.getString(PRODUCER_TRANSACTIONAL_ID)}")

    private lateinit var producer: CordaKafkaProducer
    private lateinit var eventConsumer: CordaKafkaConsumer<K, E>
    private lateinit var stateConsumer: CordaKafkaConsumer<K, S>
    private var currentStates: MutableMap<K, Pair<Long, S>>? = null

    @Volatile
    private var stopped = false
    private val lock = ReentrantLock()
    private var consumeLoopThread: Thread? = null

    private val topicPrefix = config.getString(KafkaProperties.TOPIC_PREFIX)
    private val eventTopic = Topic(topicPrefix, config.getString(TOPIC_NAME))
    private val stateTopic = Topic(topicPrefix, config.getString(STATE_TOPIC_NAME))
    private val groupName = config.getString(EVENT_GROUP_ID)
    private val producerClientId: String = config.getString(PRODUCER_CLIENT_ID)
    private val consumerThreadStopTimeout = config.getLong(CONSUMER_THREAD_STOP_TIMEOUT)
    private val producerCloseTimeout = Duration.ofMillis(config.getLong(KafkaProperties.PRODUCER_CLOSE_TIMEOUT))
    private val consumerCloseTimeout = Duration.ofMillis(config.getLong(CONSUMER_CLOSE_TIMEOUT))

    // When syncing up new partitions gives us the (partition, endOffset) for a given new partition
    private val statePartitionsToSync: MutableMap<Int, Long> = ConcurrentHashMap<Int, Long>()

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

    /**
     * This is not guaranteed to be thread-safe!
     */
    override fun getValue(key: K): S? {
        return getCurrentStates()[key]?.second
    }

    private fun getCurrentStates(): MutableMap<K, Pair<Long, S>> {
        var current = currentStates
        if (current == null) {
            current = mapFactory.createMap()
            currentStates = current
        }
        return current
    }

    @Suppress("TooGenericExceptionCaught")
    fun runConsumeLoop() {
        var attempts = 0
        while (!stopped) {
            attempts++
            try {
                producer = builder.createProducer(config.getConfig(KAFKA_PRODUCER))
                stateConsumer = builder.createStateConsumer(config.getConfig(STATE_CONSUMER))
                eventConsumer = builder.createEventConsumer(config.getConfig(EVENT_CONSUMER), this)
                validateConsumers(stateConsumer, eventConsumer)

                stateConsumer.assign(emptyList())
                eventConsumer.subscribeToTopic()

                while (!stopped) {
                    updateStates()
                    processEvents(eventConsumer, producer)
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
                eventConsumer.close(consumerCloseTimeout)
                stateConsumer.close(consumerCloseTimeout)
            }
        }
        producer.close(producerCloseTimeout)
        eventConsumer.close(consumerCloseTimeout)
        stateConsumer.close(consumerCloseTimeout)
    }

    private fun validateConsumers(stateConsumer: CordaKafkaConsumer<K, S>, eventConsumer: CordaKafkaConsumer<K, E>) {
        val statePartitions = stateConsumer.getPartitions(stateTopic.topic, Duration.ofSeconds(consumerThreadStopTimeout))
        val eventPartitions = eventConsumer.getPartitions(eventTopic.topic, Duration.ofSeconds(consumerThreadStopTimeout))
        if (statePartitions.size != eventPartitions.size) {
            val errorMsg = "Mismatch between state and event partitions."
            log.debug {
                errorMsg + "\n" +
                        "state: ${statePartitions.joinToString()}\n" +
                        "event: ${eventPartitions.joinToString()}"
            }
            throw CordaRuntimeException(errorMsg)
        }
    }

    /**
     *  This rebalance is called for the event consumer, though most of the work is to ensure the state consumer
     *  keeps up
     */
    override fun onPartitionsAssigned(newEventPartitions: MutableCollection<TopicPartition>) {
        log.debug { "Updating state partitions to match new event partitions: $newEventPartitions" }
        val newStatePartitions = newEventPartitions.toStateTopics()
        val statePartitions = stateConsumer.assignment() + newStatePartitions
        stateConsumer.assign(statePartitions)
        stateConsumer.seekToBeginning(newStatePartitions)

        // Initialise the housekeeping here but the sync and updates
        // will be handled in the normal poll cycle
        val syncablePartitions = filterSyncablePartitions(newStatePartitions)
        log.debug { "Syncing the following new state partitions: $syncablePartitions" }
        statePartitionsToSync.putAll(syncablePartitions)
        eventConsumer.pause(syncablePartitions.map { TopicPartition(eventTopic.topic, it.first) })
    }

    private fun filterSyncablePartitions(newStatePartitions: List<TopicPartition>): List<Pair<Int, Long>> {
        val beginningOffsets = stateConsumer.beginningOffsets(newStatePartitions)
        val endOffsets = stateConsumer.endOffsets(newStatePartitions)
        return newStatePartitions.mapNotNull {
            val beginningOffset = beginningOffsets[it] ?: 0
            val endOffset = endOffsets[it] ?: 0
            if (beginningOffset < endOffset) {
                Pair(it.partition(), endOffset)
            } else {
                null
            }
        }
    }

    /**
     *  This rebalance is called for the event consumer, though most of the work is to ensure the state consumer
     *  keeps up
     */
    override fun onPartitionsRevoked(removedEventPartitions: MutableCollection<TopicPartition>) {
        log.debug { "Updating state partitions to match removed event partitions: $removedEventPartitions" }
        val removedStatePartitions = removedEventPartitions.toStateTopics()
        val statePartitions = stateConsumer.assignment() - removedStatePartitions
        stateConsumer.assign(statePartitions)
        for (topicPartition in removedStatePartitions) {
            statePartitionsToSync.remove(topicPartition.partition())
        }
    }

    private fun processEvents(eventConsumer: CordaKafkaConsumer<K, E>, producer: CordaKafkaProducer) {
        for (event in eventConsumer.poll()) {
            log.trace { "Processing event: $event" }
            val updates = processor.onNext(getCurrentStates()[event.record.key()]?.second, event.asRecord())
            val updatedState = updates.updatedState
            if (updatedState != null) {
                getCurrentStates()[event.record.key()] = Pair(clock.instant().toEpochMilli(), updatedState)
            } else {
                getCurrentStates().remove(event.record.key())
            }
            producer.beginTransaction()
            producer.sendRecords(updates.responseEvents + Record(stateTopic.suffix, event.record.key(), updatedState))
            producer.tryCommitTransaction()
        }
        eventConsumer.commitSync()
    }

    private fun updateStates() {
        if (stateConsumer.assignment().isEmpty()) {
            log.trace { "State consumer has to partitions assigned." }
            return
        }
        val states = stateConsumer.poll()
        for (state in states) {
            log.trace { "Updating state: $state" }
            getCurrentStates().compute(state.record.key()) { _, currentState ->
                if (currentState == null || currentState.first <= state.record.timestamp()) {
                    if (state.record.value() == null) {
                        // Removes this state from the map
                        null
                    } else {
                        // Replaces/adds the new state
                        Pair(state.record.timestamp(), state.record.value())
                    }
                } else {
                    // Keeps the old state
                    currentState
                }
            }
            // Check sync and resume
            if (statePartitionsToSync.isNotEmpty()) {
                val currentPartition = state.record.partition()
                val endOffset = statePartitionsToSync[currentPartition]
                if (endOffset != null && endOffset >= state.record.offset()) {
                    statePartitionsToSync.remove(currentPartition)
                    val resumablePartition = TopicPartition(eventTopic.topic, currentPartition)
                    log.debug { "State consumer is up to date for $resumablePartition.  Resuming event feed." }
                    eventConsumer.resume(setOf(resumablePartition))
                }
            }
        }
    }

    private fun TopicPartition.toStateTopic() = TopicPartition(stateTopic.topic, partition())
    private fun TopicPartition.toEventTopic() = TopicPartition(eventTopic.topic, partition())
    private fun Collection<TopicPartition>.toStateTopics(): List<TopicPartition> = map { it.toStateTopic() }
    @Suppress("unused")
    private fun Collection<TopicPartition>.toEventTopics(): List<TopicPartition> = map { it.toEventTopic() }


}
