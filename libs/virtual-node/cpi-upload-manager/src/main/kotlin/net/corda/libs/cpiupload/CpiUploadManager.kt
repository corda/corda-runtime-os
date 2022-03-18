package net.corda.libs.cpiupload

import net.corda.chunking.ChunkWriter
import net.corda.chunking.RequestId
import net.corda.data.ExceptionEnvelope
import java.io.InputStream

/**
 * CPI Uploading management.
 */
interface CpiUploadManager : AutoCloseable {
    enum class UploadStatus { OK, IN_PROGRESS, FAILED }
    /**
     * Uploads the CPI to Kafka. It returns the [RequestId] (UUID) registered for this CPI upload.
     *
     * @throws CpiUploadManagerException
     */
    fun uploadCpi(cpiFileName: String, cpiContent: InputStream): ChunkWriter.Request


    /**
     * Return the status of a given upload request
     */
    fun status(requestId: RequestId): Pair<UploadStatus, ExceptionEnvelope?>
}
