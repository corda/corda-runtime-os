package net.corda.configuration.write.impl

import net.corda.configuration.write.ConfigWriteService
import net.corda.configuration.write.impl.writer.RPCSubscriptionFactory
import net.corda.configuration.write.publish.ConfigPublishService
import net.corda.db.connection.manager.DbConnectionManager
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.merger.ConfigMerger
import net.corda.libs.configuration.validation.ConfigurationValidatorFactory
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.createCoordinator
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.LoggerFactory

/** An implementation of [ConfigWriteService]. */
@Suppress("Unused", "LongParameterList")
@Component(service = [ConfigWriteService::class])
internal class ConfigWriteServiceImpl @Activate constructor(
    @Reference(service = LifecycleCoordinatorFactory::class)
    coordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = SubscriptionFactory::class)
    subscriptionFactory: SubscriptionFactory,
    @Reference(service = DbConnectionManager::class)
    dbConnectionManager: DbConnectionManager,
    @Reference(service = ConfigurationValidatorFactory::class)
    configurationValidatorFactory: ConfigurationValidatorFactory,
    @Reference(service = ConfigPublishService::class)
    configPublishService: ConfigPublishService,
    @Reference(service = ConfigMerger::class)
    configMerger: ConfigMerger
) : ConfigWriteService {

    companion object {
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    private val coordinator = let {
        val configWriterFactory = RPCSubscriptionFactory(
            subscriptionFactory,
            configurationValidatorFactory,
            dbConnectionManager,
            configPublishService
        )
        val eventHandler = ConfigWriteEventHandler(configWriterFactory, configMerger)
        coordinatorFactory.createCoordinator<ConfigWriteService>(eventHandler)
    }

    override fun bootstrapConfig(bootConfig: SmartConfig) {
        coordinator.postEvent(BootstrapConfigEvent(bootConfig))
    }

    override val isRunning get() = coordinator.isRunning

    override fun start() = coordinator.start()

    override fun stop() = coordinator.stop()
}