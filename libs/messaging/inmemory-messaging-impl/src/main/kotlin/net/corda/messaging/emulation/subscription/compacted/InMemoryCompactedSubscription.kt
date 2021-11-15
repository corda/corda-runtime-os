package net.corda.messaging.emulation.subscription.compacted

import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.messaging.api.processor.CompactedProcessor
import net.corda.messaging.api.subscription.CompactedSubscription
import net.corda.messaging.api.subscription.factory.config.SubscriptionConfig
import net.corda.messaging.emulation.topic.model.Consumption
import net.corda.messaging.emulation.topic.model.RecordMetadata
import net.corda.messaging.emulation.topic.service.TopicService
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.debug
import org.slf4j.Logger
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class InMemoryCompactedSubscription<K : Any, V : Any>(
    private val subscriptionConfig: SubscriptionConfig,
    internal val processor: CompactedProcessor<K, V>,
    private val topicService: TopicService
) : CompactedSubscription<K, V> {

    private val knownValues = ConcurrentHashMap<K, V>()

    companion object {
        private val logger: Logger = contextLogger()
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

    override fun stop() {
        logger.debug { "Stopping event log subscription with config: $subscriptionConfig" }
        startStopLock.withLock {
            currentConsumption?.stop()
            currentConsumption = null
        }
    }

    override fun start() {
        logger.debug { "Starting event log subscription with config: $subscriptionConfig" }
        startStopLock.withLock {
            if (currentConsumption == null) {
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
        get() = TODO("Not yet implemented")
}
