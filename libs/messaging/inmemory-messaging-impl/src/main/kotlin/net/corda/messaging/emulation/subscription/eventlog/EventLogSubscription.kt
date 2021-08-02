package net.corda.messaging.emulation.subscription.eventlog

import net.corda.messaging.api.processor.EventLogProcessor
import net.corda.messaging.api.subscription.PartitionAssignmentListener
import net.corda.messaging.api.subscription.Subscription
import net.corda.messaging.api.subscription.factory.config.SubscriptionConfig
import net.corda.messaging.emulation.topic.service.TopicService
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.debug
import org.slf4j.Logger
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class EventLogSubscription<K : Any, V : Any>(
    private val subscriptionConfig: SubscriptionConfig,
    internal val processor: EventLogProcessor<K, V>,
    internal val partitionAssignmentListener: PartitionAssignmentListener?,
    internal val topicService: TopicService,
    private val threadFactory:
        (EventLogSubscription<K, V>)->EventLogSubscriptionThread<K, V> = { EventLogSubscriptionThread(it) }
) : Subscription<K, V> {

    companion object {
        private val logger: Logger = contextLogger()
    }

    internal val topic by lazy {
        subscriptionConfig.eventTopic
    }
    internal val group by lazy {
        subscriptionConfig.groupName
    }

    internal val partitioner: (net.corda.messaging.api.records.Record<*, *>) -> Int by lazy {
        Partitioner(partitionAssignmentListener)
    }


    private val currentLoop = AtomicReference<EventLogSubscriptionThread<K, V>>(null)
    private val lock = ReentrantLock()

    override fun stop() {
        logger.debug { "Stopping event log subscription with config: $subscriptionConfig" }
        lock.withLock {
            currentLoop.getAndSet(null)?.stop()
        }
    }

    override fun start() {
        logger.debug { "Starting event log subscription with config: $subscriptionConfig" }
        lock.withLock {
            val currentThread = currentLoop.get()
            if(currentThread != null) {
                return
            }
            val newThread = threadFactory(this)
            currentLoop.set(newThread)
            newThread.start()

        }
    }

    override val isRunning: Boolean
        get() = currentLoop.get() != null
}
