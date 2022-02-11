package net.corda.libs.cpiupload.endpoints.v1

import net.corda.httprpc.RpcOps
import net.corda.httprpc.annotations.HttpRpcPOST
import net.corda.httprpc.annotations.HttpRpcRequestBodyParameter
import net.corda.httprpc.annotations.HttpRpcResource
import java.io.InputStream

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
    fun cpi(cpiFileName: String, cpiContent: InputStream): CpiUploadId
}

data class CpiUploadId(val id: String)