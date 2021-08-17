package net.corda.messaging.emulation.subscription.eventlog

import net.corda.lifecycle.Lifecycle
import net.corda.messaging.api.processor.EventLogProcessor
import net.corda.messaging.api.subscription.PartitionAssignmentListener
import net.corda.messaging.api.subscription.Subscription
import net.corda.messaging.api.subscription.factory.config.SubscriptionConfig
import net.corda.messaging.emulation.topic.service.TopicService
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.debug
import org.slf4j.Logger
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Implementation of the event log subscription for the in memory messaging.
 *
 * @property subscriptionConfig - The configuration of the subscription.
 * @property processor - The processor to feed the event record to.
 * @property partitionAssignmentListener - Optional listener to partition assignments.
 * @property topicService - A topic service to supply events records.
 */
class EventLogSubscription<K : Any, V : Any>(
    private val subscriptionConfig: SubscriptionConfig,
    internal val processor: EventLogProcessor<K, V>,
    internal val partitionAssignmentListener: PartitionAssignmentListener?,
    internal val topicService: TopicService,
) : Subscription<K, V> {

    companion object {
        private val logger: Logger = contextLogger()
    }

    internal val topicName = subscriptionConfig.eventTopic

    internal val groupName = subscriptionConfig.groupName

    private var currentConsumer: Lifecycle? = null
    private val lock = ReentrantLock()

    override fun stop() {
        logger.debug { "Stopping event log subscription with config: $subscriptionConfig" }
        lock.withLock {
            currentConsumer?.stop()
            currentConsumer = null
        }
    }

    override fun start() {
        logger.debug { "Starting event log subscription with config: $subscriptionConfig" }
        lock.withLock {
            if (currentConsumer == null) {
                val consumer = EventLogConsumer(this)
                currentConsumer = topicService.subscribe(consumer)
            }
        }
    }

    override val isRunning: Boolean
        get() = currentConsumer?.isRunning ?: false
}
