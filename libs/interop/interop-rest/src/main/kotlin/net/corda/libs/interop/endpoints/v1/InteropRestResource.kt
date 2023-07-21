package net.corda.libs.interop.endpoints.v1

import net.corda.libs.interop.endpoints.v1.types.InteropIdentityResponse
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
    ): List<InteropIdentityResponse>

    /**
     * Get the details of my interop identity.
     */
    @HttpGET(
        path = "{vnodeshorthash}/export/identity/{interopIdentityShortHash}",
        title = "Returns groupId, group policy and InterOpIdentityInfo belonging to a given interop identity",
        description = "This method returns Json String containing GroupID, Group Policy and my InterOpIdentityInfo.",
        responseDescription = "Interop identity"
    )
    fun exportInterOpIdentity(
        @RestPathParameter(description = "ID of the holding identity which interop identity is to be returned.")
        vnodeshorthash: String,
        @RestPathParameter(description = "ShortHash of the interop identity")
        interopIdentityShortHash: String
    ): String

    /**
     * Import the details of my interop identity into the system of another interop identity.
     */
    @HttpPUT(
        path = "{vnodeshorthash}/import/identity",
        title = "Imports the interop identity details into the system of another interop identity",
        description = "This method imports the interop identity.",
        responseDescription = "Response entity with the status of the request."
    )
    fun importInterOpIdentity(
        restInteropIdentity: RestInteropIdentity,
        @RestPathParameter(description = "ID of the holding identity which interop identity is to be returned.")
        vnodeshorthash: String
    ): ResponseEntity<String>
}