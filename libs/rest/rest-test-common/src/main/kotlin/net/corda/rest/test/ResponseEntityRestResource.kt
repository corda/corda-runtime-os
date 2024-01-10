package net.corda.rest.test

import net.corda.rest.RestResource
import net.corda.rest.annotations.HttpDELETE
import net.corda.rest.annotations.HttpPOST
import net.corda.rest.annotations.HttpPUT
import net.corda.rest.annotations.HttpRestResource
import net.corda.rest.response.ResponseEntity

@HttpRestResource(path = "responseentity")
interface ResponseEntityRestResource : RestResource {

    data class TestHttpEntity(val id: String)
    enum class DeleteStatus { DELETING }

    @HttpPOST(path = "post-returns-void")
    fun postReturnsNoContent()

    @HttpPOST(path = "post-returns-ok-string-json")
    fun postReturnsOkWithEscapedJson(): ResponseEntity<String>

    @HttpPOST(path = "post-returns-raw-entity")
    fun postReturnsRawEntity(): TestHttpEntity

    @HttpPUT(path = "put-returns-void")
    fun putReturnsVoid()

    @HttpPUT(path = "put-returns-ok-string")
    fun putReturnsOkString(): ResponseEntity<String>

    @HttpPUT(path = "put-returns-string")
    fun putReturnsString(): String

    @HttpPUT(path = "put-returns-nullable-string")
    fun putReturnsNullableString(): String?

    @HttpDELETE(path = "delete-returns-void")
    fun deleteReturnsVoid()

    @HttpDELETE(path = "async-delete-returns-accepted")
    fun asyncDeleteReturnsAccepted(): ResponseEntity<DeleteStatus>

}