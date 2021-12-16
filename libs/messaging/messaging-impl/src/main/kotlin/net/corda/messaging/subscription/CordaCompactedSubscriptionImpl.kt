package net.corda.messaging.subscription

import com.typesafe.config.Config
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleStatus
import net.corda.messagebus.api.configuration.ConfigProperties.Companion.CONSUMER_GROUP_ID
import net.corda.messagebus.api.configuration.ConfigProperties.Companion.CONSUMER_THREAD_STOP_TIMEOUT
import net.corda.messagebus.api.consumer.CordaConsumer
import net.corda.messagebus.api.consumer.CordaConsumerRecord
import net.corda.messaging.api.consumer.builder.CordaConsumerBuilder
import net.corda.messaging.api.exception.CordaMessageAPIFatalException
import net.corda.messaging.api.exception.CordaMessageAPIIntermittentException
import net.corda.messaging.api.processor.CompactedProcessor
import net.corda.messaging.api.subscription.CompactedSubscription
import net.corda.messaging.kafka.properties.ConfigProperties
import net.corda.messaging.kafka.properties.ConfigProperties.Companion.KAFKA_CONSUMER
import net.corda.messaging.kafka.properties.ConfigProperties.Companion.TOPIC_NAME
import net.corda.messaging.kafka.utils.render
import net.corda.messaging.kafka.utils.toRecord
import net.corda.messaging.subscription.factory.MapFactory
import net.corda.v5.base.util.debug
import org.slf4j.LoggerFactory
import java.time.Duration
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.thread
import kotlin.concurrent.withLock

class CordaCompactedSubscriptionImpl<K : Any, V : Any>(
    private val config: Config,
    private val mapFactory: MapFactory<K, V>,
    private val cordaConsumerBuilder: CordaConsumerBuilder,
    private val processor: CompactedProcessor<K, V>,
    private val lifecycleCoordinatorFactory: LifecycleCoordinatorFactory
) : CompactedSubscription<K, V> {

    private val log = LoggerFactory.getLogger(
        config.getString(CONSUMER_GROUP_ID)
    )

    private val consumerThreadStopTimeout = config.getLong(CONSUMER_THREAD_STOP_TIMEOUT)
    private val groupName = config.getString(CONSUMER_GROUP_ID)
    private val topic = config.getString(TOPIC_NAME)

    private val errorMsg = "Failed to read records from group $groupName, topic $topic"

    @Volatile
    private var stopped = false
    private val lock = ReentrantLock()
    private var consumeLoopThread: Thread? = null
    private val lifecycleCoordinator = lifecycleCoordinatorFactory.createCoordinator(
        LifecycleCoordinatorName(
            "$groupName-KafkaCompactedSubscription-$topic",
            //we use clientIdCounter here instead of instanceId as this subscription is readOnly
            config.getString(ConfigProperties.CLIENT_ID_COUNTER)
        )
    ) { _, _ -> }

    private var latestValues: MutableMap<K, V>? = null

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
            latestValues?.apply { mapFactory.destroyMap(this) }
            latestValues = null
            val threadTmp = consumeLoopThread
            consumeLoopThread = null
            threadTmp
        }
        thread?.join(consumerThreadStopTimeout)
    }

    override fun start() {
        log.debug { "Starting subscription with config:\n${config.render()}" }
        lock.withLock {
            if (consumeLoopThread == null) {
                stopped = false
                lifecycleCoordinator.start()
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

    override val subscriptionName: LifecycleCoordinatorName
        get() = lifecycleCoordinator.name

    override fun getValue(key: K): V? = latestValues?.get(key)

    private fun runConsumeLoop() {
        var attempts = 0
        while (!stopped) {
            attempts++
            try {
                log.debug { "Creating compacted consumer.  Attempt: $attempts" }
                cordaConsumerBuilder.createCompactedConsumer(
                    config.getConfig(KAFKA_CONSUMER),
                    processor.keyClass,
                    processor.valueClass
                ).use {
                    val partitions = it.getPartitions(
                        topic,
                        Duration.ofSeconds(consumerThreadStopTimeout)
                    )
                    it.assign(partitions)
                    lifecycleCoordinator.updateStatus(LifecycleStatus.UP)
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
                        lifecycleCoordinator.updateStatus(LifecycleStatus.ERROR, errorMsg)
                        stop()
                    }
                }
            }
        }
        lifecycleCoordinator.updateStatus(LifecycleStatus.DOWN)
    }

    private fun getLatestValues(): MutableMap<K, V> {
        var latest = latestValues
        if (latest == null) {
            latest = mapFactory.createMap()
            latestValues = latest
        }
        return latest
    }

    private fun pollAndProcessSnapshot(consumer: CordaConsumer<K, V>) {
        val partitions = consumer.assignment()
        val endOffsets = consumer.endOffsets(partitions)
        val snapshotEnds = endOffsets.toMutableMap()
        consumer.seekToBeginning(partitions)

        val currentData = getLatestValues()
        currentData.clear()

        while (snapshotEnds.isNotEmpty()) {
            val consumerRecords = consumer.poll()

            consumerRecords.forEach {
                val value = it.value
                if (value != null) {
                    currentData[it.key] = value
                } else {
                    currentData.remove(it.key)
                }
            }

            for (offsets in endOffsets) {
                val partition = offsets.key
                if (consumer.position(partition) >= offsets.value) {
                    snapshotEnds.remove(partition)
                }
            }
        }

        processor.onSnapshot(currentData)
    }

    private fun pollAndProcessRecords(consumer: CordaConsumer<K, V>) {
        while (!stopped) {
            val consumerRecords = consumer.poll()
            try {
                processCompactedRecords(consumerRecords)
            } catch (ex: Exception) {
                when (ex) {
                    is CordaMessageAPIFatalException,
                    is CordaMessageAPIIntermittentException -> {
                        throw ex
                    }
                    else -> {
                        throw CordaMessageAPIFatalException(
                            "Failed to process records from topic $topic, group $groupName.", ex
                        )
                    }
                }
            }
        }
    }

    private fun processCompactedRecords(
        cordaConsumerRecords: List<CordaConsumerRecord<K, V>>
    ) {
        val currentData = getLatestValues()
        cordaConsumerRecords.forEach {
            val oldValue = currentData[it.key]
            val newValue = it.value

            if (newValue == null) {
                currentData.remove(it.key)
            } else {
                currentData[it.key] = newValue
            }

            processor.onNext(it.toRecord(), oldValue, currentData)
        }
    }
}
