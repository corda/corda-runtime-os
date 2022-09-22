package net.corda.httprpc.test

import net.corda.httprpc.PluggableRPCOps
import net.corda.httprpc.response.ResponseEntity

class ResponseEntityRpcOpsImpl : ResponseEntityRpcOps, PluggableRPCOps<ResponseEntityRpcOps> {
    override fun postReturnsNoContent() {}

    override fun postReturnsOkWithEscapedJson(): ResponseEntity<String> {
        return ResponseEntity.ok("{\"somejson\": \"for confusion\"}")
    }

    override fun postReturnsRawEntity(): ResponseEntityRpcOps.TestHttpEntity {
        return ResponseEntityRpcOps.TestHttpEntity("no response entity used")
    }

    override fun putReturnsOkString(): ResponseEntity<String> {
        return ResponseEntity.ok("some string that isn't json inside response")
    }

    override fun putReturnsString(): String {
        return "put string"
    }

    override fun putReturnsNullableString(): String? {
        return null
    }

    override fun putReturnsVoid() {}

    override fun deleteReturnsVoid() {}

    override fun asyncDeleteReturnsAccepted(): ResponseEntity<ResponseEntityRpcOps.DeleteStatus> {
        return ResponseEntity.accepted(ResponseEntityRpcOps.DeleteStatus.DELETING)
    }

    override val protocolVersion: Int
        get() = 1

    override val targetInterface: Class<ResponseEntityRpcOps>
        get() = ResponseEntityRpcOps::class.java
}