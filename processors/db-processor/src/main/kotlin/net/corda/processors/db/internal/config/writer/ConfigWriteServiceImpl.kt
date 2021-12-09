package net.corda.processors.db.internal.config.writer

import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.createCoordinator
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

/** An implementation of [ConfigWriteService]. */
@Suppress("Unused")
@Component(service = [ConfigWriteService::class])
class ConfigWriteServiceImpl @Activate constructor(
    @Reference(service = LifecycleCoordinatorFactory::class)
    coordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = ConfigWriterSubscriptionFactory::class)
    configWriterSubscriptionFactory: ConfigWriterSubscriptionFactory
) : ConfigWriteService {

    private val coordinator = coordinatorFactory.createCoordinator<ConfigWriteService>(
        ConfigWriteEventHandler(configWriterSubscriptionFactory)
    )

    override fun bootstrapConfig(config: SmartConfig, instanceId: Int) =
        coordinator.postEvent(SubscribeEvent(config, instanceId))

    override val isRunning get() = coordinator.isRunning

    override fun start() = coordinator.start()

    override fun stop() = coordinator.stop()
}