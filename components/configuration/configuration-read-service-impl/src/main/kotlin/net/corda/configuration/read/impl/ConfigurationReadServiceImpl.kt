package net.corda.configuration.read.impl

import net.corda.configuration.read.ConfigurationGetService
import net.corda.configuration.read.ConfigurationHandler
import net.corda.configuration.read.ConfigurationReadService
import net.corda.configuration.read.reconcile.ConfigReconcilerReader
import net.corda.data.config.Configuration
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.merger.ConfigMerger
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.Resource
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.reconciliation.VersionedRecord
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Deactivate
import org.osgi.service.component.annotations.Reference
import java.util.stream.Stream

/**
 * Configuration read service implementation.
 *
 * To use the configuration read service, the component should first be started at the top level. Bootstrap
 * configuration should then be provided to connect to the message bus configuration topic. Once connected, the
 * component will then mark itself as up. Other components can register on this to identify when to register for config
 * change (although the component should cope with registration at any time after startup).
 */
@Component(service = [ConfigurationReadService::class, ConfigReconcilerReader::class, ConfigurationGetService::class])
class ConfigurationReadServiceImpl @Activate constructor(
    @Reference(service = LifecycleCoordinatorFactory::class)
    private val lifecycleCoordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = SubscriptionFactory::class)
    private val subscriptionFactory: SubscriptionFactory,
    @Reference(service = ConfigMerger::class)
    private val configMerger: ConfigMerger
) : ConfigurationReadService, ConfigReconcilerReader, ConfigurationGetService {

    private val eventHandler = ConfigReadServiceEventHandler(subscriptionFactory, configMerger)

    override val lifecycleCoordinatorName =
        LifecycleCoordinatorName.forComponent<ConfigurationReadService>()

    private val lifecycleCoordinator =
        lifecycleCoordinatorFactory.createCoordinator(lifecycleCoordinatorName, eventHandler)

    override fun bootstrapConfig(config: SmartConfig) {
        lifecycleCoordinator.postEvent(BootstrapConfigProvided(config))
    }

    override fun registerComponentForUpdates(coordinator: LifecycleCoordinator, requiredKeys: Set<String>): Resource {
        val handler = ComponentConfigHandler(coordinator, requiredKeys)
        return registerForUpdates(handler)
    }

    override fun registerForUpdates(configHandler: ConfigurationHandler): Resource {
        val registration = ConfigurationChangeRegistration(lifecycleCoordinator, configHandler)
        lifecycleCoordinator.postEvent(ConfigRegistrationAdd(registration))
        return registration
    }

    private val configProcessor: ConfigProcessor
        get() =
            eventHandler.configProcessor ?: throw IllegalStateException("Config read service configProcessor is null")

    override fun getAllVersionedRecords(): Stream<VersionedRecord<String, Configuration>> =
        configProcessor.getAllVersionedRecords()


    override fun get(section: String): Configuration? {
        return configProcessor.get(section)
    }

    override fun invoke(section: String): SmartConfig? {
        return configProcessor.getSmartConfig(section)
    }

    override val isRunning: Boolean
        get() = lifecycleCoordinator.isRunning

    override fun start() {
        lifecycleCoordinator.start()
    }

    override fun stop() {
        lifecycleCoordinator.stop()
    }

    @Deactivate
    fun close() {
        lifecycleCoordinator.close()
    }
}
