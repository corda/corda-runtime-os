package net.corda.rest.test

import net.corda.rest.RestResource
import net.corda.rest.annotations.HttpDELETE
import net.corda.rest.annotations.HttpGET
import net.corda.rest.annotations.HttpPOST
import net.corda.rest.annotations.HttpPUT
import net.corda.rest.annotations.RestPathParameter
import net.corda.rest.annotations.RestQueryParameter
import net.corda.rest.annotations.ClientRequestBodyParameter
import net.corda.rest.annotations.HttpRestResource

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