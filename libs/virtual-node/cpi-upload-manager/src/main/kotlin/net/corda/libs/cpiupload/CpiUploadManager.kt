package net.corda.libs.cpiupload

import net.corda.v5.crypto.SecureHash
import net.corda.lifecycle.Lifecycle
import net.corda.v5.base.exceptions.CordaRuntimeException
import java.io.InputStream

/**
 * CPI Uploading management.
 */
interface CpiUploadManager : Lifecycle {
    /**
     * Uploads the CPI ([inputStream]) to Kafka. During CPI uploading it calculates the [inputStream]'s checksum.
     * It returns the request Id registered under for this CPI upload and the CPI's checksum ([CPIUploadResult]).
     *
     * @throws CpiUploadManagerException
     */
    fun uploadCpi(inputStream: InputStream): CPIUploadResult
}

data class CPIUploadResult(
    val requestId: String,
    val checksum: SecureHash
)