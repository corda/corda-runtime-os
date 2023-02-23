package net.corda.httprpc.test

import net.corda.httprpc.JsonObject
import net.corda.httprpc.RestResource
import net.corda.httprpc.annotations.HttpPOST
import net.corda.httprpc.annotations.ClientRequestBodyParameter
import net.corda.httprpc.annotations.HttpRestResource

@HttpRestResource(
    name = "ObjectsInJsonEndpoint",
    description = "RESTful operations with json objects in payloads",
    path = "objects-in-json-endpoint"
)
interface ObjectsInJsonEndpoint : RestResource {

    data class RequestWithJsonObject(val id: String, val obj: JsonObject)
    data class ResponseWithJsonObject(val id: String, val obj: JsonObject)
    data class ResponseWithJsonObjectNullable(val id: String, val obj: JsonObject?)

    @HttpPOST(path = "create-with-one-object")
    fun createWithOneObject(@ClientRequestBodyParameter creationObject: RequestWithJsonObject): ResponseWithJsonObject

    @HttpPOST(path = "create-with-individual-params")
    fun createWithIndividualParams(
        @ClientRequestBodyParameter id: String,
        @ClientRequestBodyParameter obj: JsonObject
    ): ResponseWithJsonObject

    @HttpPOST(path = "nullable-json-object-in-request")
    fun nullableJsonObjectInRequest(
        @ClientRequestBodyParameter id: String,
        @ClientRequestBodyParameter obj: JsonObject?
    ): ResponseWithJsonObjectNullable
}