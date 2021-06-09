package net.corda.components.examples.pubsub

import net.corda.components.examples.pubsub.processor.DemoPubSubProcessor
import net.corda.data.demo.DemoRecord
import net.corda.lifecycle.LifeCycle
import net.corda.lifecycle.LifeCycleCoordinator
import net.corda.messaging.api.subscription.Subscription
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.messaging.api.subscription.factory.config.SubscriptionConfig
import org.osgi.service.component.annotations.Component
import org.slf4j.Logger
import org.slf4j.LoggerFactory

@Component
class RunPubSub(
    private val lifeCycleCoordinator: LifeCycleCoordinator,
    private val subscriptionFactory: SubscriptionFactory
) : LifeCycle {

    private companion object {
        val log: Logger = LoggerFactory.getLogger(this::class.java)
        const val groupName = "pubsubGroup"
        const val topic = "pubsubTopic"
    }

    private var subscription: Subscription<String, DemoRecord>? = null

    override var isRunning: Boolean = false

    override fun start() {
        isRunning = true
        val processor = DemoPubSubProcessor()
        subscription = subscriptionFactory.createPubSubSubscription(
            SubscriptionConfig(groupName, topic),
            processor,
            null,
            mapOf()
        )

        subscription?.start()
    }

    override fun stop() {
        isRunning = false
        subscription?.stop()
        log.info("Stopping pubsub sub.")
    }
}