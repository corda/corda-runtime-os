package net.corda.sdk.packaging

import net.corda.libs.cpiupload.endpoints.v1.CpiUploadRestResource
import net.corda.libs.cpiupload.endpoints.v1.GetCPIsResponse
import net.corda.libs.virtualnode.maintenance.endpoints.v1.VirtualNodeMaintenanceRestResource
import net.corda.rest.HttpFileUpload
import net.corda.rest.client.RestClient
import net.corda.rest.client.exceptions.RequestErrorException
import net.corda.sdk.rest.InvariantUtils
import java.io.File

class CpiUploader {

    fun uploadCPI(restClient: RestClient<CpiUploadRestResource>, cpiFile: File, cpiName: String): CpiUploadRestResource.CpiUploadResponse {
        return restClient.use { client ->
            InvariantUtils.checkInvariant(
                errorMessage = "Failed to upload CPI $cpiName after ${InvariantUtils.MAX_ATTEMPTS} attempts."
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

    fun cpiChecksum(restClient: RestClient<CpiUploadRestResource>, uploadRequestId: String): String {
        return restClient.use { client ->
            InvariantUtils.checkInvariant(
                errorMessage = "CPI request $uploadRequestId is not ready after ${InvariantUtils.MAX_ATTEMPTS} attempts."
            ) {
                try {
                    val resource = client.start().proxy
                    val status = resource.status(uploadRequestId)
                    if (status.status == "OK") {
                        status.cpiFileChecksum
                    } else {
                        null
                    }
                } catch (e: RequestErrorException) {
                    // This exception can be thrown while the CPI upload is being processed, so we catch it and re-try.
                    null
                }
            }
        }
    }

    fun getAllCpis(restClient: RestClient<CpiUploadRestResource>): GetCPIsResponse {
        return restClient.use { client ->
            InvariantUtils.checkInvariant(
                errorMessage = "Failed to list all CPIs after ${InvariantUtils.MAX_ATTEMPTS} attempts."
            ) {
                val resource = client.start().proxy
                resource.getAllCpis()
            }
        }
    }

    fun forceCpiUpload(
        restClient: RestClient<VirtualNodeMaintenanceRestResource>,
        cpiFile: File,
        cpiName: String
    ): CpiUploadRestResource.CpiUploadResponse {
        return restClient.use { client ->
            InvariantUtils.checkInvariant(
                errorMessage = "Failed to force upload CPI $cpiName after ${InvariantUtils.MAX_ATTEMPTS} attempts."
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
