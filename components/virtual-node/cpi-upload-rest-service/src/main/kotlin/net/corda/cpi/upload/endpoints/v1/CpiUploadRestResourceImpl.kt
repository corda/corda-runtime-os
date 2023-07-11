package net.corda.cpi.upload.endpoints.v1

import net.corda.chunking.Constants.Companion.CHUNK_FILENAME_KEY
import net.corda.crypto.core.toCorda
import net.corda.cpi.upload.endpoints.common.CpiUploadRestResourceHandler
import net.corda.cpi.upload.endpoints.service.CpiUploadService
import net.corda.cpiinfo.read.CpiInfoReadService
import net.corda.data.chunking.UploadStatus
import net.corda.libs.configuration.validation.ConfigurationValidationException
import net.corda.rest.HttpFileUpload
import net.corda.rest.PluggableRestResource
import net.corda.rest.exception.BadRequestException
import net.corda.rest.exception.InternalServerException
import net.corda.rest.exception.InvalidInputDataException
import net.corda.rest.exception.ResourceAlreadyExistsException
import net.corda.rest.messagebus.MessageBusUtils.tryWithExceptionHandling
import net.corda.libs.cpiupload.DuplicateCpiUploadException
import net.corda.libs.cpiupload.ValidationException
import net.corda.libs.cpiupload.endpoints.v1.CpiUploadRestResource
import net.corda.libs.cpiupload.endpoints.v1.GetCPIsResponse
import net.corda.libs.platform.PlatformInfoProvider
import net.corda.lifecycle.Lifecycle
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.createCoordinator
import net.corda.utilities.trace
import net.corda.v5.crypto.SecureHash
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.LoggerFactory

@Component(service = [PluggableRestResource::class])
class CpiUploadRestResourceImpl @Activate constructor(
    @Reference(service = LifecycleCoordinatorFactory::class)
    coordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = CpiUploadService::class)
    private val cpiUploadService: CpiUploadService,
    @Reference(service = CpiInfoReadService::class)
    private val cpiInfoReadService: CpiInfoReadService,
    @Reference(service = PlatformInfoProvider::class)
    private val platformInfoProvider: PlatformInfoProvider
) : CpiUploadRestResource, PluggableRestResource<CpiUploadRestResource>, Lifecycle {
    companion object {
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    private val coordinator = coordinatorFactory.createCoordinator<CpiUploadRestResource>(
        CpiUploadRestResourceHandler()
    )

    private val cpiUploadManager get() = cpiUploadService.cpiUploadManager

    override val protocolVersion get() = platformInfoProvider.localWorkerPlatformVersion

    override val targetInterface: Class<CpiUploadRestResource> = CpiUploadRestResource::class.java

    override val isRunning get() = coordinator.isRunning

    override fun start() = coordinator.start()

    override fun stop() = coordinator.stop()

    override fun cpi(upload: HttpFileUpload): CpiUploadRestResource.CpiUploadResponse {
        val opName = "Uploading CPI: ${upload.fileName}"
        val properties = mapOf<String, String?>(CHUNK_FILENAME_KEY to upload.fileName)
        logger.info(opName)
        requireRunning()
        val cpiUploadRequestId = tryWithExceptionHandling(logger, opName) {
            cpiUploadManager.uploadCpi(upload.content, properties)
        }
        logger.info("Request ID for uploading CPI ${upload.fileName} is $cpiUploadRequestId")
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
            ConfigurationValidationException::class.java.name -> throw BadRequestException(ex.errorMessage, details)
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
