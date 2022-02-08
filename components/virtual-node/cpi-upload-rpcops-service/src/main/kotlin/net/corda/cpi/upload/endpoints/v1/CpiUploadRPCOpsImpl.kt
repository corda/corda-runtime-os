package net.corda.cpi.upload.endpoints.v1

import net.corda.chunking.ChunkWriterFactory
import net.corda.chunking.toCorda
import net.corda.cpi.upload.endpoints.common.CpiUploadRPCOpsHandler
import net.corda.cpi.upload.endpoints.service.CpiUploadRPCOpsService
import net.corda.data.chunking.Chunk
import net.corda.httprpc.PluggableRPCOps
import net.corda.httprpc.exception.InternalServerException
import net.corda.httprpc.exception.InvalidInputDataException
import net.corda.libs.cpiupload.endpoints.v1.CpiUploadRPCOps
import net.corda.libs.cpiupload.endpoints.v1.HTTPCpiUploadRequestId
import net.corda.lifecycle.Lifecycle
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.createCoordinator
import net.corda.v5.base.util.contextLogger
import net.corda.v5.crypto.SecureHash
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import java.io.InputStream
import java.lang.IllegalStateException
import java.nio.file.Path
import java.nio.file.Paths
import java.util.UUID

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

    // TODO - kyriakos - this method needs to also take the checksum of the file.
    override fun cpi(file: InputStream): HTTPCpiUploadRequestId {
        // TODO to be added to method's parameters
        val todoSentChecksum: SecureHash

        if (!isRunning) {
            throw IllegalStateException("CpiUploadRPCOpsImpl is not running! Its status is ${coordinator.status}")
        }

        // TODO - kyriakos we need new topic to post the requestId -> which will be posted by the db worker when the processing is ready on the db worker
        // TODO - kyriakos - in later PR check the requestId topic if the CPI already has been processed so return fast
        val cpiUploadResult = cpiUploadManager.uploadCpi(file)
        val requestId = cpiUploadResult.requestId
        val calculatedChecksum = cpiUploadResult.checksum

        todoSentChecksum = calculatedChecksum // copying for now - to be replaced with sent checksum
        log.debug("Successfully sent CPI: $todoSentChecksum to db worker")
        validateCpiChecksum(calculatedChecksum,  todoSentChecksum)
        log.info("Successfully sent to db worker and validated CPI: $todoSentChecksum ")
        return HTTPCpiUploadRequestId(requestId)
    }

    private fun validateCpiChecksum(calculatedChecksum: SecureHash, sentChecksum: SecureHash) {
        if (calculatedChecksum != sentChecksum) {
            val msg = "Calculated checksum: $calculatedChecksum was different to sent checksum: $sentChecksum"
            log.warn(msg)
            throw InvalidInputDataException(msg)
        }
    }
}