package net.corda.sdk.packaging

import net.corda.cli.plugins.data.Checksum
import net.corda.cli.plugins.data.RequestId
import net.corda.libs.cpiupload.endpoints.v1.CpiUploadRestResource
import net.corda.libs.cpiupload.endpoints.v1.GetCPIsResponse
import net.corda.libs.virtualnode.maintenance.endpoints.v1.VirtualNodeMaintenanceRestResource
import net.corda.rest.HttpFileUpload
import net.corda.rest.client.RestClient
import net.corda.sdk.rest.RestClientUtils.executeWithRetry
import java.io.InputStream
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class CpiUploader {

    /**
     * Upload a given CPI
     * @param restClient of type RestClient<CpiUploadRestResource>
     * @param cpi value of the CPI to upload
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
     * @param restClient of type RestClient<CpiUploadRestResource>
     * @param cpi value of the CPI to upload
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
}
