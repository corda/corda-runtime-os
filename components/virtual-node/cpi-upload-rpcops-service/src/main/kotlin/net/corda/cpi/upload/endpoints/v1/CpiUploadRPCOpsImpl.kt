package net.corda.cpi.upload.endpoints.v1

import net.corda.cpi.upload.endpoints.common.CpiUploadRPCOpsHandler
import net.corda.cpi.upload.endpoints.service.CpiUploadRPCOpsService
import net.corda.cpiinfo.read.CpiInfoReadService
import net.corda.httprpc.PluggableRPCOps
import net.corda.httprpc.exception.ResourceNotFoundException
import net.corda.libs.cpiupload.CpiUploadStatus
import net.corda.libs.cpiupload.endpoints.v1.CpiIdentifier
import net.corda.libs.cpiupload.endpoints.v1.CpiMetadata
import net.corda.libs.cpiupload.endpoints.v1.CpiUploadRPCOps
import net.corda.libs.cpiupload.endpoints.v1.CpkIdentifier
import net.corda.libs.cpiupload.endpoints.v1.CpkMetadata
import net.corda.libs.cpiupload.endpoints.v1.HTTPGetCPIsResponse
import net.corda.lifecycle.Lifecycle
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.createCoordinator
import net.corda.v5.base.util.contextLogger
import net.corda.v5.crypto.SecureHash
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import java.io.InputStream

@Component(service = [PluggableRPCOps::class])
class CpiUploadRPCOpsImpl @Activate constructor(
    @Reference(service = LifecycleCoordinatorFactory::class)
    coordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = CpiUploadRPCOpsService::class)
    private val cpiUploadRPCOpsService: CpiUploadRPCOpsService,
    @Reference(service = CpiInfoReadService::class)
    private val cpiInfoReadService: CpiInfoReadService
) : CpiUploadRPCOps, PluggableRPCOps<CpiUploadRPCOps>, Lifecycle {
    companion object {
        val log = contextLogger()
    }

    private val coordinator = coordinatorFactory.createCoordinator<CpiUploadRPCOps>(
        CpiUploadRPCOpsHandler()
    )

    private val cpiUploadManager get() = cpiUploadRPCOpsService.cpiUploadManager

    override val protocolVersion: Int = 1

    override val targetInterface: Class<CpiUploadRPCOps> = CpiUploadRPCOps::class.java

    override val isRunning get() = coordinator.isRunning

    override fun start() {
        coordinator.start()
        cpiInfoReadService.start()
    }

    override fun stop() = coordinator.close()

    /** @return first 12 characters of the hex string */
    private fun toShortHash(secureHash: SecureHash) : String {
        // see [HoldingIdentity]
        return secureHash.toHexString().substring(0, 12)
    }

    override fun cpi(cpiFileName: String, cpiContent: InputStream): CpiUploadRPCOps.UploadResponse {
        if (!isRunning) {
            throw IllegalStateException("CpiUploadRPCOpsImpl is not running! Its status is ${coordinator.status}")
        }

        val cpiUploadRequestId = cpiUploadManager.uploadCpi(cpiFileName, cpiContent)
        return CpiUploadRPCOps.UploadResponse(cpiUploadRequestId.requestId, toShortHash(cpiUploadRequestId.secureHash))
    }

    // We're mostly returning the enumeration to a string in this version
    override fun status(id: String): CpiUploadRPCOps.Status {
        val status = cpiUploadManager.status(id)
        when (status) {
            CpiUploadStatus.NO_SUCH_REQUEST_ID -> throw ResourceNotFoundException("No such request id '$id'")
            else -> return CpiUploadRPCOps.Status(status.toString())
        }
    }

    override fun getAllCpis(): HTTPGetCPIsResponse {
        val cpis = cpiInfoReadService.getAll().map{ cpiMetadata ->
            CpiMetadata(
                createCpiIdentifier(cpiMetadata.id),
                cpiMetadata.fileChecksum.toHexString(),
                cpiMetadata.cpks.map{ createCpkMetadata(it) },
                cpiMetadata.groupPolicy)
        }

        return HTTPGetCPIsResponse(cpis)
    }

    private fun createCpiIdentifier(cpiId: net.corda.libs.packaging.CpiIdentifier): CpiIdentifier {
        return CpiIdentifier(cpiId.name, cpiId.version, cpiId.signerSummaryHash.toString())
    }

    private fun createCpkIdentifier(cpkId: net.corda.libs.packaging.CpkIdentifier): CpkIdentifier {
        return CpkIdentifier(cpkId.name, cpkId.version, cpkId.signerSummaryHash.toString())
    }

    private fun createCpkMetadata(cpk: net.corda.libs.packaging.CpkMetadata): CpkMetadata {
        return CpkMetadata(
            createCpkIdentifier(cpk.id), cpk.mainBundle, cpk.libraries, cpk.dependencies.map{ createCpkIdentifier(it) },
            cpk.type.toString(), cpk.hash.toString())
    }
}
