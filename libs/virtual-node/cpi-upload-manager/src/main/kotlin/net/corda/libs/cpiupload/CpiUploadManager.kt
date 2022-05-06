package net.corda.libs.cpiupload

import net.corda.chunking.ChunkWriter
import net.corda.chunking.RequestId
import net.corda.data.chunking.UploadStatus
import java.io.InputStream

/**
 * CPI Uploading management.
 */
interface CpiUploadManager : AutoCloseable {
    /**
     * Uploads the CPI to Kafka. It returns the [RequestId] (UUID) registered for this CPI upload.
     */
    fun uploadCpi(cpiFileName: String, cpiContent: InputStream, forceUpload: Boolean): ChunkWriter.Request

    /**
     * Return the status of a given upload request
     */
    fun status(requestId: RequestId): UploadStatus?
}
