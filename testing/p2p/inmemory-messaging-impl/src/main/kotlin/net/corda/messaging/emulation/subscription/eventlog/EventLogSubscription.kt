package net.corda.messaging.emulation.subscription.eventlog

import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleStatus
import net.corda.messaging.api.processor.EventLogProcessor
import net.corda.messaging.api.subscription.Subscription
import net.corda.messaging.api.subscription.config.SubscriptionConfig
import net.corda.messaging.api.subscription.listener.PartitionAssignmentListener
import net.corda.messaging.emulation.topic.model.Consumption
import net.corda.messaging.emulation.topic.service.TopicService
import net.corda.v5.base.util.debug
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Implementation of the event log subscription for the in memory messaging.
 *
 * @property subscriptionConfig - The configuration of the subscription.
 * @property processor - The processor to feed the event record to.
 * @property partitionAssignmentListener - Optional listener to partition assignments.
 * @property topicService - A topic service to supply events records.
 * @property lifecycleCoordinatorFactory used to create the lifecycleCoordinator object that external users can follow for updates
 */
@Suppress("LongParameterList")
class EventLogSubscription<K : Any, V : Any>(
    internal val subscriptionConfig: SubscriptionConfig,
    internal val processor: EventLogProcessor<K, V>,
    internal val partitionAssignmentListener: PartitionAssignmentListener?,
    internal val topicService: TopicService,
    private val lifecycleCoordinatorFactory: LifecycleCoordinatorFactory,
    private val instanceId: Int
) : Subscription<K, V> {

    companion object {
        private val logger: Logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    private var currentConsumer: Consumption? = null
    private val lock = ReentrantLock()
    private val lifecycleCoordinator = lifecycleCoordinatorFactory.createCoordinator(
        LifecycleCoordinatorName(
            "${subscriptionConfig.groupName}-EventLogSubscription-${subscriptionConfig.eventTopic}",
            instanceId.toString()
        )
    ) { _, _ -> }

    override fun close() {
        logger.debug { "Closing event log subscription with config: $subscriptionConfig" }
        stopConsumer()
        lifecycleCoordinator.close()
    }

    private fun stopConsumer() {
        lock.withLock {
            currentConsumer?.stop()
            currentConsumer = null
            lifecycleCoordinator.updateStatus(LifecycleStatus.DOWN)
        }
    }

    override fun start() {
        logger.debug { "Starting event log subscription with config: $subscriptionConfig" }
        lock.withLock {
            if (currentConsumer == null) {
                val consumer = EventLogConsumer(this)
                lifecycleCoordinator.start()
                lifecycleCoordinator.updateStatus(LifecycleStatus.UP)
                currentConsumer = topicService.createConsumption(consumer)
            }
        }
    }

    override val isRunning: Boolean
        get() = currentConsumer?.isRunning ?: false
    override val subscriptionName: LifecycleCoordinatorName
        get() = lifecycleCoordinator.name
}
