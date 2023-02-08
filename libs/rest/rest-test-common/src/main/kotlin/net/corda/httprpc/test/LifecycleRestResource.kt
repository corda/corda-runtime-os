package net.corda.httprpc.test

import net.corda.httprpc.RestResource
import net.corda.httprpc.annotations.HttpGET
import net.corda.httprpc.annotations.RestPathParameter
import net.corda.httprpc.annotations.RestQueryParameter
import net.corda.httprpc.annotations.HttpRestResource

@HttpRestResource(name = "LifecycleRestResource", description = "LifecycleRestResource", path = "lifecycle/")
interface LifecycleRestResource : RestResource {

    @HttpGET(path = "hello/{name}", title = "Hello", description = "Hello endpoint")
    fun hello(
        @RestPathParameter(name = "name", description = "The name") pathParam: String,
        @RestQueryParameter(name = "id", description = "id", required = false) param: Int?
    ): String
}