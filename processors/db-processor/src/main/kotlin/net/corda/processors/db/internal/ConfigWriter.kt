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

@Component(service = [ConfigWriter::class])
class ConfigWriter @Activate constructor(
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
        private const val CLIENT_ID = "joel" // TODO - Joel - Choose a proper client ID.
        private const val TOPIC = "config"
        private const val EVENT_TOPIC = "config-update-request"
    }

    internal val coordinator = coordinatorFactory.createCoordinator<ConfigWriter>(this).apply { start() }
    private val dbWriter = DBWriter(schemaMigrator, entityManagerFactoryFactory)
    private var instanceId: Int? = null
    private var bootstrapConfig: SmartConfig? = null
    private var newConfigRequestSub: Subscription<String, String>? = null
    private var newConfigPub: Publisher? = null

    override fun processEvent(event: LifecycleEvent, coordinator: LifecycleCoordinator) {
        when (event) {
            is StartEvent -> Unit // We cannot start until we have the required config.

            is ConfigProvidedEvent -> {
                // TODO - Joel - Introduce similar logic to config read service to be idempotent.
                instanceId = event.instanceId
                bootstrapConfig = event.config
                coordinator.postEvent(StartPubSubEvent())
            }

            is StartPubSubEvent -> {
                setUpPubSub()
                // TODO - Joel - Should I sleep here while waiting for DB to come up, if it's not up?
                coordinator.updateStatus(LifecycleStatus.UP)
            }

            is StopEvent -> {
                newConfigRequestSub?.stop()
                coordinator.updateStatus(LifecycleStatus.DOWN)
            }
        }
    }

    private fun setUpPubSub() {
        newConfigRequestSub?.stop()

        val config = bootstrapConfig ?: throw ConfigWriteException("TODO - Joel - Exception message.")

        if (newConfigPub != null || newConfigRequestSub != null) {
            throw ConfigWriteException("TODO - Joel - Exception message.")
        }

        val publisher = publisherFactory.createPublisher(PublisherConfig(CLIENT_ID, instanceId), config)
        val subscription = subscriptionFactory.createCompactedSubscription(
            SubscriptionConfig(GROUP_NAME, EVENT_TOPIC, instanceId),
            DBCompactedProcessor(::newConfigHandler),
            config
        )

        this.newConfigPub = publisher.apply { start() }
        this.newConfigRequestSub = subscription.apply { start() }
    }

    private fun newConfigHandler(newRecord: Record<String, String>) {
        // TODO - Joel - Don't default record value to empty string. Handle properly.
        val configEntity = ConfigEntity(newRecord.key, newRecord.value ?: "")
        dbWriter.writeConfig(configEntity)

        val record = Record(TOPIC, newRecord.key, newRecord.value)
        logger.info("JJJ publishing record $record")
        newConfigPub?.publish(listOf(record)) ?: throw ConfigWriteException("TODO - Joel - Exception message.")
    }
}