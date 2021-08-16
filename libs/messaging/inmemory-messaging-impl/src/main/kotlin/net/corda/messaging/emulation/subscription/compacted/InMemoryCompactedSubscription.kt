package net.corda.messaging.emulation.subscription.compacted

import net.corda.lifecycle.Lifecycle
import net.corda.messaging.api.processor.CompactedProcessor
import net.corda.messaging.api.records.Record
import net.corda.messaging.api.subscription.CompactedSubscription
import net.corda.messaging.api.subscription.factory.config.SubscriptionConfig
import net.corda.messaging.emulation.topic.service.TopicService
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.debug
import org.slf4j.Logger
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference
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

    private val currentConsumer = AtomicReference<Lifecycle>()
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

    fun gotRecord(record: Record<K, V>) {
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
            currentConsumer.getAndSet(null)?.stop()
        }
    }

    override fun start() {
        logger.debug { "Starting event log subscription with config: $subscriptionConfig" }
        startStopLock.withLock {
            if (currentConsumer.get() == null) {
                updateSnapshots()
                processor.onSnapshot(knownValues)
                val consumer = CompactedConsumer(this)
                val lifeCycle = topicService.subscribe(consumer)
                currentConsumer.set(lifeCycle)
            }
        }
    }

    override val isRunning
        get() = currentConsumer.get()?.isRunning ?: false

    override fun getValue(key: K): V? {
        return snapshotsLock.withLock {
            knownValues[key]
        }
    }
}
