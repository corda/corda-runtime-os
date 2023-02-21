package net.corda.httprpc.test

import net.corda.httprpc.RestResource
import net.corda.httprpc.annotations.HttpDELETE
import net.corda.httprpc.annotations.HttpGET
import net.corda.httprpc.annotations.HttpPOST
import net.corda.httprpc.annotations.HttpPUT
import net.corda.httprpc.annotations.RestPathParameter
import net.corda.httprpc.annotations.RestQueryParameter
import net.corda.httprpc.annotations.ClientRequestBodyParameter
import net.corda.httprpc.annotations.HttpRestResource

@HttpRestResource(name = "TestEntity", description = "RESTful operations on Test Entity", path = "testEntity/")
interface TestEntityRestResource : RestResource {

    data class CreationParams(val name: String, val amount: Int)

    @HttpPOST
    fun create(@ClientRequestBodyParameter creationParams: CreationParams): String

    @HttpGET(path = "{id}")
    fun getUsingPath(@RestPathParameter id: String): String

    @HttpGET
    fun getUsingQuery(@RestQueryParameter query: String): String

    data class UpdateParams(val id: String, val name: String, val amount: Int)

    @HttpPUT
    fun update(@ClientRequestBodyParameter updateParams: UpdateParams): String

    @HttpDELETE(path = "{id}")
    fun deleteUsingPath(@RestPathParameter id: String): String

    @HttpDELETE
    fun deleteUsingQuery(@RestQueryParameter query: String): String
}