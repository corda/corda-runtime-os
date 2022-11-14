package net.corda.utxo.token.sync.integration.tests.fakes

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
import net.corda.lifecycle.Resource
import net.corda.lifecycle.StartEvent
import net.corda.schema.configuration.ConfigKeys
import org.mockito.kotlin.mock

val emptyConfig: SmartConfig =
    SmartConfigFactory.create(ConfigFactory.empty()).create(ConfigFactory.empty())

class TestConfigurationReadService(
    coordinatorFactory: LifecycleCoordinatorFactory,
    val configUpdates: MutableList<Pair<String, SmartConfig>> = mutableListOf(
        ConfigKeys.BOOT_CONFIG to emptyConfig,
        ConfigKeys.MESSAGING_CONFIG to emptyConfig
    )
) : ConfigurationReadService {
    val lifecycleCoordinator = coordinatorFactory.createCoordinator(
        LifecycleCoordinatorName.forComponent<ConfigurationReadService>()
    ) { e, c -> if(e is StartEvent) { c.updateStatus(LifecycleStatus.UP) } }

    fun reissueConfigChangedEvent(coordinator: LifecycleCoordinator) {
        if(configUpdates.isNotEmpty()) {
            coordinator.postEvent(
                ConfigChangedEvent(
                    configUpdates.map { it.first }.toSet(),
                    configUpdates.toMap()
                )
            )
        }
    }

    override fun registerForUpdates(configHandler: ConfigurationHandler): Resource =
        mock()

    override fun registerComponentForUpdates(
        coordinator: LifecycleCoordinator,
        requiredKeys: Set<String>
    ): Resource {
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
        get() = lifecycleCoordinator.isRunning

    override fun start() {
        lifecycleCoordinator.start()
    }

    override fun stop() {
        lifecycleCoordinator.stop()
    }
}