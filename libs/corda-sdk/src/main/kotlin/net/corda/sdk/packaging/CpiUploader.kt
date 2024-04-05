package net.corda.sdk.packaging

import net.corda.libs.cpiupload.endpoints.v1.CpiUploadRestResource
import net.corda.libs.cpiupload.endpoints.v1.GetCPIsResponse
import net.corda.libs.virtualnode.maintenance.endpoints.v1.VirtualNodeMaintenanceRestResource
import net.corda.rest.HttpFileUpload
import net.corda.rest.client.RestClient
import net.corda.sdk.data.Checksum
import net.corda.sdk.data.RequestId
import net.corda.sdk.rest.RestClientUtils.executeWithRetry
import java.io.InputStream
import java.util.*
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class CpiUploader {

    /**
     * Upload a given CPI
     * @param restClient of type RestClient<CpiUploadRestResource>
     * @param cpi InputStream of the CPI to upload
     * @param cpiName name of the CPI file
     * @param wait Duration before timing out, default 30 seconds
     */
    fun uploadCPI(
        restClient: RestClient<CpiUploadRestResource>,
        cpi: InputStream,
        cpiName: String,
        wait: Duration = 30.seconds
    ): CpiUploadRestResource.CpiUploadResponse {
        return restClient.use { client ->
            executeWithRetry(
                waitDuration = wait,
                operationName = "Upload CPI $cpiName"
            ) {
                val resource = client.start().proxy
                resource.cpi(
                    HttpFileUpload(
                        content = cpi,
                        fileName = cpiName,
                    )
                )
            }
        }
    }

    /**
     * Upload a CPI, if it already exists then force upload it again
     * @param uploadRestClient of type RestClient<CpiUploadRestResource>
     * @param forceUploadRestClient of type RestClient<VirtualNodeMaintenanceRestResource>
     * @param cpi InputStream of the CPI to upload
     * @param cpiName name of the CPI file
     * @param cpiVersion version of the CPI file
     * @param wait Duration before timing out, default 30 seconds
     */
    @Suppress("LongParameterList")
    fun uploadCpiEvenIfExists(
        uploadRestClient: RestClient<CpiUploadRestResource>,
        forceUploadRestClient: RestClient<VirtualNodeMaintenanceRestResource>,
        cpi: InputStream,
        cpiName: String,
        cpiVersion: String,
        wait: Duration = 30.seconds
    ): CpiUploadRestResource.CpiUploadResponse {
        return if (
            cpiPreviouslyUploaded(
                restClient = uploadRestClient,
                cpiName = cpiName,
                cpiVersion = cpiVersion,
                wait = wait
            )
        ) {
            forceCpiUpload(
                restClient = forceUploadRestClient,
                cpiFile = cpi,
                cpiName = cpiName,
                wait = wait
            )
        } else {
            uploadCPI(
                restClient = uploadRestClient,
                cpi = cpi,
                cpiName = cpiName,
                wait = wait
            )
        }
    }

    /**
     * Wait for the CPI to be ingested, where status is OK, and return the CPI checksum
     * @param restClient of type RestClient<CpiUploadRestResource>
     * @param uploadRequestId the ID returned from the upload request
     * @param wait Duration before timing out, default 60 seconds
     * @return checksum value
     */
    fun cpiChecksum(
        restClient: RestClient<CpiUploadRestResource>,
        uploadRequestId: RequestId,
        wait: Duration = 60.seconds
    ): Checksum {
        return restClient.use { client ->
            executeWithRetry(
                waitDuration = wait,
                operationName = "Wait for CPI to be ingested and return checksum"
            ) {
                val resource = client.start().proxy
                val status = resource.status(uploadRequestId.value)
                if (status.status == "OK") {
                    Checksum(status.cpiFileChecksum)
                } else {
                    throw CpiUploadException("Cpi status is not ok: ${status.status}")
                }
            }
        }
    }

    internal class CpiUploadException(message: String) : Exception(message)

    /**
     * List all CPIs
     * @param restClient of type RestClient<CpiUploadRestResource>
     * @param wait Duration before timing out, default 10 seconds
     * @return list of CPI metadata
     */
    fun getAllCpis(restClient: RestClient<CpiUploadRestResource>, wait: Duration = 10.seconds): GetCPIsResponse {
        return restClient.use { client ->
            executeWithRetry(
                waitDuration = wait,
                operationName = "List all CPIs"
            ) {
                client.start().proxy.getAllCpis()
            }
        }
    }

    /**
     * Force upload a given CPI
     * @param restClient of type RestClient<VirtualNodeMaintenanceRestResource>
     * @param cpi InputStream of the CPI to upload
     * @param cpiName name of the CPI file
     * @param wait Duration before timing out, default 30 seconds
     */
    fun forceCpiUpload(
        restClient: RestClient<VirtualNodeMaintenanceRestResource>,
        cpiFile: InputStream,
        cpiName: String,
        wait: Duration = 30.seconds
    ): CpiUploadRestResource.CpiUploadResponse {
        return restClient.use { client ->
            executeWithRetry(
                waitDuration = wait,
                operationName = "Force upload CPI $cpiName"
            ) {
                val resource = client.start().proxy
                resource.forceCpiUpload(
                    HttpFileUpload(
                        content = cpiFile,
                        fileName = cpiName,
                    )
                )
            }
        }
    }

    /**
     * Checks if a CPI has previously been uploaded by comparing the cpiName and cpiVersion to CPIs already uploaded.
     * @param restClient of type RestClient<CpiUploadRestResource>
     * @param cpiName name of the CPI file
     * @param cpiVersion version of the CPI file
     * @param wait Duration before timing out, default 10 seconds
     */
    fun cpiPreviouslyUploaded(
        restClient: RestClient<CpiUploadRestResource>,
        cpiName: String,
        cpiVersion: String,
        wait: Duration = 10.seconds
    ): Boolean {
        val existingCpis = getAllCpis(restClient, wait)
        existingCpis.cpis.forEach { cpi ->
            if (Objects.equals(cpi.id.cpiName, cpiName) && Objects.equals(cpi.id.cpiVersion, cpiVersion)) {
                return true
            }
        }
        return false
    }
}
