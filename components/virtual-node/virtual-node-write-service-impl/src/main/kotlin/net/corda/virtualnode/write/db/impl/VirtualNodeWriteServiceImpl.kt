package net.corda.virtualnode.write.db.impl

import net.corda.libs.configuration.SmartConfig
import net.corda.libs.virtualnode.write.VirtualNodeWriterFactory
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.createCoordinator
import net.corda.virtualnode.write.db.VirtualNodeWriteService
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import javax.persistence.EntityManagerFactory

/** An implementation of [VirtualNodeWriteService]. */
@Suppress("Unused")
@Component(service = [VirtualNodeWriteService::class])
internal class VirtualNodeWriteServiceImpl @Activate constructor(
    @Reference(service = LifecycleCoordinatorFactory::class)
    coordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = VirtualNodeWriterFactory::class)
    virtualNodeWriterFactory: VirtualNodeWriterFactory
) : VirtualNodeWriteService {

    private val coordinator = let {
        val eventHandler = VirtualNodeWriteEventHandler(virtualNodeWriterFactory)
        coordinatorFactory.createCoordinator<VirtualNodeWriteService>(eventHandler)
    }

    override fun startProcessing(config: SmartConfig, instanceId: Int, entityManagerFactory: EntityManagerFactory) {
        val startProcessingEvent = StartProcessingEvent(config, instanceId, entityManagerFactory)
        coordinator.postEvent(startProcessingEvent)
    }

    override val isRunning get() = coordinator.isRunning

    override fun start() = coordinator.start()

    override fun stop() = coordinator.stop()
}