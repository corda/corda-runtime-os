package net.corda.libs.interop.endpoints.v1

import net.corda.libs.interop.endpoints.v1.types.CreateInteropIdentityRequest
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
        path = "{holdingidentityid}/groups",
        title = "Lists all interop",
        description = "This method returns a list of interop group ids.",
        responseDescription = "List of interop groups"
    )
    fun getInterOpGroups(@RestPathParameter(description = "ID of the holding identity which groups are to be returned.")
                         holdingidentityid: String?): List<UUID>

    /**
     * Endpoint to create interop identity
     */
    @HttpPUT(
        path = "{holdingidentityid}/interopidentity",
        title = "Create interop identity.",
        description = "This method creates interop identity from x500name.",
        responseDescription = "Identifier for the request."
    )
    fun createInterOpIdentity(
        createInteropIdentityRequest: CreateInteropIdentityRequest,
        @RestPathParameter(description = "ID of the holding identity which groups are to be returned.")
        holdingidentityid: String?
    ): ResponseEntity<String>

    /**
     * Get a list of interop identities belonging to the given holding identity.
     */
    @HttpGET(
        path = "{holdingidentityid}/interopidentities",
        title = "Lists all interop identities belonging to a given holding identity",
        description = "This method returns a list of interop identities belonging to the given holding identity.",
        responseDescription = "List of interop identities"
    )
    fun getInterOpIdentities(@RestPathParameter(description = "ID of the holding identity which identities are to be returned.")
                         holdingidentityid: String?): List<CreateInteropIdentityRequest>
}