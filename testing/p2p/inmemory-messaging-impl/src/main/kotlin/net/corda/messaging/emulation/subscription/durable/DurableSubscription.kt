package net.corda.messaging.emulation.subscription.durable

import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleStatus
import net.corda.messaging.api.processor.DurableProcessor
import net.corda.messaging.api.subscription.Subscription
import net.corda.messaging.api.subscription.config.SubscriptionConfig
import net.corda.messaging.api.subscription.listener.PartitionAssignmentListener
import net.corda.messaging.emulation.topic.model.Consumption
import net.corda.messaging.emulation.topic.model.RecordMetadata
import net.corda.messaging.emulation.topic.service.TopicService
import net.corda.utilities.debug
import org.slf4j.LoggerFactory
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

@Suppress("LongParameterList")
internal class DurableSubscription<K : Any, V : Any>(
    internal val subscriptionConfig: SubscriptionConfig,
    private val processor: DurableProcessor<K, V>,
    internal val partitionAssignmentListener: PartitionAssignmentListener?,
    private val topicService: TopicService,
    private val lifecycleCoordinatorFactory: LifecycleCoordinatorFactory,
    private val instanceId: Int
) : Subscription<K, V> {
    companion object {
        private val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    private val lifecycleCoordinator = lifecycleCoordinatorFactory.createCoordinator(
        LifecycleCoordinatorName(
            "${subscriptionConfig.groupName}-DurableSubscription-${subscriptionConfig.eventTopic}",
            instanceId.toString()
        )
    ) { _, _ -> }
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
                lifecycleCoordinator.start()
                lifecycleCoordinator.updateStatus(LifecycleStatus.UP)
            }
        }
    }

    override fun close() {
        log.debug { "Closing durable subscription with config: $subscriptionConfig" }
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

    internal fun processRecords(records: Collection<RecordMetadata>) {
        val castMessages = records.mapNotNull {
            it.castToType(processor.keyClass, processor.valueClass)
        }

        val responses = processor.onNext(castMessages)

        topicService.addRecords(responses)
    }

    override val subscriptionName: LifecycleCoordinatorName
        get() = lifecycleCoordinator.name
}
