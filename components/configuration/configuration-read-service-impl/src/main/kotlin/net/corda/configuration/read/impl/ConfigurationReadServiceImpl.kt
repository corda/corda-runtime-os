package net.corda.configuration.read.impl

import net.corda.configuration.read.ConfigurationHandler
import net.corda.configuration.read.ConfigurationReadService
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.SmartConfigFactory
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.createCoordinator
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Component(service = [ConfigurationReadService::class])
class ConfigurationReadServiceImpl @Activate constructor(
    @Reference(service = LifecycleCoordinatorFactory::class)
    private val lifecycleCoordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = SubscriptionFactory::class)
    private val subscriptionFactory: SubscriptionFactory,
    @Reference(service = SmartConfigFactory::class)
    private val smartConfigFactory: SmartConfigFactory
) : ConfigurationReadService {

    private val eventHandler = ConfigReadServiceEventHandler(subscriptionFactory, smartConfigFactory)

    private val lifecycleCoordinator =
        lifecycleCoordinatorFactory.createCoordinator<ConfigurationReadService>(eventHandler)

    override fun bootstrapConfig(config: SmartConfig) {
        lifecycleCoordinator.postEvent(BootstrapConfigProvided(config))
    }

    override fun registerForUpdates(configHandler: ConfigurationHandler): AutoCloseable {
        if (isRunning) {
            val registration = ConfigurationChangeRegistration(lifecycleCoordinator, configHandler)
            lifecycleCoordinator.postEvent(ConfigRegistrationOpen(registration))
            return registration
        } else {
            throw IllegalArgumentException("Cannot register for config changes while the configuration read service " +
                    "is not running")
        }
    }

    override val isRunning: Boolean
        get() = lifecycleCoordinator.isRunning

    override fun start() {
        lifecycleCoordinator.start()
    }

    override fun stop() {
        lifecycleCoordinator.stop()
    }

    override fun close() {
        lifecycleCoordinator.close()
    }
}
