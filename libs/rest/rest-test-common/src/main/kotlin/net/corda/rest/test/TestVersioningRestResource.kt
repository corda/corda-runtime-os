package net.corda.rest.test

import net.corda.rest.RestResource
import net.corda.rest.annotations.HttpGET
import net.corda.rest.annotations.HttpRestResource
import net.corda.rest.annotations.RestApiVersion
import net.corda.rest.annotations.RestPathParameter
import net.corda.rest.annotations.RestQueryParameter

@HttpRestResource(
    name = "TestEndpointVersioning",
    description = "RESTful operations on Test Entity",
    path = "testEndpointVersion/",
    minVersion = RestApiVersion.C5_1,
    maxVersion = RestApiVersion.C5_3
)
interface TestEndpointVersioningRestResource : RestResource {
    @Deprecated("Deprecated in favour of `getUsingPath()`")
    @HttpGET(minVersion = RestApiVersion.C5_1, maxVersion = RestApiVersion.C5_1)
    fun getUsingQuery(@RestQueryParameter id: String): String

    @HttpGET(path = "{id}", minVersion = RestApiVersion.C5_2, maxVersion = RestApiVersion.C5_3)
    fun getUsingPath(@RestPathParameter id: String): String
}

@HttpRestResource(
    name = "TestResourceVersioning",
    description = "RESTful operations on Test Entity",
    path = "testResourceVersion/",
    minVersion = RestApiVersion.C5_2,
    maxVersion = RestApiVersion.C5_3
)
interface TestResourceVersioningRestResource : RestResource {
    @Deprecated("Deprecated in favour of `getUsingPath()`")
    @HttpGET()
    fun getUsingQuery(@RestQueryParameter id: String): String

    @HttpGET(path = "{id}", minVersion = RestApiVersion.C5_1, maxVersion = RestApiVersion.C5_1)
    fun getUsingPath(@RestPathParameter id: String): String
}

@HttpRestResource(
    name = "TestResourceMaxVersion",
    description = "RESTful operations on Test Entity",
    path = "testResourceMaxVersion/",
    minVersion = RestApiVersion.C5_1,
    maxVersion = RestApiVersion.C5_3
)
interface TestResourceMaxVersioningRestResource : RestResource {
    @Deprecated("Deprecated in favour of `getUsingPath()`")
    @HttpGET()
    fun getUsingQuery(@RestQueryParameter id: String): String

    @HttpGET(path = "{id}", minVersion = RestApiVersion.C5_1)
    fun getUsingPath(@RestPathParameter id: String): String
}
