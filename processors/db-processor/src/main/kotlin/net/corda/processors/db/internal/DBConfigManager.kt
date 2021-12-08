package net.corda.processors.db.internal

import net.corda.db.admin.LiquibaseSchemaMigrator
import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.LifecycleEventHandler
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import net.corda.lifecycle.createCoordinator
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.records.Record
import net.corda.messaging.api.subscription.Subscription
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.messaging.api.subscription.factory.config.SubscriptionConfig
import net.corda.orm.EntityManagerFactoryFactory
import net.corda.v5.base.util.contextLogger
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Component(service = [DBConfigManager::class])
class DBConfigManager @Activate constructor(
    @Reference(service = LifecycleCoordinatorFactory::class)
    private val coordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = SubscriptionFactory::class)
    private val subscriptionFactory: SubscriptionFactory,
    @Reference(service = PublisherFactory::class)
    private val publisherFactory: PublisherFactory,
    @Reference(service = EntityManagerFactoryFactory::class)
    entityManagerFactoryFactory: EntityManagerFactoryFactory,
    @Reference(service = LiquibaseSchemaMigrator::class)
    schemaMigrator: LiquibaseSchemaMigrator
) : LifecycleEventHandler {

    private companion object {
        private val logger = contextLogger()
        private const val GROUP_NAME = "DB_EVENT_HANDLER"
    }

    internal val coordinator = coordinatorFactory.createCoordinator<DBConfigManager>(this).apply { start() }
    private val dbWriter = DBWriter(schemaMigrator, entityManagerFactoryFactory)
    private var newConfigRequestSub: Subscription<String, String>? = null
    private var newConfigPub: Publisher? = null

    override fun processEvent(event: LifecycleEvent, coordinator: LifecycleCoordinator) {
        when (event) {
            is StartEvent -> Unit // We cannot start until we have the required Kafka config.
            is StartListentingEvent -> {
                // The component is temporarily down while we reconfigure it.
                coordinator.updateStatus(LifecycleStatus.DOWN)
                listenForConfig(event.instanceId, event.config)
                // TODO - Joel - Should I sleep here while waiting for DB to come up, if it's not up?
                coordinator.updateStatus(LifecycleStatus.UP)
            }
            is StopEvent -> newConfigRequestSub?.stop()
        }
    }

    @Synchronized
    private fun listenForConfig(instanceId: Int, config: SmartConfig) {
        newConfigRequestSub?.stop()

        // TODO - Joel - Choose a client ID.
        val publisher = publisherFactory.createPublisher(PublisherConfig("joel", instanceId), config)
        val subscription = subscriptionFactory.createCompactedSubscription(
            SubscriptionConfig(GROUP_NAME, "config-update-request", instanceId),
            DBCompactedProcessor(::newConfigHandler),
            config
        )

        this.newConfigPub = publisher.apply { start() }
        this.newConfigRequestSub = subscription.apply { start() }
    }

    private fun newConfigHandler(newRecord: Record<String, String>) {
        val configEntity = ConfigEntity(newRecord.key, newRecord.value ?: "")
        dbWriter.writeConfig(configEntity)

        val record = Record("config", newRecord.key, newRecord.value)
        logger.info("JJJ publishing record $record")
        // TODO - Handle publisher being null.
        newConfigPub?.publish(listOf(record))
    }
}

// TODO - Joel - Move to another file and nest.
internal class StartListentingEvent(val instanceId: Int, val config: SmartConfig) : LifecycleEvent