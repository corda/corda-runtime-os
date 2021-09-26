package net.corda.messaging.emulation.subscription.durable

import net.corda.messaging.api.processor.DurableProcessor
import net.corda.messaging.api.subscription.PartitionAssignmentListener
import net.corda.messaging.api.subscription.Subscription
import net.corda.messaging.api.subscription.factory.config.SubscriptionConfig
import net.corda.messaging.emulation.topic.model.Consumption
import net.corda.messaging.emulation.topic.model.RecordMetadata
import net.corda.messaging.emulation.topic.service.TopicService
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.debug
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

internal class DurableSubscription<K : Any, V : Any>(
    internal val subscriptionConfig: SubscriptionConfig,
    private val processor: DurableProcessor<K, V>,
    internal val partitionAssignmentListener: PartitionAssignmentListener?,
    private val topicService: TopicService
) : Subscription<K, V> {
    companion object {
        private val log = contextLogger()
    }

    private val lock = ReentrantLock()
    private var currentConsumer: Consumption? = null

    override val isRunning: Boolean
        get() {
            return currentConsumer?.isRunning ?: false
        }

    override fun start() {
        log.debug { "Starting subscription with config: $subscriptionConfig" }
        lock.withLock {
            if (currentConsumer == null) {
                val consumer = DurableConsumer(this)
                currentConsumer = topicService.createConsumption(consumer)
            }
        }
    }

    override fun stop() {
        lock.withLock {
            currentConsumer?.stop()
            currentConsumer = null
        }
    }

    internal fun processRecords(records: Collection<RecordMetadata>) {
        val castMessages = records.mapNotNull {
            it.castToType(processor.keyClass, processor.valueClass)
        }

        val responses = processor.onNext(castMessages)

        topicService.addRecords(responses)
    }
}
