package net.corda.configuration.read.impl

import net.corda.configuration.read.ConfigurationHandler
import net.corda.configuration.read.ConfigurationReadService
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.read.factory.ConfigReaderFactory
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.createCoordinator
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Component(service = [ConfigurationReadService::class])
class ConfigurationReadServiceImpl @Activate constructor(
    @Reference(service = LifecycleCoordinatorFactory::class)
    private val lifecycleCoordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = ConfigReaderFactory::class)
    private val readServiceFactory: ConfigReaderFactory
) : ConfigurationReadService {

    private val callbackHandles = ConfigurationHandlerStorage()
    private val eventHandler = ConfigReadServiceEventHandler(readServiceFactory, callbackHandles)

    private val lifecycleCoordinator =
        lifecycleCoordinatorFactory.createCoordinator<ConfigurationReadService>(eventHandler)

    override fun bootstrapConfig(config: SmartConfig) {
        lifecycleCoordinator.postEvent(BootstrapConfigProvided(config))
    }

    override fun registerForUpdates(configHandler: ConfigurationHandler): AutoCloseable {
        return callbackHandles.add(configHandler)
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
