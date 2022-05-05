package net.corda.libs.cpiupload.endpoints.v1

import net.corda.httprpc.RpcOps
import net.corda.httprpc.annotations.HttpRpcGET
import net.corda.httprpc.annotations.HttpRpcPOST
import net.corda.httprpc.annotations.HttpRpcPathParameter
import net.corda.httprpc.annotations.HttpRpcResource
import net.corda.httprpc.HttpFileUpload

@HttpRpcResource(
    name = "CpiUploadRPCOps",
    description = "Cpi Upload management endpoints",
    path = "cpi"
)
interface CpiUploadRPCOps : RpcOps {
    /** Simple class to return some information back to the caller regarding the upload request */
    data class UploadResponse(val id: String)

    /**
     * HTTP POST resource to upload a CPI to Kafka.
     *
     * Please note that this method will not close [cpiContent] input stream, the caller must close it.
     */
    @HttpRpcPOST(
        title = "Upload a CPI",
        description = "Uploads a CPI",
        responseDescription = "The request Id calculated for a CPI upload request"
    )
    fun cpi(upload: HttpFileUpload): UploadResponse

    /** Simple class to return the status of the upload request */
    data class Status(val status: String, val checksum: String)

    /**
     * Get the status of the upload.
     *
     * @param id request id returned from the [cpi] method
     * @return a status object that is converted to json on the client side `{status: OK}`
     */
    @HttpRpcGET(path = "status/{id}", title = "CPI upload status", description = "Check upload status for given requestId")
    fun status(
        @HttpRpcPathParameter(description = "The requestId")
        id: String,
    ): Status

    /**
     * Lists all CPIs uploaded to the cluster.
     *
     * @throws `HttpApiException` If the request returns an exceptional response.
     */
    @HttpRpcGET(
        title = "List all CPIs uploaded to the cluster",
        description = "List all CPIs uploaded to the cluster.",
        responseDescription = "List details of the all CPIs uploaded to the cluster."
    )
    fun getAllCpis(): HTTPGetCPIsResponse
}
