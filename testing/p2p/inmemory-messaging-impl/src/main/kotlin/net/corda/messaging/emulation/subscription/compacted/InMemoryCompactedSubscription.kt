package net.corda.messaging.emulation.subscription.compacted

import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleStatus
import net.corda.messaging.api.processor.CompactedProcessor
import net.corda.messaging.api.subscription.CompactedSubscription
import net.corda.messaging.api.subscription.config.SubscriptionConfig
import net.corda.messaging.emulation.topic.model.Consumption
import net.corda.messaging.emulation.topic.model.RecordMetadata
import net.corda.messaging.emulation.topic.service.TopicService
import net.corda.utilities.debug
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class InMemoryCompactedSubscription<K : Any, V : Any>(
    private val subscriptionConfig: SubscriptionConfig,
    internal val processor: CompactedProcessor<K, V>,
    private val topicService: TopicService,
    private val lifecycleCoordinatorFactory: LifecycleCoordinatorFactory,
    private val clientIdCounter: String
) : CompactedSubscription<K, V> {

    private val knownValues = ConcurrentHashMap<K, V>()

    companion object {
        private val logger: Logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    internal val topicName = subscriptionConfig.eventTopic

    internal val groupName = subscriptionConfig.groupName

    private val latestOffsets = topicService.getLatestOffsets(topicName)
    private val waitingForPartitions = ConcurrentHashMap.newKeySet<Int>().also {
        it.addAll(
            latestOffsets
                .filterValues {
                    it >= 0
                }
                .keys
        )
    }
    private val lifecycleCoordinator = lifecycleCoordinatorFactory.createCoordinator(
        LifecycleCoordinatorName(
            "${subscriptionConfig.groupName}-CompactedSubscription-${subscriptionConfig.eventTopic}",
            //we use clientIdCounter here instead of instanceId as this subscription is readOnly
            clientIdCounter
        )
    ) { _, _ -> }

    private var currentConsumption: Consumption? = null
    private val startStopLock = ReentrantLock()

    fun onNewRecord(recordMetadata: RecordMetadata) {
        val removed = if ((latestOffsets[recordMetadata.partition] ?: 0) <= recordMetadata.offset) {
            waitingForPartitions.remove(recordMetadata.partition)
        } else {
            false
        }

        val record = recordMetadata.castToType(processor.keyClass, processor.valueClass)
        val oldValue = if (record != null) {
            val value = record.value
            if (value == null) {
                knownValues.remove(record.key)
            } else {
                knownValues.put(record.key, value)
            }
        } else {
            null
        }

        if (waitingForPartitions.isEmpty()) {
            if (removed) {
                processor.onSnapshot(knownValues.toMap())
            } else {
                if (record != null) {
                    processor.onNext(record, oldValue, knownValues)
                }
            }
        }
    }

    override fun close() {
        logger.debug { "Closing compacted subscription with config: $subscriptionConfig" }
        stopConsumer()
        lifecycleCoordinator.close()
    }

    private fun stopConsumer() {
        startStopLock.withLock {
            currentConsumption?.stop()
            currentConsumption = null
            lifecycleCoordinator.updateStatus(LifecycleStatus.DOWN)
        }
    }

    override fun start() {
        logger.debug { "Starting compacted subscription with config: $subscriptionConfig" }
        startStopLock.withLock {
            if (currentConsumption == null) {
                lifecycleCoordinator.start()
                lifecycleCoordinator.updateStatus(LifecycleStatus.UP)
                val consumer = CompactedConsumer(this)
                if (waitingForPartitions.isEmpty()) {
                    processor.onSnapshot(knownValues)
                }
                currentConsumption = topicService.createConsumption(consumer)
            }
        }
    }

    override val isRunning
        get() = currentConsumption?.isRunning ?: false

    override fun getValue(key: K): V? {
        if (waitingForPartitions.isNotEmpty()) {
            throw IllegalStateException("Snapshot is not ready")
        }
        return knownValues[key]
    }

    override val subscriptionName: LifecycleCoordinatorName
        get() = lifecycleCoordinator.name
}
