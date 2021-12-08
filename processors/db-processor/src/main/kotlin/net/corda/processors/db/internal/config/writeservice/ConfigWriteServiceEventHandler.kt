package net.corda.processors.db.internal.config.writeservice

import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.LifecycleEventHandler
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import net.corda.messaging.api.subscription.Subscription
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.messaging.api.subscription.factory.config.SubscriptionConfig
import net.corda.processors.db.internal.config.writer.ConfigWriterProcessor
import net.corda.processors.db.internal.db.DBWriter
import net.corda.v5.base.util.contextLogger

class ConfigWriteServiceEventHandler(
    private val dbWriter: DBWriter,
    private val subscriptionFactory: SubscriptionFactory
) : LifecycleEventHandler {

    private companion object {
        private val logger = contextLogger()
    }

    private var instanceId: Int? = null
    private var bootstrapConfig: SmartConfig? = null
    private var subscription: Subscription<String, String>? = null

    override fun processEvent(event: LifecycleEvent, coordinator: LifecycleCoordinator) {
        when (event) {
            is StartEvent -> Unit // We cannot start until we have the required config.

            is BootstrapConfigEvent -> {
                logger.info("JJJ received bootstrap event")
                // TODO - Joel - Introduce similar logic to config read service to be idempotent.
                instanceId = event.instanceId
                bootstrapConfig = event.config
                coordinator.postEvent(SubscribeEvent())
            }

            is SubscribeEvent -> {
                logger.info("JJJ received subscribe event")
                subscribe()
                // TODO - Joel - Should I sleep here while waiting for DB to come up, if it's not up?
                coordinator.updateStatus(LifecycleStatus.UP)
            }

            is StopEvent -> {
                subscription?.stop()
                coordinator.updateStatus(LifecycleStatus.DOWN)
            }
        }
    }

    private fun subscribe() {
        val config = bootstrapConfig ?: throw ConfigWriteServiceException("TODO - Joel - Exception message.")
        if (subscription != null) throw ConfigWriteServiceException("TODO - Joel - Exception message.")


        val subscriptionConfig = SubscriptionConfig(GROUP_NAME, CONFIG_UPDATE_REQUEST_TOPIC, instanceId)
        // TODO - Joel - Processor should be created from factory so we can use either a Kafka or non-Kafka impl.
        val subscription = subscriptionFactory.createDurableSubscription(
            subscriptionConfig, ConfigWriterProcessor(dbWriter), config, null
        )

        this.subscription = subscription
        subscription.start()
    }
}