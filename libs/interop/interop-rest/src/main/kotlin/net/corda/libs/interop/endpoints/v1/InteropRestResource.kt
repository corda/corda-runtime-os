package net.corda.libs.interop.endpoints.v1

import net.corda.rest.RestResource
import net.corda.rest.annotations.HttpGET
import net.corda.rest.annotations.HttpPUT
import net.corda.rest.annotations.HttpRestResource
import net.corda.rest.annotations.RestPathParameter
import net.corda.rest.asynchronous.v1.AsyncResponse
import net.corda.rest.response.ResponseEntity
import java.util.UUID

/** Rest operations for interop management. */
@HttpRestResource(
    name = "Interop API",
    description = "The Interop API consists of a number of endpoints to manage interop functionality.",
    path = "interop"
)
interface InteropRestResource : RestResource {
    /**
     * Get a list of interop groups.
     */
    @HttpGET(
        path = "groups",
        title = "Lists all interop",
        description = "This method returns a list of interop group ids.",
        responseDescription = "List of interop groups"
    )
    fun getInterOpGroups(): List<UUID>

    /**
     * Asynchronous endpoint to create interop identity
     */
    @HttpPUT(
        path = "{x500Name}/{groupId}",
        title = "Create interop identity.",
        description = "This method creates interop identity from x500name.",
        responseDescription = "Identifier for the request."
    )
    fun createInterOpIdentity(
        @RestPathParameter(description = "The X500 name of the identity to create")
        x500Name: String,
        @RestPathParameter(description = "The groupId of the group the identity belongs to.")
        groupId: String
    ): ResponseEntity<AsyncResponse>
}