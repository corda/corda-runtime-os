package net.corda.membership.httprpc.v1

import net.corda.httprpc.RpcOps
import net.corda.httprpc.annotations.HttpRpcGET
import net.corda.httprpc.annotations.HttpRpcPOST
import net.corda.httprpc.annotations.HttpRpcQueryParameter
import net.corda.httprpc.annotations.HttpRpcRequestBodyParameter
import net.corda.httprpc.annotations.HttpRpcResource

@HttpRpcResource(
    name = "P2P tests API",
    description = "Test only. Do not use",
    path = "p2p"
)
interface P2pTestRpcOps : RpcOps {
    @HttpRpcPOST(
        path = "send",
        description = "Send a P2P message.",
        responseDescription = "The message ID"
    )
    fun send(
        @HttpRpcRequestBodyParameter(description = "Group")
        group: String,
        @HttpRpcRequestBodyParameter(description = "Source")
        source: String,
        @HttpRpcRequestBodyParameter(description = "Target")
        target: String,
        @HttpRpcRequestBodyParameter(description = "Content")
        content: String,
    ): String

    @HttpRpcGET(
        path = "read",
        description = "Read all P2P messages.",
        responseDescription = "Message ID to content"
    )
    fun read(
        @HttpRpcQueryParameter(description = "Group")
        group: String,
        @HttpRpcQueryParameter(description = "Source")
        source: String,
        @HttpRpcQueryParameter(description = "Target")
        target: String,
        @HttpRpcQueryParameter(description = "Timeout (seconds)")
        timeout: Int,
    ): Map<String, String>
}
