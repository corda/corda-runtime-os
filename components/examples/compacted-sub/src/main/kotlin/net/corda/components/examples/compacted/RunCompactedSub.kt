package net.corda.components.examples.compacted

import com.typesafe.config.Config
import net.corda.components.examples.compacted.processor.DemoCompactedProcessor
import net.corda.data.demo.DemoRecord
import net.corda.lifecycle.LifeCycle
import net.corda.messaging.api.subscription.Subscription
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.messaging.api.subscription.factory.config.SubscriptionConfig
import net.corda.v5.base.util.contextLogger
import org.osgi.service.component.annotations.Component
import org.slf4j.Logger

@Component
class RunCompactedSub(
    private val subscriptionFactory: SubscriptionFactory,
    private var config: Config
) : LifeCycle {

    private companion object {
        val log: Logger = contextLogger()
        const val groupName = "compactedGroup"
        const val topic = "configTopic"
    }

    private var subscription: Subscription<String, DemoRecord>? = null

    override val isRunning: Boolean
        get() = subscription?.isRunning ?: false

    fun reStart(newConfig: Config) {
        log.info("Restarting compacted subscription")
        stop()
        config = newConfig
        start()
    }

    override fun start() {
        if (!isRunning) {
            log.info("Creating compacted subscription")
            val processor = DemoCompactedProcessor()
            subscription = subscriptionFactory.createCompactedSubscription(
                SubscriptionConfig(groupName, topic),
                processor,
                mapOf()
            )

            log.info("Starting compacted subscription")
            subscription?.start()
        }
    }

    override fun stop() {
        log.info("Stopping compacted sub")
        subscription?.stop()
    }
}
