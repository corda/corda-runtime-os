package net.corda.crypto.service.impl._utils

import net.corda.configuration.read.ConfigurationHandler
import net.corda.configuration.read.ConfigurationReadService
import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.StartEvent
import org.mockito.kotlin.mock

class TestConfigurationReadService(
    coordinatorFactory: LifecycleCoordinatorFactory
) : ConfigurationReadService {
    private val coordinator = coordinatorFactory.createCoordinator(
        LifecycleCoordinatorName.forComponent<ConfigurationReadService>()
    ) { e, c -> if(e is StartEvent) { c.updateStatus(LifecycleStatus.UP) } }

    override fun registerForUpdates(configHandler: ConfigurationHandler): AutoCloseable =
        mock()

    override fun registerComponentForUpdates(
        coordinator: LifecycleCoordinator,
        requiredKeys: Set<String>
    ): AutoCloseable =
        mock()

    override fun bootstrapConfig(config: SmartConfig) {
    }

    override val isRunning: Boolean
        get() = coordinator.isRunning

    override fun start() {
        coordinator.start()
    }

    override fun stop() {
        coordinator.stop()
    }
}