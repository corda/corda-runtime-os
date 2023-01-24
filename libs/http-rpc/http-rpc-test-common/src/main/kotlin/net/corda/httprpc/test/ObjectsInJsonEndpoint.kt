package net.corda.httprpc.test

import net.corda.httprpc.JsonObject
import net.corda.httprpc.RestResource
import net.corda.httprpc.annotations.HttpRpcPOST
import net.corda.httprpc.annotations.HttpRpcRequestBodyParameter
import net.corda.httprpc.annotations.HttpRpcResource

@HttpRpcResource(
    name = "ObjectsInJsonEndpoint",
    description = "RESTful operations with json objects in payloads",
    path = "objects-in-json-endpoint"
)
interface ObjectsInJsonEndpoint : RestResource {

    data class RequestWithJsonObject(val id: String, val obj: JsonObject)
    data class ResponseWithJsonObject(val id: String, val obj: JsonObject)
    data class ResponseWithJsonObjectNullable(val id: String, val obj: JsonObject?)

    @HttpRpcPOST(path = "create-with-one-object")
    fun createWithOneObject(@HttpRpcRequestBodyParameter creationObject: RequestWithJsonObject): ResponseWithJsonObject

    @HttpRpcPOST(path = "create-with-individual-params")
    fun createWithIndividualParams(
        @HttpRpcRequestBodyParameter id: String,
        @HttpRpcRequestBodyParameter obj: JsonObject
    ): ResponseWithJsonObject

    @HttpRpcPOST(path = "nullable-json-object-in-request")
    fun nullableJsonObjectInRequest(
        @HttpRpcRequestBodyParameter id: String,
        @HttpRpcRequestBodyParameter obj: JsonObject?
    ): ResponseWithJsonObjectNullable
}