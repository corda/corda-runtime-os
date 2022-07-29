package net.corda.libs.cpiupload.endpoints.v1

import net.corda.httprpc.RpcOps
import net.corda.httprpc.annotations.HttpRpcGET
import net.corda.httprpc.annotations.HttpRpcPOST
import net.corda.httprpc.annotations.HttpRpcPathParameter
import net.corda.httprpc.annotations.HttpRpcResource
import net.corda.httprpc.HttpFileUpload

@HttpRpcResource(
    name = "CPI Upload API",
    description = "CPI Upload management endpoints.",
    path = "cpi"
)
interface CpiUploadRPCOps : RpcOps {
    /** Simple class to return some information back to the caller regarding the upload request */
    data class CpiUploadResponse(val id: String)

    /**
     * HTTP POST resource to upload a CPI to Kafka.
     *
     * Please note that this method will not close [HttpFileUpload.content] input stream, the caller must close it.
     */
    @HttpRpcPOST(
        title = "CPI API",
        description = "CPI management endpoints.",
        responseDescription = "The request Id calculated for a CPI upload request"
    )
    fun cpi(upload: HttpFileUpload): CpiUploadResponse

    /** Simple class to return the status of the upload request */
    data class CpiUploadStatus(val status: String, val cpiFileChecksum: String)

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
    ): CpiUploadStatus

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
    fun getAllCpis(): GetCPIsResponse
}
