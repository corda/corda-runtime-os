package net.corda.components.examples.compacted

import net.corda.components.examples.compacted.processor.DemoCompactedProcessor
import net.corda.data.demo.DemoRecord
import net.corda.lifecycle.LifeCycle
import net.corda.messaging.api.subscription.Subscription
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.messaging.api.subscription.factory.config.SubscriptionConfig
import org.osgi.service.component.annotations.Component
import org.slf4j.Logger
import org.slf4j.LoggerFactory

@Component
class RunCompactedSub(
    private val subscriptionFactory: SubscriptionFactory
) : LifeCycle {

    private companion object {
        val log: Logger = LoggerFactory.getLogger(this::class.java)
        const val groupName = "compactedGroup"
        const val topic = "configTopic"
    }

    private var subscription: Subscription<String, DemoRecord>? = null

    override val isRunning: Boolean
        get() = subscription?.isRunning ?: false

    override fun start() {
        val processor = DemoCompactedProcessor()
        subscription = subscriptionFactory.createCompactedSubscription(
            SubscriptionConfig(groupName, topic),
            processor,
            mapOf()
        )

        subscription?.start()
    }

    override fun stop() {
        subscription?.stop()
        log.info("Stopping compacted sub")
    }
}
