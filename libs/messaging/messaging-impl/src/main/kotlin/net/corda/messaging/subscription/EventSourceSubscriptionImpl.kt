package net.corda.messaging.subscription

import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.messaging.api.subscription.Subscription
import net.corda.messaging.config.ResolvedSubscriptionConfig
import net.corda.utilities.debug
import org.slf4j.Logger

/**
 * The [EventSourceSubscriptionImpl] is responsible for controlling a background thread to drive the polling
 * of the [EventSourceConsumer].
 */
internal class EventSourceSubscriptionImpl<K : Any, V : Any>(
    private val config: ResolvedSubscriptionConfig,
    private val eventSourceConsumer: EventSourceConsumer<K, V>,
    threadLooperFactory: (String, () -> Unit) -> ThreadLooper,
    private val logger: Logger
) : Subscription<K, V> {

    private var threadLooper = threadLooperFactory(
        "event source processing thread",
        eventSourceConsumer::poll
    )

    override val isRunning: Boolean
        get() = threadLooper.isRunning

    override val subscriptionName: LifecycleCoordinatorName
        get() = threadLooper.lifecycleCoordinatorName

    override fun start() {
        logger.debug { "Starting subscription with config:\n${config}" }
        threadLooper.start()
    }

    override fun close() = threadLooper.close()
}