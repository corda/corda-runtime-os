package net.corda.components.examples.persistence.config.admin

import com.typesafe.config.Config
import net.corda.lifecycle.Lifecycle
import net.corda.messaging.api.subscription.Subscription
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.messaging.api.subscription.factory.config.SubscriptionConfig
import net.corda.v5.base.util.contextLogger
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.slf4j.Logger
import java.sql.Connection

@Component
class ConfigAppSubscription @Activate constructor(
    private val subscriptionFactory: SubscriptionFactory,
    private var config: Config,
    private val instanceId: Int,
    private val dbConnection: Connection,
    private val delayOnNext: Long = 0,
    ) : Lifecycle {

    private companion object {
        val log: Logger = contextLogger()
        const val groupName = "configAdminEventsGroup"
        const val inputTopic = "config-event"
        const val outputEventTopic = "config-state"
    }

    private var subscription: Subscription<String, String>? = null

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
            val processor = ConfigDurableProcessor(outputEventTopic, dbConnection, log)
            subscription = subscriptionFactory.createDurableSubscription(
                // config-event
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