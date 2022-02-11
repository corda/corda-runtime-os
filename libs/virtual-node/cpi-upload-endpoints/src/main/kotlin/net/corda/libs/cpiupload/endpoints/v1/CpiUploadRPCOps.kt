package net.corda.libs.cpiupload.endpoints.v1

import net.corda.httprpc.RpcOps
import net.corda.httprpc.annotations.HttpRpcPOST
import net.corda.httprpc.annotations.HttpRpcResource
import java.io.InputStream

@HttpRpcResource(
    name = "CpiUploadRPCOps",
    description = "Cpi Upload management endpoints",
    path = "cpi"
)
interface CpiUploadRPCOps : RpcOps {

    /**
     * HTTP POST resource to upload a CPI to Kafka.
     *
     * Please note that this method will not close [cpiContent], its owner is responsible to close it.
     */
    @HttpRpcPOST(
        path = "/",
        title = "Upload a CPI",
        description = "Uploads a CPI",
        responseDescription = "The request Id calculated for a CPI upload request"
    )
    fun cpi(cpiFileName: String, cpiContent: InputStream): CpiUploadId
}

data class CpiUploadId(val id: String)