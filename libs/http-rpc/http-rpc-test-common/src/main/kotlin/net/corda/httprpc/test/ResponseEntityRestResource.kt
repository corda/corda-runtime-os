package net.corda.httprpc.test

import net.corda.httprpc.RestResource
import net.corda.httprpc.annotations.HttpRpcDELETE
import net.corda.httprpc.annotations.HttpRpcPOST
import net.corda.httprpc.annotations.HttpRpcPUT
import net.corda.httprpc.annotations.HttpRpcResource
import net.corda.httprpc.response.ResponseEntity

@HttpRpcResource(path = "responseentity")
interface ResponseEntityRestResource : RestResource {

    data class TestHttpEntity(val id: String)
    enum class DeleteStatus { DELETING }

    @HttpRpcPOST(path = "post-returns-void")
    fun postReturnsNoContent()

    @HttpRpcPOST(path = "post-returns-ok-string-json")
    fun postReturnsOkWithEscapedJson(): ResponseEntity<String>

    @HttpRpcPOST(path = "post-returns-raw-entity")
    fun postReturnsRawEntity(): TestHttpEntity

    @HttpRpcPUT(path = "put-returns-void")
    fun putReturnsVoid()

    @HttpRpcPUT(path = "put-returns-ok-string")
    fun putReturnsOkString(): ResponseEntity<String>

    @HttpRpcPUT(path = "put-returns-string")
    fun putReturnsString(): String

    @HttpRpcPUT(path = "put-returns-nullable-string")
    fun putReturnsNullableString(): String?

    @HttpRpcDELETE(path = "delete-returns-void")
    fun deleteReturnsVoid()

    @HttpRpcDELETE(path = "async-delete-returns-accepted")
    fun asyncDeleteReturnsAccepted(): ResponseEntity<DeleteStatus>

}