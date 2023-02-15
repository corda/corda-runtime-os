package net.corda.cpi.upload.endpoints.v1

import net.corda.chunking.toCorda
import net.corda.cpi.upload.endpoints.common.CpiUploadRPCOpsHandler
import net.corda.cpi.upload.endpoints.service.CpiUploadRPCOpsService
import net.corda.cpiinfo.read.CpiInfoReadService
import net.corda.data.chunking.UploadStatus
import net.corda.httprpc.HttpFileUpload
import net.corda.httprpc.PluggableRestResource
import net.corda.httprpc.exception.BadRequestException
import net.corda.httprpc.exception.InternalServerException
import net.corda.httprpc.exception.InvalidInputDataException
import net.corda.httprpc.exception.ResourceAlreadyExistsException
import net.corda.libs.cpiupload.DuplicateCpiUploadException
import net.corda.libs.cpiupload.ValidationException
import net.corda.libs.cpiupload.endpoints.v1.CpiUploadRestResource
import net.corda.libs.cpiupload.endpoints.v1.GetCPIsResponse
import net.corda.lifecycle.Lifecycle
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.createCoordinator
import net.corda.v5.base.util.trace
import net.corda.v5.crypto.SecureHash
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.LoggerFactory

@Component(service = [PluggableRestResource::class])
class CpiUploadRestResourceImpl @Activate constructor(
    @Reference(service = LifecycleCoordinatorFactory::class)
    coordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = CpiUploadRPCOpsService::class)
    private val cpiUploadRPCOpsService: CpiUploadRPCOpsService,
    @Reference(service = CpiInfoReadService::class)
    private val cpiInfoReadService: CpiInfoReadService
) : CpiUploadRestResource, PluggableRestResource<CpiUploadRestResource>, Lifecycle {
    companion object {
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    private val coordinator = coordinatorFactory.createCoordinator<CpiUploadRestResource>(
        CpiUploadRPCOpsHandler()
    )

    private val cpiUploadManager get() = cpiUploadRPCOpsService.cpiUploadManager

    override val protocolVersion: Int = 1

    override val targetInterface: Class<CpiUploadRestResource> = CpiUploadRestResource::class.java

    override val isRunning get() = coordinator.isRunning

    override fun start() = coordinator.start()

    override fun stop() = coordinator.stop()

    override fun cpi(upload: HttpFileUpload): CpiUploadRestResource.CpiUploadResponse {
        logger.info("Uploading CPI: ${upload.fileName}")
        requireRunning()
        val cpiUploadRequestId = cpiUploadManager.uploadCpi(upload.fileName, upload.content)
        logger.info("Request ID for uploading CPI ${upload.fileName} is ${cpiUploadRequestId}")
        return CpiUploadRestResource.CpiUploadResponse(cpiUploadRequestId.requestId)
    }

    // We're mostly returning the enumeration to a string in this version
    override fun status(id: String): CpiUploadRestResource.CpiUploadStatus {
        logger.info("Upload status request for CPI id: $id")
        requireRunning()
        val uploadStatus = cpiUploadManager.status(id) ?: throw InvalidInputDataException("No such requestId=$id")

        // HTTP response status values are passed back via exceptions.
        if (uploadStatus.exception != null) {
            translateExceptionAndRethrow(uploadStatus)
        }

        val checksum = if (uploadStatus.checksum != null) toShortHash(uploadStatus.checksum.toCorda()) else ""
        return CpiUploadRestResource.CpiUploadStatus(uploadStatus.message, checksum)
    }

    @Suppress("ThrowsCount")
    private fun translateExceptionAndRethrow(uploadStatus: UploadStatus) {
        val ex = uploadStatus.exception!!

        // These keys *are* returned to the client, and are visible in JSON for some exceptions
        val details = mapOf(
            "errorType" to ex.errorType,
            "errorMessage" to ex.errorMessage,
            "message" to uploadStatus.message
        )

        // DuplicateCpiUploadException only contains the "resource" in the error message,
        // i.e. "name version (groupId)"
        when (ex.errorType) {
            ValidationException::class.java.name -> throw BadRequestException(ex.errorMessage, details)
            DuplicateCpiUploadException::class.java.name -> throw ResourceAlreadyExistsException(ex.errorMessage)
            else -> throw InternalServerException(ex.toString(), details)
        }
    }

    override fun getAllCpis(): GetCPIsResponse {
        logger.trace { "Get all CPIs request" }
        requireRunning()
        val cpis = cpiInfoReadService.getAll().map { it.toEndpointType() }
        return GetCPIsResponse(cpis)
    }

    /**
     * @return first 12 characters of the hex string
     * TODO Needs to be ported to corda-api repo as per https://r3-cev.atlassian.net/browse/CORE-4298
     * */
    private fun toShortHash(secureHash: SecureHash): String {
        // see [HoldingIdentity]
        return secureHash.toHexString().substring(0, 12)
    }

    private fun requireRunning() {
        if (!isRunning) {
            throw IllegalStateException(
                "${CpiUploadRestResource::class.java.simpleName} is not running! Its status is: ${coordinator.status}")
        }
    }
}
