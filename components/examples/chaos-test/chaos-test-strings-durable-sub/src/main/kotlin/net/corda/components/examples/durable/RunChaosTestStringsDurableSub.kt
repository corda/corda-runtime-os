package net.corda.components.examples.durable

import net.corda.components.examples.durable.processor.ChaosTestStringsDurableProcessor
import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.Lifecycle
import net.corda.messaging.api.subscription.Subscription
import net.corda.messaging.api.subscription.config.SubscriptionConfig
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.v5.base.util.contextLogger
import org.osgi.service.component.annotations.Component
import org.slf4j.Logger

@Component
class RunChaosTestStringsDurableSub(
    private val subscriptionFactory: SubscriptionFactory,
    private var config: SmartConfig,
    private val instanceId: Int,
    private val killProcessOnRecord: Int = 0,
    private val delayOnNext: Long = 0,
) : Lifecycle {

    private companion object {
        val log: Logger = contextLogger()
        const val groupName = "durableGroup"
        const val inputTopic = "PublisherTopic"
        const val outputEventTopic = "EventTopic"
        const val outputPubSubTopic = "PubsubTopic"
    }

    private var subscription: Subscription<String, String>? = null

    override val isRunning: Boolean
        get() = subscription?.isRunning ?: false

    fun reStart(newConfig: SmartConfig) {
        log.info("Restarting chaos test strings durable subscription")
        stop()
        config = newConfig
        start()
    }

    override fun start() {
        if (!isRunning) {
            log.info("Creating chaos test strings durable subscription")
            val processor = ChaosTestStringsDurableProcessor(outputEventTopic, outputPubSubTopic, killProcessOnRecord, delayOnNext)
            subscription = subscriptionFactory.createDurableSubscription(
                SubscriptionConfig(groupName, inputTopic, instanceId),
                processor,
                config,
                null
            )
            log.info("Starting chaos strings durable subscription")
            subscription?.start()
        }
    }

    override fun stop() {
        log.info("Stopping chaos test strings durable sub")
        subscription?.stop()
    }
}
