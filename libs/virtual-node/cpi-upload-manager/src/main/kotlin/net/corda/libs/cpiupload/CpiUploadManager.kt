package net.corda.libs.cpiupload

import java.io.InputStream

/**
 * CPI Uploading management.
 */
interface CpiUploadManager {
    /**
     * Uploads the CPI to Kafka. It returns the [RequestId] (UUID) registered for this CPI upload.
     *
     * @throws CpiUploadManagerException
     */
    fun uploadCpi(cpiFileName: String, cpiContent: InputStream): RequestId
}

typealias RequestId = String