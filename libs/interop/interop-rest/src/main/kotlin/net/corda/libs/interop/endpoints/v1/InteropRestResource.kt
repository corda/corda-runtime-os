package net.corda.libs.interop.endpoints.v1

import net.corda.libs.interop.endpoints.v1.types.RestInteropIdentity
import net.corda.rest.RestResource
import net.corda.rest.annotations.HttpGET
import net.corda.rest.annotations.HttpPUT
import net.corda.rest.annotations.HttpRestResource
import net.corda.rest.annotations.RestPathParameter
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
        path = "{vnodeshorthash}/groups",
        title = "Lists all interop groups for a given holding identity.",
        description = "This method returns a list of interop group ids.",
        responseDescription = "Map of interop group ids to group policy"
    )
    fun getInterOpGroups(
        @RestPathParameter(description = "ID of the holding identity which groups are to be returned.")
        vnodeshorthash: String
    ): Map<UUID,String>

    /**
     * Endpoint to create interop identity
     */
    @HttpPUT(
        path = "{vnodeshorthash}/interopidentity",
        title = "Create interop identity.",
        description = "This method creates interop identity from holding identity id, group id and x500name.",
        responseDescription = "Response entity with the status of the request."
    )
    fun createInterOpIdentity(
        restInteropIdentity: RestInteropIdentity,
        @RestPathParameter(description = "ID of the holding identity.")
        vnodeshorthash: String
    ): ResponseEntity<String>

    /**
     * Get a list of interop identities belonging to the given holding identity.
     */
    @HttpGET(
        path = "{vnodeshorthash}/interopidentities",
        title = "Lists all interop identities belonging to a given holding identity",
        description = "This method returns a list of interop identities belonging to the given holding identity.",
        responseDescription = "List of interop identities"
    )
    fun getInterOpIdentities(
        @RestPathParameter(description = "ID of the holding identity which identities are to be returned.")
        vnodeshorthash: String
    ): List<RestInteropIdentity>
}