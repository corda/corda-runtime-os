package net.corda.cpi.upload.endpoints.service

import net.corda.configuration.read.ConfigurationReadService
import net.corda.libs.cpiupload.CpiUploadManager
import net.corda.libs.cpiupload.CpiUploadManagerFactory
import net.corda.lifecycle.Lifecycle
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.createCoordinator
import net.corda.messaging.api.publisher.factory.PublisherFactory
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

// This service should be responsible of just setting [CpiUploadRPCOps] internal components (i.e [CpiUploadManager]),
// to be shared by [CpiUploadRPCOps] different versions.
@Component(service = [CpiUploadRPCOpsService::class])
class CpiUploadRPCOpsService @Activate constructor(
    @Reference(service = ConfigurationReadService::class)
    configReadService: ConfigurationReadService,
    @Reference(service = LifecycleCoordinatorFactory::class)
    coordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = PublisherFactory::class)
    publisherFactory: PublisherFactory,
    @Reference(service = CpiUploadManagerFactory::class)
    cpiUploadManagerFactory: CpiUploadManagerFactory
) : Lifecycle {

    private val handler = CpiUploadRPCOpsServiceHandler(
        cpiUploadManagerFactory,
        configReadService,
        publisherFactory
    )

    private val coordinator: LifecycleCoordinator = coordinatorFactory.createCoordinator<CpiUploadRPCOpsService>(
        handler
    )

    val cpiUploadManager: CpiUploadManager
        get() {
            checkNotNull(handler.cpiUploadManager) {
                "Cpi Upload Manager is null. Getter should be called only after service is UP."
            }
            return handler.cpiUploadManager!!
        }

    override val isRunning get() = coordinator.isRunning

    // It gets started by [RPCProcessorImpl].
    override fun start() {
        coordinator.start()
    }

    override fun stop() {
        coordinator.stop() // also sends stop event to coordinator
    }
}