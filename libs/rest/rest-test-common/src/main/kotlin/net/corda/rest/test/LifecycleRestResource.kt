package net.corda.rest.test

import net.corda.rest.RestResource
import net.corda.rest.annotations.HttpGET
import net.corda.rest.annotations.RestPathParameter
import net.corda.rest.annotations.RestQueryParameter
import net.corda.rest.annotations.HttpRestResource

@HttpRestResource(name = "LifecycleRestResource", description = "LifecycleRestResource", path = "lifecycle/")
interface LifecycleRestResource : RestResource {

    @HttpGET(path = "hello/{name}", title = "Hello", description = "Hello endpoint")
    fun hello(
        @RestPathParameter(name = "name", description = "The name") pathParam: String,
        @RestQueryParameter(name = "id", description = "id", required = false) param: Int?
    ): String
}