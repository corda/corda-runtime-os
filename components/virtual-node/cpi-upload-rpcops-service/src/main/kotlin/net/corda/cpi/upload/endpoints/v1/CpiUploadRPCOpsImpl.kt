package net.corda.cpi.upload.endpoints.v1

import net.corda.cpi.upload.endpoints.common.CpiUploadManager
import net.corda.cpi.upload.endpoints.common.CpiUploadRPCOpsHandler
import net.corda.httprpc.PluggableRPCOps
import net.corda.libs.virtualnode.endpoints.v1.CpiUploadRPCOps
import net.corda.libs.virtualnode.endpoints.v1.HTTPCpiUploadRequestId
import net.corda.lifecycle.Lifecycle
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.createCoordinator
import net.corda.v5.base.util.contextLogger
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import java.io.InputStream

@Component(service = [PluggableRPCOps::class])
class CpiUploadRPCOpsImpl @Activate constructor(
    @Reference(service = LifecycleCoordinatorFactory::class)
    coordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = CpiUploadManager::class)
    private val cpiUploadManager: CpiUploadManager
) : CpiUploadRPCOps, PluggableRPCOps<CpiUploadRPCOps>, Lifecycle {

    private val coordinator = coordinatorFactory.createCoordinator<CpiUploadRPCOps>(
        CpiUploadRPCOpsHandler()
    )

    companion object {
        val log = contextLogger()
    }

    override val protocolVersion: Int = 1

    override val targetInterface: Class<CpiUploadRPCOps> = CpiUploadRPCOps::class.java

    override val isRunning get() = coordinator.isRunning

    override fun start() = Unit

    override fun stop() {
        cpiUploadManager.close()
    }

    override fun cpi(file: InputStream): HTTPCpiUploadRequestId {
        require(isRunning) {
            "CpiUploadRPCOpsImpl is not running yet!"
        }

        // TODO - kyriakos - fix the endpoint to actually receive the file - needs corda rpc framework extended
        // TODO - kyriakos - validation of CPI -> check it is well formed - maybe in a subsequent PR
        // TODO - kyriakos - split it in chunks and put it on kafka
        // TODO - kyriakos - should i then be waiting for some response from kafka here?
        // TODO - kyriakos - return HTTP response to user
        return HTTPCpiUploadRequestId(5)
    }
}