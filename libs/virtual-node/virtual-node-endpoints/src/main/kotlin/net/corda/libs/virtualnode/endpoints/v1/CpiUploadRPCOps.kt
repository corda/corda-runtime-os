package net.corda.libs.virtualnode.endpoints.v1

import net.corda.httprpc.RpcOps
import net.corda.httprpc.annotations.HttpRpcPOST
import net.corda.httprpc.annotations.HttpRpcRequestBodyParameter
import net.corda.httprpc.annotations.HttpRpcResource
import java.io.InputStream

data class HTTPCpiUploadRequestId(val id: Int)

@HttpRpcResource(
    name = "CpiUploadRPCOps",
    description = "Cpi Upload management endpoints",
    path = "cpi"
)
interface CpiUploadRPCOps : RpcOps {

    @HttpRpcPOST(
        path = "/",
        title = "Upload a CPI",
        description = "Uploads a CPI",
        responseDescription = "The request Id calculated for a CPI upload request"
    )
    fun cpi(@HttpRpcRequestBodyParameter file: InputStream): HTTPCpiUploadRequestId
}