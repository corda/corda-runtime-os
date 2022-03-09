package net.corda.messaging.subscription

import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleStatus
import net.corda.messagebus.api.configuration.ConsumerConfig
import net.corda.messagebus.api.constants.ConsumerRoles
import net.corda.messagebus.api.consumer.CordaConsumer
import net.corda.messagebus.api.consumer.CordaConsumerRecord
import net.corda.messagebus.api.consumer.builder.MessageBusConsumerBuilder
import net.corda.messaging.api.exception.CordaMessageAPIFatalException
import net.corda.messaging.api.exception.CordaMessageAPIIntermittentException
import net.corda.messaging.api.processor.CompactedProcessor
import net.corda.messaging.api.subscription.CompactedSubscription
import net.corda.messaging.config.ResolvedSubscriptionConfig
import net.corda.messaging.subscription.factory.MapFactory
import net.corda.messaging.utils.toRecord
import net.corda.v5.base.util.debug
import org.slf4j.LoggerFactory
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.thread
import kotlin.concurrent.withLock

internal class CompactedSubscriptionImpl<K : Any, V : Any>(
    private val config: ResolvedSubscriptionConfig,
    private val mapFactory: MapFactory<K, V>,
    private val cordaConsumerBuilder: MessageBusConsumerBuilder,
    private val processor: CompactedProcessor<K, V>,
    lifecycleCoordinatorFactory: LifecycleCoordinatorFactory
) : CompactedSubscription<K, V> {

    private val log = LoggerFactory.getLogger(config.loggerName)

    private val errorMsg = "Failed to read records from group ${config.group}, topic ${config.topic}"

    @Volatile
    private var stopped = false
    private val lock = ReentrantLock()
    private var consumeLoopThread: Thread? = null
    private val lifecycleCoordinator = lifecycleCoordinatorFactory.createCoordinator(
        LifecycleCoordinatorName(
            "${config.topic}-KafkaCompactedSubscription-${config.group}",
            //we use clientIdCounter here instead of instanceId as this subscription is readOnly
            config.clientId
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
        thread?.join(config.threadStopTimeout.toMillis())
    }

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
                    name = "compacted subscription thread ${config.group}-${config.topic}",
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
                val consumerConfig = ConsumerConfig(config.group, config.clientId, ConsumerRoles.COMPACTED)
                cordaConsumerBuilder.createConsumer(
                    consumerConfig,
                    config.busConfig,
                    processor.keyClass,
                    processor.valueClass,
                    ::onError
                ).use {
                    val partitions = it.getPartitions(
                        config.topic,
                        // TODO - This looks like the wrong timeout to me.
                        config.threadStopTimeout
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

    private fun onError(bytes: ByteArray) {
        log.error("Failed to deserialize record from ${config.topic} with bytes $bytes")
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
            val consumerRecords = consumer.poll(config.pollTimeout)

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
            val consumerRecords = consumer.poll(config.pollTimeout)
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
                            "Failed to process records from topic ${config.topic}, group ${config.group}.", ex
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
