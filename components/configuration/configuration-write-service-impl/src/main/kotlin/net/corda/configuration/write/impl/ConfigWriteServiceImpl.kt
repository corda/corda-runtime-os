package net.corda.configuration.write.impl

import javax.persistence.EntityManagerFactory
import net.corda.configuration.write.ConfigWriteService
import net.corda.configuration.write.impl.writer.ConfigWriterFactory
import net.corda.db.connection.manager.DbConnectionManager
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.merger.ConfigMerger
import net.corda.libs.configuration.validation.ConfigurationValidatorFactory
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.createCoordinator
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.v5.base.util.contextLogger
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

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
    dbConnectionManager: DbConnectionManager,
    @Reference(service = ConfigurationValidatorFactory::class)
    configurationValidatorFactory: ConfigurationValidatorFactory,
    @Reference(service = ConfigMerger::class)
    private val configMerger: ConfigMerger
) : ConfigWriteService {

    companion object {
        private val logger = contextLogger()
    }

    private val coordinator = let {
        val configWriterFactory = ConfigWriterFactory(subscriptionFactory, publisherFactory, configurationValidatorFactory,
            dbConnectionManager)
        val eventHandler = ConfigWriteEventHandler(configWriterFactory)
        coordinatorFactory.createCoordinator<ConfigWriteService>(eventHandler)
    }

    override fun startProcessing(bootConfig: SmartConfig, entityManagerFactory: EntityManagerFactory) {
        val startProcessingEvent = StartProcessingEvent(configMerger.getMessagingConfig(bootConfig))
        coordinator.postEvent(startProcessingEvent)
    }

    override val isRunning get() = coordinator.isRunning

    override fun start() = coordinator.start()

    override fun stop() = coordinator.stop()
}