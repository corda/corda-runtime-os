package net.corda.messaging.emulation.subscription.stateandevent

import net.corda.messaging.api.processor.StateAndEventProcessor
import net.corda.messaging.api.subscription.StateAndEventSubscription
import net.corda.messaging.api.subscription.factory.config.SubscriptionConfig
import net.corda.messaging.api.subscription.listener.StateAndEventListener
import net.corda.messaging.emulation.topic.service.TopicService
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.debug
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class InMemoryStateAndEventSubscription<K : Any, S : Any, E : Any>(
    internal val subscriptionConfig: SubscriptionConfig,
    internal val processor: StateAndEventProcessor<K, S, E>,
    internal val stateAndEventListener: StateAndEventListener<K, S>?,
    internal val topicService: TopicService
) :
    StateAndEventSubscription<K, S, E> {

    companion object {
        private val log = contextLogger()
    }

    internal val stateSubscriptionConfig = subscriptionConfig.copy(
        eventTopic = "${subscriptionConfig.eventTopic}.state",
        groupName = "${subscriptionConfig.groupName}.state"
    )

    private val lock = ReentrantLock()
    private val eventSubscription: EventSubscription<K, S, E> = EventSubscription(this)
    internal val stateSubscription: StateSubscription<K, S> = StateSubscription(this)

    override val isRunning: Boolean
        get() = lock.withLock {
            eventSubscription.isRunning && stateSubscription.isRunning
        }

    override fun start() {
        log.debug { "Starting subscription with config: $subscriptionConfig" }

        lock.withLock {
            stateSubscription.start()
            eventSubscription.start()
        }
    }

    override fun stop() {
        lock.withLock {
            eventSubscription.stop()
            stateSubscription.stop()
        }
    }


    internal fun setValue(key: K, updatedState: S?, partition: Int) {
        stateSubscription.setValue(key, updatedState, partition)
    }
}
