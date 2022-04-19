package net.corda.crypto.client.impl.infra

import com.typesafe.config.ConfigFactory
import net.corda.configuration.read.ConfigChangedEvent
import net.corda.configuration.read.ConfigurationHandler
import net.corda.configuration.read.ConfigurationReadService
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.SmartConfigFactory
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.StartEvent
import net.corda.schema.configuration.ConfigKeys
import org.mockito.kotlin.mock

val emptyConfig: SmartConfig =
    SmartConfigFactory.create(ConfigFactory.empty()).create(ConfigFactory.empty())

class TestConfigurationReadService(
    coordinatorFactory: LifecycleCoordinatorFactory,
    private val configUpdates: List<Pair<String, SmartConfig>> = listOf(
        ConfigKeys.BOOT_CONFIG to emptyConfig,
        ConfigKeys.MESSAGING_CONFIG to emptyConfig
    )
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