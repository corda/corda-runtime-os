package net.corda.cpi.upload.endpoints.v1

import net.corda.cpi.upload.endpoints.common.CpiUploadRPCOpsHandler
import net.corda.cpi.upload.endpoints.service.CpiUploadRPCOpsService
import net.corda.httprpc.PluggableRPCOps
import net.corda.libs.cpiupload.endpoints.v1.CpiUploadRPCOps
import net.corda.libs.cpiupload.endpoints.v1.HTTPCpiUploadRequestId
import net.corda.lifecycle.Lifecycle
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleStatus
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

    // Need to check again coordinator.isRunning vs coordinator.status
    override val isRunning get() = coordinator.status == LifecycleStatus.UP

    override fun start() {
        coordinator.start()
    }

    override fun stop() {
        coordinator.close()
    }

    override fun cpi(file: InputStream): HTTPCpiUploadRequestId {
        if (!isRunning) {
            throw IllegalStateException("CpiUploadRPCOpsImpl is not running! Its status is ${coordinator.status}")
        }

        //cpiUploadManager.sendCpiChunk()
        // TODO - kyriakos - fix the endpoint to actually receive the file - needs corda rpc framework extended
        // TODO - kyriakos - split it in chunks and put it on kafka
        // TODO - kyriakos - should i then be waiting for some response from kafka here?
        // TODO - kyriakos - return HTTP response to user
        return HTTPCpiUploadRequestId(5)
    }
}