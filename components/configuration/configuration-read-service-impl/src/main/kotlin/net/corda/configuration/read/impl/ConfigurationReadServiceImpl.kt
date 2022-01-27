package net.corda.configuration.read.impl

import net.corda.configuration.read.ConfigurationHandler
import net.corda.configuration.read.ConfigurationReadService
import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.createCoordinator
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

/**
 * Configuration read service implementation.
 *
 * To use the configuration read service, the component should first be started at the top level. Bootstrap
 * configuration should then be provided to connect to the message bus configuration topic. Once connected, the
 * component will then mark itself as up. Other components can register on this to identify when to register for config
 * change (although the component should cope with registration at any time after startup).
 */
@Component(service = [ConfigurationReadService::class])
class ConfigurationReadServiceImpl @Activate constructor(
    @Reference(service = LifecycleCoordinatorFactory::class)
    private val lifecycleCoordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = SubscriptionFactory::class)
    private val subscriptionFactory: SubscriptionFactory
) : ConfigurationReadService {

    private val eventHandler = ConfigReadServiceEventHandler(subscriptionFactory)

    private val lifecycleCoordinator =
        lifecycleCoordinatorFactory.createCoordinator<ConfigurationReadService>(eventHandler)

    override fun bootstrapConfig(config: SmartConfig) {
        lifecycleCoordinator.postEvent(BootstrapConfigProvided(config))
    }

    override fun registerComponentForUpdates(coordinator: LifecycleCoordinator, requiredKeys: Set<String>): AutoCloseable {
        val handler = ComponentConfigHandler(coordinator, requiredKeys)
        return registerForUpdates(handler)
    }

    override fun registerForUpdates(configHandler: ConfigurationHandler): AutoCloseable {
        val registration = ConfigurationChangeRegistration(lifecycleCoordinator, configHandler)
        lifecycleCoordinator.postEvent(ConfigRegistrationAdd(registration))
        return registration
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
