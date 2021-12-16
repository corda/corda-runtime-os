package net.corda.components.examples.persistence.config.admin

import net.corda.components.examples.persistence.config.admin.processor.ConfigAdminProcessor
import net.corda.data.poc.persistence.ConfigAdminEvent
import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.Lifecycle
import net.corda.messaging.api.subscription.Subscription
import net.corda.messaging.api.subscription.config.SubscriptionConfig
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import org.osgi.service.component.annotations.Component
import org.slf4j.Logger
import javax.persistence.EntityManagerFactory

@Component
class ConfigAdminSubscription(
    private val subscriptionFactory: SubscriptionFactory,
    private var config: SmartConfig,
    private val instanceId: Int,
    private val entityManagerFactory: EntityManagerFactory,
    private val logger: Logger,
    ) : Lifecycle {

    private companion object {
        const val groupName = "configAdminEventsGroup"
        const val inputTopic = "config-event"
        const val outputEventTopic = "config-state"
    }

    private var subscription: Subscription<String, ConfigAdminEvent>? = null

    override val isRunning: Boolean
        get() = subscription?.isRunning ?: false

    fun reStart(newConfig: SmartConfig) {
        logger.info("Restarting durable subscription for $inputTopic")
        stop()
        config = newConfig
        start()
    }

    override fun start() {
        if (!isRunning) {
            logger.info("Creating durable subscription for $inputTopic")
            val processor = ConfigAdminProcessor(outputEventTopic, entityManagerFactory, logger)
            subscription = subscriptionFactory.createDurableSubscription(
                // config-event
                SubscriptionConfig(groupName, inputTopic, instanceId),
                processor,
                config,
                null
            )
            logger.info("Starting durable subscription for $inputTopic")
            subscription?.start()
        }
    }

    override fun stop() {
        logger.info("Stopping durable sub")
        subscription?.stop()
    }
}
