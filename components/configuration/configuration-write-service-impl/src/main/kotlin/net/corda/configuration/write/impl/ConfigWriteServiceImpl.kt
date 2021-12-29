package net.corda.configuration.write.impl

import net.corda.configuration.write.ConfigWriteService
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.write.ConfigWriterFactory
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.createCoordinator
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import javax.persistence.EntityManagerFactory

/** An implementation of [ConfigWriteService]. */
@Suppress("Unused")
@Component(service = [ConfigWriteService::class])
internal class ConfigWriteServiceImpl @Activate constructor(
    @Reference(service = LifecycleCoordinatorFactory::class)
    coordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = ConfigWriterFactory::class)
    configWriterFactory: ConfigWriterFactory
) : ConfigWriteService {

    private val coordinator = let {
        val eventHandler = ConfigWriteEventHandler(configWriterFactory)
        coordinatorFactory.createCoordinator<ConfigWriteService>(eventHandler)
    }

    override fun startProcessing(config: SmartConfig, instanceId: Int, entityManagerFactory: EntityManagerFactory) {
        val startProcessingEvent = StartProcessingEvent(config, instanceId, entityManagerFactory)
        coordinator.postEvent(startProcessingEvent)
    }

    override val isRunning get() = coordinator.isRunning

    override fun start() = coordinator.start()

    override fun stop() = coordinator.stop()
}