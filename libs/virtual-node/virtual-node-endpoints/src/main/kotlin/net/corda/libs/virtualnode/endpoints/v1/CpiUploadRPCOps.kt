package net.corda.libs.virtualnode.endpoints.v1

import net.corda.httprpc.RpcOps
import net.corda.httprpc.annotations.HttpRpcPOST
import net.corda.httprpc.annotations.HttpRpcRequestBodyParameter
import net.corda.httprpc.annotations.HttpRpcResource

data class HTTPCpiUploadRequestId(val id: Int)

@HttpRpcResource(
    name = "CpiUploadRPCOps",
    description = "Cpi Upload management endpoints",
    path = "cpiupload"
)
interface CpiUploadRPCOps : RpcOps {

    @HttpRpcPOST
    fun cpi(@HttpRpcRequestBodyParameter file: ByteArray): HTTPCpiUploadRequestId
}