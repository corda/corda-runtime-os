package net.corda.messaging.emulation.subscription.compacted

import net.corda.messaging.api.processor.CompactedProcessor
import net.corda.messaging.api.records.Record
import net.corda.messaging.api.subscription.CompactedSubscription
import net.corda.messaging.api.subscription.factory.config.SubscriptionConfig
import net.corda.messaging.emulation.topic.model.Consumption
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
    private val topicService: TopicService,
) : CompactedSubscription<K, V> {

    private val knownValues = ConcurrentHashMap<K, V>()
    private val snapshotsLock = ReentrantLock()

    companion object {
        private val logger: Logger = contextLogger()
    }

    internal val topicName = subscriptionConfig.eventTopic

    internal val groupName = subscriptionConfig.groupName

    private var currentConsumption: Consumption? = null
    private val startStopLock = ReentrantLock()

    fun updateSnapshots() {
        snapshotsLock.withLock {
            knownValues.clear()
            topicService.handleAllRecords(topicName) { records ->
                records.mapNotNull {
                    it.castToType(processor.keyClass, processor.valueClass)
                }.groupBy({ it.key }, { it.value })
                    .mapValues { it.value.last() }
                    .onEach { (key, value) ->
                        if (value != null) {
                            knownValues[key] = value
                        }
                    }
            }
        }
    }

    fun onNewRecord(record: Record<K, V>) {
        val oldValue = snapshotsLock.withLock {
            val value = record.value
            if (value == null) {
                knownValues.remove(record.key)
            } else {
                knownValues.put(record.key, value)
            }
        }
        processor.onNext(record, oldValue, knownValues)
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
                updateSnapshots()
                processor.onSnapshot(knownValues)
                val consumer = CompactedConsumer(this)
                currentConsumption = topicService.subscribe(consumer)
            }
        }
    }

    override val isRunning
        get() = currentConsumption?.isRunning ?: false

    override fun getValue(key: K): V? {
        return snapshotsLock.withLock {
            knownValues[key]
        }
    }
}
