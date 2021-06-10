package net.corda.components.examples.durable

import net.corda.components.examples.durable.processor.DemoPubSubProcessor
import net.corda.data.demo.DemoRecord
import net.corda.lifecycle.LifeCycle
import net.corda.messaging.api.subscription.Subscription
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.messaging.api.subscription.factory.config.SubscriptionConfig
import org.osgi.service.component.annotations.Component
import org.slf4j.Logger
import org.slf4j.LoggerFactory

@Component
class RunDurableSub(
    private val subscriptionFactory: SubscriptionFactory,
    private val instanceId: Int
    ) : LifeCycle {

    private companion object {
        val log: Logger = LoggerFactory.getLogger(this::class.java)
        const val groupName = "durableGroup"
        const val inputTopic = "publisherTopic"
        const val outputEventTopic = "eventTopic"
        const val outputPubSubTopic = "pubsubTopic"
    }

    private var subscription: Subscription<String, DemoRecord>? = null

    override val isRunning: Boolean
        get() = subscription?.isRunning ?: false

    override fun start() {
        val processor = DemoPubSubProcessor(outputEventTopic, outputPubSubTopic)
        subscription = subscriptionFactory.createDurableSubscription(
            SubscriptionConfig(groupName, inputTopic, instanceId),
            processor,
            mapOf(),
            null
        )

        subscription?.start()
    }

    override fun stop() {
        subscription?.stop()
        log.info("Stopping durable sub")
    }
}