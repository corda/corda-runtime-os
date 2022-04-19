package net.corda.crypto.service.impl.infra

import net.corda.configuration.read.ConfigChangedEvent
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
    coordinatorFactory: LifecycleCoordinatorFactory,
    private val configUpdates: List<Pair<String, SmartConfig>>
) : ConfigurationReadService {
    val coordinator = coordinatorFactory.createCoordinator(
        LifecycleCoordinatorName.forComponent<ConfigurationReadService>()
    ) { e, c -> if(e is StartEvent) { c.updateStatus(LifecycleStatus.UP) } }

    override fun registerForUpdates(configHandler: ConfigurationHandler): AutoCloseable =
        mock()

    override fun registerComponentForUpdates(
        coordinator: LifecycleCoordinator,
        requiredKeys: Set<String>
    ): AutoCloseable {
        if(configUpdates.isNotEmpty()) {
            coordinator.postEvent(
                ConfigChangedEvent(
                    configUpdates.map { it.first }.toSet(),
                    configUpdates.toMap()
                )
            )
        }
        return mock()
    }

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