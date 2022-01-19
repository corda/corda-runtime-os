package net.corda.components.examples.durable

import net.corda.components.examples.durable.processor.DemoDurableProcessor
import net.corda.data.demo.DemoRecord
import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.Lifecycle
import net.corda.messaging.api.subscription.Subscription
import net.corda.messaging.api.subscription.config.SubscriptionConfig
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.v5.base.util.contextLogger
import org.osgi.service.component.annotations.Component
import org.slf4j.Logger

@Component
class RunDurableSub(
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

    private var subscription: Subscription<String, DemoRecord>? = null

    override val isRunning: Boolean
        get() = subscription?.isRunning ?: false

    fun reStart(newConfig: SmartConfig) {
        log.info("Restarting durable subscription")
        stop()
        config = newConfig
        start()
    }

    override fun start() {
        if (!isRunning) {
            log.info("Creating durable subscription")
            val processor = DemoDurableProcessor(outputEventTopic, outputPubSubTopic, killProcessOnRecord, delayOnNext)
            subscription = subscriptionFactory.createDurableSubscription(
                SubscriptionConfig(groupName, inputTopic, instanceId),
                processor,
                config,
                null
            )
            log.info("Starting durable subscription")
            subscription?.start()
        }
    }

    override fun stop() {
        log.info("Stopping durable sub")
        subscription?.stop()
    }
}
