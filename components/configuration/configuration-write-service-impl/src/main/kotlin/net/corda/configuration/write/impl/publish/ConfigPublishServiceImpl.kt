package net.corda.configuration.write.impl.publish

import net.corda.configuration.write.publish.ConfigPublishService
import net.corda.data.config.Configuration
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.merger.ConfigMerger
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.records.Record
import net.corda.schema.Schemas.Config.Companion.CONFIG_TOPIC
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

// This needs to be a `Lifecycle` for reconciliation, maybe not only for that. However it cannot really wait on
// `ConfigurationReadService`, because it will be used by `ConfigWriteService` which needs to be started before
// `ConfigurationReadService`. Otherwise there is going to be `ConfigWriteService` waiting for `ConfigurationReadService`
// and `ConfigurationReadService` waiting for `ConfigWriteService` since it needs it to feed `config.topic`.
@Component(service = [ConfigPublishService::class])
class ConfigPublishServiceImpl @Activate constructor(
    @Reference(service = LifecycleCoordinatorFactory::class)
    coordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = PublisherFactory::class)
    publisherFactory: PublisherFactory,
    @Reference(service = ConfigMerger::class)
    configMerger: ConfigMerger
) : ConfigPublishService {

    private val handler = ConfigPublishServiceHandler(publisherFactory, configMerger)

    override val lifecycleCoordinatorName = LifecycleCoordinatorName.forComponent<ConfigPublishService>()

    private val coordinator = coordinatorFactory.createCoordinator(
        lifecycleCoordinatorName,
        handler
    )

    private val publisher: Publisher
        get() =
            handler.publisher ?: throw IllegalStateException("Config publish service publisher is null")

    override fun put(configSection: String, config: Configuration) {

        // TODO - CORE-3404 - Check new config against current Kafka config to avoid overwriting.
        val futures = publisher.publish(listOf(Record(CONFIG_TOPIC, configSection, config)))

        // TODO - CORE-3730 - Define timeout policy.
        futures.first().get()
    }

    override fun bootstrapConfig(bootConfig: SmartConfig) {
        coordinator.postEvent(BootstrapConfigEvent(bootConfig))
    }

    @Suppress("parameter_name_changed_on_override")
    override fun remove(configSection: String) {
        TODO("Not yet implemented")
    }

    override val isRunning get() = coordinator.isRunning

    override fun start() = coordinator.start()

    override fun stop() = coordinator.stop()
}