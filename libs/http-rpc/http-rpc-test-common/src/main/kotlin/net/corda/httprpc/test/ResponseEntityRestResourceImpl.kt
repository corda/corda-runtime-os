package net.corda.httprpc.test

import net.corda.httprpc.PluggableRestResource
import net.corda.httprpc.response.ResponseEntity

class ResponseEntityRestResourceImpl : ResponseEntityRestResource, PluggableRestResource<ResponseEntityRestResource> {
    override fun postReturnsNoContent() {}

    override fun postReturnsOkWithEscapedJson(): ResponseEntity<String> {
        return ResponseEntity.ok("{\"somejson\": \"for confusion\"}")
    }

    override fun postReturnsRawEntity(): ResponseEntityRestResource.TestHttpEntity {
        return ResponseEntityRestResource.TestHttpEntity("no response entity used")
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

    override fun asyncDeleteReturnsAccepted(): ResponseEntity<ResponseEntityRestResource.DeleteStatus> {
        return ResponseEntity.accepted(ResponseEntityRestResource.DeleteStatus.DELETING)
    }

    override val protocolVersion: Int
        get() = 1

    override val targetInterface: Class<ResponseEntityRestResource>
        get() = ResponseEntityRestResource::class.java
}