package net.corda.messaging.kafka.subscription

import com.typesafe.config.Config
import net.corda.messaging.api.exception.CordaMessageAPIIntermittentException
import net.corda.messaging.api.processor.CompactedProcessor
import net.corda.messaging.api.subscription.CompactedSubscription
import net.corda.messaging.kafka.properties.KafkaProperties
import net.corda.messaging.kafka.properties.KafkaProperties.Companion.CONSUMER_GROUP_ID
import net.corda.messaging.kafka.properties.KafkaProperties.Companion.KAFKA_CONSUMER
import net.corda.messaging.kafka.properties.KafkaProperties.Companion.TOPIC_NAME
import net.corda.messaging.kafka.render
import net.corda.messaging.kafka.subscription.consumer.builder.ConsumerBuilder
import net.corda.messaging.kafka.subscription.consumer.wrapper.ConsumerRecordAndMeta
import net.corda.messaging.kafka.subscription.consumer.wrapper.CordaKafkaConsumer
import net.corda.messaging.kafka.subscription.consumer.wrapper.asRecord
import net.corda.messaging.kafka.subscription.factory.SubscriptionMapFactory
import net.corda.v5.base.util.debug
import org.apache.kafka.common.TopicPartition
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.Duration
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.thread
import kotlin.concurrent.withLock

class KafkaCompactedSubscriptionImpl<K : Any, V : Any>(
    private val config: Config,
    private val mapFactory: SubscriptionMapFactory<K, V>,
    private val consumerBuilder: ConsumerBuilder<K, V>,
    private val processor: CompactedProcessor<K, V>,
) : CompactedSubscription<K, V> {
    companion object {
        private val log: Logger = LoggerFactory.getLogger(this::class.java)
    }

    private val consumerThreadStopTimeout = config.getLong(KafkaProperties.CONSUMER_THREAD_STOP_TIMEOUT)
    private val topicPrefix = config.getString(KafkaProperties.TOPIC_PREFIX)
    private val groupName = config.getString(CONSUMER_GROUP_ID)
    private val topic = config.getString(TOPIC_NAME)

    private val errorMsg = "Failed to read records from group $groupName, topic $topic"

    @Volatile
    private var stopped = false
    private val lock = ReentrantLock()
    private var consumeLoopThread: Thread? = null

    private var latestValues: MutableMap<K, V>? = null

    override fun stop() {
        if (!stopped) {
            val thread = lock.withLock {
                stopped = true
                latestValues?.apply { mapFactory.destroyMap(this) }
                latestValues = null
                val threadTmp = consumeLoopThread
                consumeLoopThread = null
                threadTmp
            }
            thread?.join(consumerThreadStopTimeout)
        }
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
                    name = "compacted subscription thread $groupName-$topic",
                    priority = -1,
                    block = ::runConsumeLoop
                )
            }
        }
    }

    override val isRunning: Boolean
        get() = !stopped

    override fun getValue(key: K): V? = latestValues?.get(key)

    @Suppress("TooGenericExceptionCaught")
    private fun runConsumeLoop() {
        var attempts = 0
        while (!stopped) {
            attempts++
            try {
                log.debug { "Creating compacted consumer.  Attempt: $attempts" }
                consumerBuilder.createCompactedConsumer(config.getConfig(KAFKA_CONSUMER)).use {
                    val partitions = it.getPartitions(
                        topicPrefix + topic,
                        Duration.ofSeconds(consumerThreadStopTimeout)
                    )
                    it.assign(partitions)
                    pollAndProcessSnapshot(it)
                    pollAndProcessRecords(it)
                }
                attempts = 0
            } catch (ex: Exception) {
                when (ex) {
                    is CordaMessageAPIIntermittentException -> {
                        log.warn("$errorMsg. Attempts: $attempts. Retrying.", ex)
                    }
                    else -> {
                        log.error("$errorMsg. Fatal error occurred. Closing subscription.", ex)
                        stop()
                    }
                }
            }
        }
    }

    private fun getLatestValues(): MutableMap<K, V> {
        var latest = latestValues
        if (latest == null) {
            latest = mapFactory.createMap()
            latestValues = latest
        }
        return latest
    }

    private fun pollAndProcessSnapshot(consumer: CordaKafkaConsumer<K, V>) {
        val partitions = consumer.assignment()
        val snapshotEnds = consumer.endOffsets(partitions)
        val currentOffsets = consumer.beginningOffsets(partitions)
        consumer.seekToBeginning(partitions)

        val currentData = getLatestValues()
        currentData.clear()

        while (currentOffsets.any { it.value < (snapshotEnds[it.key] ?: 0) }) {
            val consumerRecords = consumer.poll()
            if (consumerRecords.isEmpty()) {
                break
            }
            consumerRecords.forEach {
                if (it.record.value() != null) {
                    currentData[it.record.key()] = it.record.value()
                } else {
                    currentData.remove(it.record.key())
                }
                currentOffsets[TopicPartition(it.record.topic(), it.record.partition())] = it.record.offset()
            }
        }
        processor.onSnapshot(currentData)
    }

    @Suppress("TooGenericExceptionCaught")
    private fun pollAndProcessRecords(consumer: CordaKafkaConsumer<K, V>) {
        while (!stopped) {
            try {
                val consumerRecords = consumer.poll()
                processCompactedRecords(consumerRecords)
            } catch (ex: Exception) {
                throw CordaMessageAPIIntermittentException("$errorMsg.", ex)
            }
        }
    }

    private fun processCompactedRecords(
        consumerRecords: List<ConsumerRecordAndMeta<K, V>>
    ) {
        val currentData = getLatestValues()
        consumerRecords.forEach {
            val oldValue = currentData[it.record.key()]
            val newValue = it.record.value()

            if (newValue == null) {
                currentData.remove(it.record.key())
            } else {
                currentData[it.record.key()] = newValue
            }

            processor.onNext(it.asRecord(), oldValue, currentData)
        }
    }
}
