package net.corda.cpi.upload.endpoints.v1

import net.corda.cpi.upload.endpoints.common.CpiUploadRPCOpsHandler
import net.corda.cpi.upload.endpoints.service.CpiUploadRPCOpsService
import net.corda.httprpc.PluggableRPCOps
import net.corda.libs.cpiupload.endpoints.v1.CpiUploadRPCOps
import net.corda.libs.cpiupload.endpoints.v1.CpiUploadId
import net.corda.lifecycle.Lifecycle
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.createCoordinator
import net.corda.v5.base.util.contextLogger
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import java.io.InputStream
import java.lang.IllegalStateException

@Component(service = [PluggableRPCOps::class])
class CpiUploadRPCOpsImpl @Activate constructor(
    @Reference(service = LifecycleCoordinatorFactory::class)
    coordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = CpiUploadRPCOpsService::class)
    private val cpiUploadRPCOpsService: CpiUploadRPCOpsService
) : CpiUploadRPCOps, PluggableRPCOps<CpiUploadRPCOps>, Lifecycle {

    private val coordinator = coordinatorFactory.createCoordinator<CpiUploadRPCOps>(
        CpiUploadRPCOpsHandler()
    )

    private val cpiUploadManager get() = cpiUploadRPCOpsService.cpiUploadManager

    companion object {
        val log = contextLogger()
    }

    override val protocolVersion: Int = 1

    override val targetInterface: Class<CpiUploadRPCOps> = CpiUploadRPCOps::class.java

    override val isRunning get() = coordinator.isRunning

    override fun start() = coordinator.start()

    override fun stop() = coordinator.close()

    override fun cpi(cpiFileName: String, cpiContent: InputStream): CpiUploadId {
        if (!isRunning) {
            throw IllegalStateException("CpiUploadRPCOpsImpl is not running! Its status is ${coordinator.status}")
        }

        // TODO in a later PR check the requestId topic ("HTTP Status" topic) if the CPI already has been processed so return fast
        // First validate CPI against http sent checksum. Then we should continue with uploading it.
        // TODO - validate CPI against sent checksum
        val cpiUploadRequestId = cpiUploadManager.uploadCpi(cpiFileName, cpiContent)
        return CpiUploadId(cpiUploadRequestId)
    }
}
