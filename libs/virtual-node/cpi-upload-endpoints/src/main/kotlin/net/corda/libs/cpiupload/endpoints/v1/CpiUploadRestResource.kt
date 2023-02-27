package net.corda.libs.cpiupload.endpoints.v1

import net.corda.httprpc.RestResource
import net.corda.httprpc.HttpFileUpload
import net.corda.httprpc.annotations.HttpGET
import net.corda.httprpc.annotations.RestPathParameter
import net.corda.httprpc.annotations.HttpPOST
import net.corda.httprpc.annotations.ClientRequestBodyParameter
import net.corda.httprpc.annotations.HttpRestResource

@HttpRestResource(
    name = "CPI API",
    description = "The CPI API consists of a number of endpoints used to manage Corda Package Installer (CPI) " +
            "files in the Corda cluster.",
    path = "cpi"
)
interface CpiUploadRestResource : RestResource {
    /**
     * Response from CPI Upload Request
     *
     * @param id ID of the CPI Upload Request.
     */
    data class CpiUploadResponse(val id: String)

    /**
     * HTTP POST resource to upload a CPI to Kafka.
     *
     * Please note that this method will not close [HttpFileUpload.content] input stream, the caller must close it.
     */
    @HttpPOST(
        title = "CPI upload",
        description = "This method uses the POST method to upload a Corda Package Installer (CPI) file to the " +
                "Corda cluster.",
        responseDescription = "The ID for the CPI upload request"
    )
    fun cpi(
        @ClientRequestBodyParameter(
            description = "The CPI file to be uploaded.",
            required = true
        )
        upload: HttpFileUpload): CpiUploadResponse

    /**
     * Status of the CPI Upload Request
     *
     * @param status Status of the Upload request.
     * @param cpiFileChecksum Checksum for the file to be uploaded.
     */
    data class CpiUploadStatus(val status: String, val cpiFileChecksum: String)

    /**
     * Get the status of the upload.
     *
     * @param id Request ID returned from the [cpi] method.
     * @return A status object that is converted to .json on the client side `{status: OK}`.
     */
    @HttpGET(
        path = "status/{id}",
        title = "CPI upload status",
        description = "The status endpoint uses the GET method to return status information for the CPI upload with the " +
                "given request ID.")
    fun status(
        @RestPathParameter(
            description = "The ID returned from the CPI upload request."
        )
        id: String,
    ): CpiUploadStatus

    /**
     * Lists all CPIs uploaded to the cluster.
     *
     * @throws `HttpApiException` If the request returns an exceptional response.
     */
    @HttpGET(
        title = "CPI info",
        description = "The GET method returns a list of all CPIs uploaded to the cluster.",
        responseDescription = "Details of all of the CPIs uploaded to the cluster."
    )
    fun getAllCpis(): GetCPIsResponse
}
