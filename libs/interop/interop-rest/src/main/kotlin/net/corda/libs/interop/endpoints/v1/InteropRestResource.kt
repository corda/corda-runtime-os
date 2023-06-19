package net.corda.libs.interop.endpoints.v1

import net.corda.libs.interop.endpoints.v1.types.CreateInteropIdentityType
import net.corda.libs.interop.endpoints.v1.types.InteropIdentityResponseType
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
     * Asynchronous endpoint to create interop identity
     */
    @HttpPUT(
//        path = "{x500Name}/{groupId}",
        path = "interopidentity",
        title = "Create interop identity.",
        description = "This method creates interop identity from x500name.",
        responseDescription = "Identifier for the request."
    )
    fun createInterOpIdentity(
//        @RestPathParameter(description = "The X500 name of the identity to create")
//        x500Name: String,
//        @RestPathParameter(description = "The groupId of the group the identity belongs to.")
//        groupId: String
        createInteropIdentityType: CreateInteropIdentityType
    ): ResponseEntity<InteropIdentityResponseType>
}