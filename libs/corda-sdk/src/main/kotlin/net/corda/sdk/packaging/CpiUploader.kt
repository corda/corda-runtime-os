package net.corda.sdk.packaging

import net.corda.libs.cpiupload.endpoints.v1.CpiUploadRestResource
import net.corda.libs.cpiupload.endpoints.v1.GetCPIsResponse
import net.corda.libs.virtualnode.maintenance.endpoints.v1.VirtualNodeMaintenanceRestResource
import net.corda.rest.HttpFileUpload
import net.corda.rest.client.RestClient
import net.corda.sdk.rest.RestClientUtils.executeWithRetry
import java.io.File
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class CpiUploader {

    fun uploadCPI(
        restClient: RestClient<CpiUploadRestResource>,
        cpiFile: File,
        cpiName: String,
        wait: Duration = 10.seconds
    ): CpiUploadRestResource.CpiUploadResponse {
        return restClient.use { client ->
            executeWithRetry(
                waitDuration = wait,
                operationName = "Upload CPI $cpiName"
            ) {
                val resource = client.start().proxy
                resource.cpi(
                    HttpFileUpload(
                        content = cpiFile.inputStream(),
                        fileName = cpiName,
                    )
                )
            }
        }
    }

    fun cpiChecksum(
        restClient: RestClient<CpiUploadRestResource>,
        uploadRequestId: String,
        wait: Duration = 10.seconds
    ): String {
        return restClient.use { client ->
            executeWithRetry(
                waitDuration = wait,
                operationName = "Wait for CPI to be ingested and return checksum"
            ) {
                val resource = client.start().proxy
                val status = resource.status(uploadRequestId)
                if (status.status == "OK") {
                    status.cpiFileChecksum
                } else {
                    throw CpiUploadException("Cpi status is not ok: ${status.status}")
                }
            }
        }
    }

    internal class CpiUploadException(message: String) : Exception(message)

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

    fun forceCpiUpload(
        restClient: RestClient<VirtualNodeMaintenanceRestResource>,
        cpiFile: File,
        cpiName: String,
        wait: Duration = 10.seconds
    ): CpiUploadRestResource.CpiUploadResponse {
        return restClient.use { client ->
            executeWithRetry(
                waitDuration = wait,
                operationName = "Force upload CPI $cpiName"
            ) {
                val resource = client.start().proxy
                resource.forceCpiUpload(
                    HttpFileUpload(
                        content = cpiFile.inputStream(),
                        fileName = cpiName,
                    )
                )
            }
        }
    }
}
