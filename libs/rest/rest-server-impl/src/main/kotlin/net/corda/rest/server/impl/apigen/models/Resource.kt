package net.corda.rest.server.impl.apigen.models

import net.corda.rest.annotations.RestApiVersion

internal data class Resource(
    /**
     * The name of the resource, used for the API spec.
     */
    val name: String,
    /**
     * The description of the resource, used for the API spec.
     */
    val description: String,
    /**
     * The path of the resource, without containing the base path of the API.
     * Every endpoint under this resource will start with this path.
     */
    val path: String,
    /**
     * The endpoints under this resource.
     */
    val endpoints: Set<Endpoint>,

    /**
     * A set of REST API versions fot this resource.
     */
    val apiVersions: Set<RestApiVersion>
)
