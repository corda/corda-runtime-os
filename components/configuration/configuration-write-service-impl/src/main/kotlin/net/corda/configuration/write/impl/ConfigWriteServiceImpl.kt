package net.corda.configuration.write.impl

import net.corda.configuration.write.ConfigWriteService
import net.corda.configuration.write.impl.writer.ConfigWriterFactory
import net.corda.db.connection.manager.DbConnectionManager
import net.corda.db.core.DbPrivilege
import net.corda.db.schema.CordaDb
import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.createCoordinator
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import javax.persistence.EntityManagerFactory

/** An implementation of [ConfigWriteService]. */
@Suppress("Unused")
@Component(service = [ConfigWriteService::class])
internal class ConfigWriteServiceImpl @Activate constructor(
    @Reference(service = LifecycleCoordinatorFactory::class)
    coordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = SubscriptionFactory::class)
    subscriptionFactory: SubscriptionFactory,
    @Reference(service = PublisherFactory::class)
    publisherFactory: PublisherFactory,
    @Reference(service = DbConnectionManager::class)
    dbConnectionManager: DbConnectionManager
) : ConfigWriteService {

    private val coordinator = let {
        val configWriterFactory = ConfigWriterFactory(
            subscriptionFactory,
            publisherFactory
        ) { dbConnectionManager.getOrCreateEntityManagerFactory(CordaDb.RBAC, DbPrivilege.DML) }
        val eventHandler = ConfigWriteEventHandler(configWriterFactory)
        coordinatorFactory.createCoordinator<ConfigWriteService>(eventHandler)
    }

    override fun startProcessing(config: SmartConfig, instanceId: Int, entityManagerFactory: EntityManagerFactory) {
        val startProcessingEvent = StartProcessingEvent(config, instanceId)
        coordinator.postEvent(startProcessingEvent)
    }

    override val isRunning get() = coordinator.isRunning

    override fun start() = coordinator.start()

    override fun stop() = coordinator.stop()
}