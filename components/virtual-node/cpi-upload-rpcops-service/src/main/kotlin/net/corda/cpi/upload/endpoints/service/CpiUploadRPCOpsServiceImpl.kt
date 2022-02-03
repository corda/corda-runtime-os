package net.corda.cpi.upload.endpoints.service

import net.corda.configuration.read.ConfigurationReadService
import net.corda.cpi.upload.endpoints.common.CpiUploadManager
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.createCoordinator
import net.corda.messaging.api.publisher.factory.PublisherFactory
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

// This service should be responsible of just setting [CpiUploadRPCOps] internal components (i.e [CpiUploadManager]),
// to be shared by [CpiUploadRPCOps] different versions.
@Component(service = [CpiUploadRPCOpsService::class], immediate = true)
class CpiUploadRPCOpsServiceImpl @Activate constructor(
    @Reference(service = CpiUploadManager::class)
    cpiUploadManager: CpiUploadManager,
    @Reference(service = ConfigurationReadService::class)
    configReadService: ConfigurationReadService,
    @Reference(service = LifecycleCoordinatorFactory::class)
    coordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = PublisherFactory::class)
    publisherFactory: PublisherFactory
) : CpiUploadRPCOpsService {

    // This configuration must
    //  1. set [CpiUploadManager].
    //  2. have [CpiUploadRPCOpsImpl] listen and wait for [CpiUploadManager] to be ready (?).
    private val coordinator: LifecycleCoordinator = coordinatorFactory.createCoordinator<CpiUploadRPCOpsService>(
        CpiUploadRPCOpsServiceHandler(
            cpiUploadManager,
            configReadService,
            publisherFactory
        )
    )

    override val isRunning get() = coordinator.isRunning

    // It gets started by [RPCProcessorImpl].
    override fun start() {
        coordinator.start()
    }

    override fun stop() {
        coordinator.stop() // also sends stop event to coordinator
    }
}