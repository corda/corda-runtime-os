package net.corda.processors.db.internal.config

import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.LifecycleEventHandler
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import net.corda.lifecycle.createCoordinator
import net.corda.messaging.api.records.Record
import net.corda.messaging.api.subscription.Subscription
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.messaging.api.subscription.factory.config.SubscriptionConfig
import net.corda.processors.db.internal.db.DBWriter
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Component(service = [ConfigWriter::class])
class ConfigWriter @Activate constructor(
    @Reference(service = LifecycleCoordinatorFactory::class)
    private val coordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = DBWriter::class)
    private val dbWriter: DBWriter,
    @Reference(service = SubscriptionFactory::class)
    private val subscriptionFactory: SubscriptionFactory
) : LifecycleEventHandler {

    internal val coordinator = coordinatorFactory.createCoordinator<ConfigWriter>(this).apply { start() }
    private var instanceId: Int? = null
    private var bootstrapConfig: SmartConfig? = null
    private var subscription: Subscription<String, String>? = null

    override fun processEvent(event: LifecycleEvent, coordinator: LifecycleCoordinator) {
        when (event) {
            is StartEvent -> Unit // We cannot start until we have the required config.

            is BootstrapConfigEvent -> {
                // TODO - Joel - Introduce similar logic to config read service to be idempotent.
                instanceId = event.instanceId
                bootstrapConfig = event.config
                coordinator.postEvent(SubscribeEvent())
            }

            is SubscribeEvent -> {
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
        val config = bootstrapConfig ?: throw ConfigWriteException("TODO - Joel - Exception message.")
        if (subscription != null) throw ConfigWriteException("TODO - Joel - Exception message.")

        val subscriptionConfig = SubscriptionConfig(GROUP_NAME, CONFIG_UPDATE_REQUEST_TOPIC, instanceId)
        val subscription = subscriptionFactory.createDurableSubscription(
            subscriptionConfig, ConfigWriterProcessor(dbWriter), config, null
        )

        this.subscription = subscription
        subscription.start()
    }
}