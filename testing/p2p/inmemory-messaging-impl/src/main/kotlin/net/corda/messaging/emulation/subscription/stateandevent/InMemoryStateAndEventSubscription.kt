package net.corda.messaging.emulation.subscription.stateandevent

import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleStatus
import net.corda.messaging.api.processor.StateAndEventProcessor
import net.corda.messaging.api.subscription.StateAndEventSubscription
import net.corda.messaging.api.subscription.config.SubscriptionConfig
import net.corda.messaging.api.subscription.listener.StateAndEventListener
import net.corda.messaging.emulation.topic.service.TopicService
import net.corda.v5.base.util.debug
import org.slf4j.LoggerFactory
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

@Suppress("LongParameterList")
class InMemoryStateAndEventSubscription<K : Any, S : Any, E : Any>(
    internal val subscriptionConfig: SubscriptionConfig,
    internal val processor: StateAndEventProcessor<K, S, E>,
    internal val stateAndEventListener: StateAndEventListener<K, S>?,
    internal val topicService: TopicService,
    private val lifecycleCoordinatorFactory: LifecycleCoordinatorFactory,
    private val instanceId: Int
) :
    StateAndEventSubscription<K, S, E> {

    companion object {
        private val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    internal val stateSubscriptionConfig = subscriptionConfig.copy(
        eventTopic = "${subscriptionConfig.eventTopic}.state",
        groupName = "${subscriptionConfig.groupName}.state"
    )

    private val lock = ReentrantLock()
    private val eventSubscription: EventSubscription<K, S, E> = EventSubscription(this)
    internal val stateSubscription: StateSubscription<K, S> = StateSubscription(this)
    private val lifecycleCoordinator = lifecycleCoordinatorFactory.createCoordinator(
        LifecycleCoordinatorName(
            "${subscriptionConfig.groupName}-StateAndEventSubscription-${subscriptionConfig.eventTopic}",
            instanceId.toString()
        )
    ) { _, _ -> }

    val isRunning: Boolean
        get() = lock.withLock {
            eventSubscription.isRunning && stateSubscription.isRunning
        }

    override fun start() {
        log.debug { "Starting subscription with config: $subscriptionConfig" }

        lock.withLock {
            stateSubscription.start()
            eventSubscription.start()
            lifecycleCoordinator.start()
            lifecycleCoordinator.updateStatus(LifecycleStatus.UP)
        }
    }

    override fun close() {
        stopSubscriptions()
        lifecycleCoordinator.close()
    }

    private fun stopSubscriptions() {
        lock.withLock {
            eventSubscription.stop()
            stateSubscription.stop()
            lifecycleCoordinator.updateStatus(LifecycleStatus.DOWN)
        }
    }

    internal fun setValue(key: K, updatedState: S?, partition: Int) {
        stateSubscription.setValue(key, updatedState, partition)
    }

    override val subscriptionName: LifecycleCoordinatorName
        get() = lifecycleCoordinator.name
}
