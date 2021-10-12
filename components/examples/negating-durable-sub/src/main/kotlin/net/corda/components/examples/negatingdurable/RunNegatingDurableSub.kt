package net.corda.components.examples.negatingdurable

import com.typesafe.config.Config
import net.corda.components.examples.negatingdurable.processor.TestNegatingDurableProcessor
import net.corda.data.demo.DemoRecord
import net.corda.lifecycle.Lifecycle
import net.corda.messaging.api.subscription.Subscription
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.messaging.api.subscription.factory.config.SubscriptionConfig
import net.corda.v5.base.util.contextLogger
import org.osgi.service.component.annotations.Component
import org.slf4j.Logger

@Component
class RunNegatingDurableSub(
    private val subscriptionFactory: SubscriptionFactory,
    private var config: Config,
    private val instanceId: Int,
    private val killProcessOnRecord: Int = 0,
    private val delayOnNext: Long = 0,
    ) : Lifecycle {

    private companion object {
        val log: Logger = contextLogger()
        const val groupName = "negatingDurableGroup"
        const val inputTopic = "PublisherTopic"
        const val outputPubSubTopic = "PubsubTopic"
    }

    private var subscription: Subscription<String, DemoRecord>? = null

    override val isRunning: Boolean
        get() = subscription?.isRunning ?: false

    fun reStart(newConfig: Config) {
        log.info("Restarting negating durable subscription")
        stop()
        config = newConfig
        start()
    }

    override fun start() {
        if (!isRunning) {
            log.info("Creating negating durable subscription")
            val processor = TestNegatingDurableProcessor(outputPubSubTopic, delayOnNext)
            subscription = subscriptionFactory.createDurableSubscription(
                SubscriptionConfig(groupName, inputTopic, instanceId),
                processor,
                config,
                null
            )
            log.info("Starting negating durable subscription")
            subscription?.start()
        }
    }

    override fun stop() {
        log.info("Stopping negating durable sub")
        subscription?.stop()
    }
}