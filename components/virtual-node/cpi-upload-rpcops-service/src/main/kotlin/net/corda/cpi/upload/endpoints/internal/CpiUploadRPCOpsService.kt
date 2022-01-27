package net.corda.cpi.upload.endpoints.internal

import net.corda.configuration.read.ConfigurationReadService
import net.corda.cpi.upload.endpoints.CpiUploadRPCOpsService
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.Lifecycle
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.createCoordinator
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Component(service = [CpiUploadRPCOpsService::class], immediate = true)
class CpiUploadRPCOpsServiceImpl @Activate constructor(
    @Reference(service = CpiUploadRPCOpsInternal::class)
    cpiUploadRPCOps: CpiUploadRPCOpsInternal,
    @Reference(service = LifecycleCoordinatorFactory::class)
    coordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = ConfigurationReadService::class)
    configReadService: ConfigurationReadService
) : CpiUploadRPCOpsService {

    private val coordinator: LifecycleCoordinator

    init {
        val eventHandler = CpiUploadRPCOpsEventHandler(configReadService, cpiUploadRPCOps)
        coordinator = coordinatorFactory.createCoordinator<CpiUploadRPCOpsService>(eventHandler)
    }

    override val isRunning get() = coordinator.isRunning

    override fun start() {
        coordinator.start()
    }

    override fun stop() {
        coordinator.stop()
    }
}