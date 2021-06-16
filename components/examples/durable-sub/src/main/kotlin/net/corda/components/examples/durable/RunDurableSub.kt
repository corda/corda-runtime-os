package net.corda.components.examples.durable

import com.typesafe.config.Config
import net.corda.components.examples.durable.processor.DemoDurableProcessor
import net.corda.data.demo.DemoRecord
import net.corda.lifecycle.LifeCycle
import net.corda.lifecycle.LifeCycleCoordinator
import net.corda.messaging.api.subscription.Subscription
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.messaging.api.subscription.factory.config.SubscriptionConfig
import net.corda.v5.base.util.contextLogger
import org.osgi.service.component.annotations.Component
import org.slf4j.Logger

@Component
class RunDurableSub(
    private val lifeCycleCoordinator: LifeCycleCoordinator,
    private val subscriptionFactory: SubscriptionFactory,
    private var config: Config,
    private val instanceId: Int,
    private val killProcessOnRecord: Int = 0
    ) : LifeCycle {

    private companion object {
        val log: Logger = contextLogger()
        const val groupName = "durableGroup"
        const val inputTopic = "publisherTopic"
        const val outputEventTopic = "eventTopic"
        const val outputPubSubTopic = "pubsubTopic"
    }

    private var subscription: Subscription<String, DemoRecord>? = null

    override val isRunning: Boolean
        get() = subscription?.isRunning ?: false

    fun reStart(newConfig: Config) {
        log.info("Restarting durable subscription")
        stop()
        config = newConfig
        start()
    }

    override fun start() {
        if (!isRunning) {
            log.info("Creating durable subscription")
            val processor = DemoDurableProcessor(outputEventTopic, outputPubSubTopic, killProcessOnRecord)
            subscription = subscriptionFactory.createDurableSubscription(
                SubscriptionConfig(groupName, inputTopic, instanceId),
                processor,
                mapOf(),
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