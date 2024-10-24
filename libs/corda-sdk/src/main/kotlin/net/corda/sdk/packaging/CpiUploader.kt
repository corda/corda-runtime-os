package net.corda.sdk.packaging

import net.corda.rest.ResponseCode
import net.corda.rest.exception.ExceptionDetails
import net.corda.rest.exception.ResourceAlreadyExistsException
import net.corda.restclient.CordaRestClient
import net.corda.restclient.generated.infrastructure.ClientException
import net.corda.restclient.generated.models.CpiUploadResponse
import net.corda.restclient.generated.models.GetCPIsResponse
import net.corda.sdk.data.Checksum
import net.corda.sdk.data.RequestId
import net.corda.sdk.rest.RestClientUtils.executeWithRetry
import java.io.File
import java.util.Objects
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class CpiUploader(val restClient: CordaRestClient) {

    /**
     * Upload a given CPI
     * @param cpi File of the CPI to upload
     */
    fun uploadCPI(
        cpi: File,
    ): CpiUploadResponse {
        return restClient.cpiClient.postCpi(cpi)
    }

    /**
     * Upload a CPI, if it already exists then force upload it again
     * @param cpi File of the CPI to upload
     * @param cpiName name of the CPI file
     * @param cpiVersion version of the CPI file
     * @param wait Duration before timing out, default 30 seconds
     */
    @Suppress("LongParameterList")
    fun uploadCpiEvenIfExists(
        cpi: File,
        cpiName: String,
        cpiVersion: String,
        wait: Duration = 30.seconds
    ): CpiUploadResponse {
        return if (
            cpiPreviouslyUploaded(
                cpiName = cpiName,
                cpiVersion = cpiVersion,
                wait = wait
            )
        ) {
            forceCpiUpload(
                cpiFile = cpi
            )
        } else {
            uploadCPI(
                cpi = cpi,
            )
        }
    }

    /**
     * Wait for the CPI to be ingested, where status is OK, and return the CPI checksum
     * @param uploadRequestId the ID returned from the upload request
     * @param wait Duration before timing out, default 60 seconds
     * @return checksum value
     */
    @Suppress("ThrowsCount")
    fun cpiChecksum(
        uploadRequestId: RequestId,
        wait: Duration = 60.seconds,
    ): Checksum {
        return executeWithRetry(
            waitDuration = wait,
            operationName = "Wait for CPI to be ingested and return checksum",
        ) {
            val status = try {
                restClient.cpiClient.getCpiStatusId(uploadRequestId.value)
            } catch (e: ClientException) {
                if (e.statusCode == ResponseCode.CONFLICT.statusCode) {
                    throw ResourceAlreadyExistsException(
                        e::class.java.simpleName,
                        ExceptionDetails(e::class.java.name, "${e.message}")
                    )
                }
                throw e
            }

            if (status.status != "OK") {
                throw CpiUploadException("Cpi status is not ok: ${status.status}")
            }

            Checksum(status.cpiFileChecksum)
        }
    }

    internal class CpiUploadException(message: String) : Exception(message)

    /**
     * List all CPIs
     * @param wait Duration before timing out, default 10 seconds
     * @return list of CPI metadata
     */
    fun getAllCpis(wait: Duration = 10.seconds): GetCPIsResponse {
        return executeWithRetry(
            waitDuration = wait,
            operationName = "List all CPIs"
        ) {
            restClient.cpiClient.getCpi()
        }
    }

    /**
     * Force upload a given CPI
     * @param cpi File of the CPI to upload
     */
    fun forceCpiUpload(
        cpiFile: File
    ): CpiUploadResponse {
        return restClient.virtualNodeMaintenanceClient.postMaintenanceVirtualnodeForcecpiupload(cpiFile)
    }

    /**
     * Checks if a CPI has previously been uploaded by comparing the cpiName and cpiVersion to CPIs already uploaded.
     * @param cpiName name of the CPI file
     * @param cpiVersion version of the CPI file
     * @param wait Duration before timing out, default 10 seconds
     */
    fun cpiPreviouslyUploaded(
        cpiName: String,
        cpiVersion: String,
        wait: Duration = 10.seconds
    ): Boolean {
        val existingCpis = getAllCpis(wait)
        existingCpis.cpis.forEach { cpi ->
            if (Objects.equals(cpi.id.cpiName, cpiName) && Objects.equals(cpi.id.cpiVersion, cpiVersion)) {
                return true
            }
        }
        return false
    }

    fun cpiChecksumExists(
        checksum: Checksum,
        wait: Duration = 10.seconds
    ): Boolean {
        val existingCpis = getAllCpis(wait)
        existingCpis.cpis.forEach { cpi ->
            if (Objects.equals(cpi.cpiFileChecksum, checksum.value)) {
                return true
            }
        }
        return false
    }
}
