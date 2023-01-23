package net.corda.httprpc.test

import net.corda.httprpc.RestResource
import net.corda.httprpc.annotations.HttpRpcDELETE
import net.corda.httprpc.annotations.HttpRpcGET
import net.corda.httprpc.annotations.HttpRpcPOST
import net.corda.httprpc.annotations.HttpRpcPUT
import net.corda.httprpc.annotations.HttpRpcPathParameter
import net.corda.httprpc.annotations.HttpRpcQueryParameter
import net.corda.httprpc.annotations.HttpRpcRequestBodyParameter
import net.corda.httprpc.annotations.HttpRpcResource

@HttpRpcResource(name = "TestEntity", description = "RESTful operations on Test Entity", path = "testEntity/")
interface TestEntityRestResource : RestResource {

    data class CreationParams(val name: String, val amount: Int)

    @HttpRpcPOST
    fun create(@HttpRpcRequestBodyParameter creationParams: CreationParams): String

    @HttpRpcGET(path = "{id}")
    fun getUsingPath(@HttpRpcPathParameter id: String): String

    @HttpRpcGET
    fun getUsingQuery(@HttpRpcQueryParameter query: String): String

    data class UpdateParams(val id: String, val name: String, val amount: Int)

    @HttpRpcPUT
    fun update(@HttpRpcRequestBodyParameter updateParams: UpdateParams): String

    @HttpRpcDELETE(path = "{id}")
    fun deleteUsingPath(@HttpRpcPathParameter id: String): String

    @HttpRpcDELETE
    fun deleteUsingQuery(@HttpRpcQueryParameter query: String): String
}