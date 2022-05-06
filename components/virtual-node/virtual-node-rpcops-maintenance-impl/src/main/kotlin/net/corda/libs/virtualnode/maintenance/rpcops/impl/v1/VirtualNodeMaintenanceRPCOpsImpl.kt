package net.corda.libs.virtualnode.maintenance.rpcops.impl.v1

import net.corda.cpi.upload.endpoints.service.CpiUploadRPCOpsService
import net.corda.httprpc.HttpFileUpload
import net.corda.httprpc.PluggableRPCOps
import net.corda.libs.cpiupload.endpoints.v1.CpiUploadRPCOps
import net.corda.libs.virtualnode.maintenance.endpoints.v1.VirtualNodeMaintenanceRPCOps
import net.corda.lifecycle.Lifecycle
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.createCoordinator
import net.corda.v5.base.util.contextLogger
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Suppress("unused")
@Component(service = [PluggableRPCOps::class])
class VirtualNodeMaintenanceRPCOpsImpl @Activate constructor(
    @Reference(service = LifecycleCoordinatorFactory::class)
    coordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = CpiUploadRPCOpsService::class)
    private val cpiUploadRPCOpsService: CpiUploadRPCOpsService
) : VirtualNodeMaintenanceRPCOps, PluggableRPCOps<VirtualNodeMaintenanceRPCOps>, Lifecycle {

    companion object {
        val logger = contextLogger()
    }

    private val coordinator = coordinatorFactory.createCoordinator<VirtualNodeMaintenanceRPCOps>(
        VirtualNodeMaintenanceRPCOpsHandler()
    )

    override val protocolVersion: Int = 1

    override val targetInterface: Class<VirtualNodeMaintenanceRPCOps> = VirtualNodeMaintenanceRPCOps::class.java

    override val isRunning get() = coordinator.isRunning

    private val cpiUploadManager get() = cpiUploadRPCOpsService.cpiUploadManager

    override fun start() = coordinator.start()

    override fun stop() = coordinator.close()

    private fun requireRunning() {
        if (!isRunning) {
            throw IllegalStateException("${this.javaClass.simpleName} is not running! Its status is: ${coordinator.status}")
        }
    }

    override fun forceCpiUpload(upload: HttpFileUpload): CpiUploadRPCOps.UploadResponse {
        logger.info("Force uploading CPI: ${upload.fileName}")
        requireRunning()
        val cpiUploadRequestId = cpiUploadManager.uploadCpi(upload.fileName, upload.content, true)
        return CpiUploadRPCOps.UploadResponse(cpiUploadRequestId.requestId)
    }
}