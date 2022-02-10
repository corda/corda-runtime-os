package net.corda.libs.cpiupload

import net.corda.lifecycle.Lifecycle
import java.io.InputStream

/**
 * CPI Uploading management.
 */
interface CpiUploadManager : Lifecycle {
    /**
     * Uploads the CPI to Kafka. It returns the [RequestId] (UUID) registered for this CPI upload.
     *
     * @throws CpiUploadManagerException
     */
    fun uploadCpi(cpi: InputStream): RequestId
}

typealias RequestId = String