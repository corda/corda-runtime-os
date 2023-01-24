package net.corda.httprpc.test

import net.corda.httprpc.RestResource
import net.corda.httprpc.annotations.HttpRpcGET
import net.corda.httprpc.annotations.HttpRpcPathParameter
import net.corda.httprpc.annotations.HttpRpcQueryParameter
import net.corda.httprpc.annotations.HttpRpcResource

@HttpRpcResource(name = "LifecycleRestResource", description = "LifecycleRestResource", path = "lifecycle/")
interface LifecycleRestResource : RestResource {

    @HttpRpcGET(path = "hello/{name}", title = "Hello", description = "Hello endpoint")
    fun hello(
        @HttpRpcPathParameter(name = "name", description = "The name") pathParam: String,
        @HttpRpcQueryParameter(name = "id", description = "id", required = false) param: Int?
    ): String
}