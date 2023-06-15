package net.corda.libs.interop.endpoints.v1

import net.corda.libs.interop.endpoints.v1.types.CreateInterOpIdentityType
import net.corda.libs.interop.endpoints.v1.types.InteropIdentityResponseType
import net.corda.rest.RestResource
import net.corda.rest.annotations.ClientRequestBodyParameter
import net.corda.rest.annotations.HttpGET
import net.corda.rest.annotations.HttpPUT
import net.corda.rest.annotations.HttpRestResource
import net.corda.rest.response.ResponseEntity
import java.util.UUID

@HttpRestResource(
    name = "Interop Identities API",
    description = "The interop identity API consists of a number of endpoints to query and create interop identities.",
    path = "interop"
)
interface InteropRestResource : RestResource {
    /**
     * Get a list of interop groups.
     */
    @HttpGET(
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
//        @RestPathParameter(description = "The X500 name of the identity to create")
//        x500Name: String,
//        @RestPathParameter(description = "The groupId of the group the identity belongs to.")
//        groupId: UUID
        @ClientRequestBodyParameter(
            description =
            """
                Details of the identity to be created: 
                x500Name - name of the identity
                groupId - group id of the interop group
            """)
        createInterOpIdentityType: CreateInterOpIdentityType
    ): ResponseEntity<InteropIdentityResponseType>
}
