package net.corda.messaging.emulation.subscription.eventlog

import net.corda.messaging.api.processor.EventLogProcessor
import net.corda.messaging.api.subscription.PartitionAssignmentListener
import net.corda.messaging.api.subscription.Subscription
import net.corda.messaging.emulation.topic.service.TopicService
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.debug
import org.slf4j.Logger
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

@Suppress("LongParameterList")
class EventLogSubscription<K : Any, V : Any>(
    internal val config: InMemoryEventLogSubscriptionConfig,
    internal val processor: EventLogProcessor<K, V>,
    private val partitionAssignmentListener: PartitionAssignmentListener?,
    internal val topicService: TopicService,
    private val threadFactory:
        (EventLogSubscription<K, V>) -> EventLogSubscriptionThread<K, V> = { EventLogSubscriptionThread(it) }
) : Subscription<K, V> {

    companion object {
        private val logger: Logger = contextLogger()
    }

    internal val topic by lazy {
        config.subscriptionConfig.eventTopic
    }
    internal val group by lazy {
        config.subscriptionConfig.groupName
    }

    internal val partitioner: (net.corda.messaging.api.records.Record<*, *>) -> Int by lazy {
        Partitioner(partitionAssignmentListener, config.partitionSize)
    }

    private val currentLoop = AtomicReference<EventLogSubscriptionThread<K, V>>(null)
    private val lock = ReentrantLock()

    override fun stop() {
        logger.debug { "Stopping event log subscription with config: ${config.subscriptionConfig}" }
        lock.withLock {
            currentLoop.getAndSet(null)?.stop()
        }
    }

    override fun start() {
        logger.debug { "Starting event log subscription with config: ${config.subscriptionConfig}" }
        lock.withLock {
            val currentThread = currentLoop.get()
            if (currentThread != null) {
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
