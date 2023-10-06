package net.corda.libs.interop.endpoints.v1

import net.corda.libs.interop.endpoints.v1.types.CreateInteropIdentityRest
import net.corda.libs.interop.endpoints.v1.types.ExportInteropIdentityRest
import net.corda.libs.interop.endpoints.v1.types.ImportInteropIdentityRest
import net.corda.libs.interop.endpoints.v1.types.InteropIdentityResponse
import net.corda.rest.RestResource
import net.corda.rest.annotations.HttpGET
import net.corda.rest.annotations.HttpPUT
import net.corda.rest.annotations.HttpRestResource
import net.corda.rest.annotations.RestPathParameter
import net.corda.rest.response.ResponseEntity
import java.util.UUID
import net.corda.rest.annotations.RestApiVersion

/** Rest operations for interop management. */
@HttpRestResource(
    name = "Interop API",
    description = "The interop API is used to administrate interop identities and interop groups.",
    path = "interop",
    minVersion = RestApiVersion.C5_1
)
interface InteropRestResource : RestResource {
    /**
     * Get a list of interop groups.
     */
    @HttpGET(
        path = "{holdingIdentityShortHash}/groups",
        title = "Get interop groups.",
        description = "Returns the set of interop groups within which the specified holding identity is participating.",
        responseDescription = "Map of interop group UUIDs to group policy information."
    )
    fun getInterOpGroups(
        @RestPathParameter(description = "Short hash the holding identity to query.")
        holdingIdentityShortHash: String
    ): Map<UUID, String>

    /**
     * Endpoint to create interop identity
     */
    @HttpPUT(
        path = "{holdingIdentityShortHash}/interopidentity",
        title = "Create interop identity.",
        description = "Creates an interop identity for the specified holding identity within the specified group " +
                      "with the specified application name.",
        responseDescription = "Response entity with the status of the request."
    )
    fun createInterOpIdentity(
        createInteropIdentityRestRequest: CreateInteropIdentityRest.Request,
        @RestPathParameter(description = "Short hash of holding identity to create interop identity for.")
        holdingIdentityShortHash: String
    ): CreateInteropIdentityRest.Response

    /**
     * Get a list of interop identities belonging to the given holding identity.
     */
    @HttpGET(
        path = "{holdingIdentityShortHash}/interopidentities",
        title = "Get interop identities.",
        description = "Returns a list of interop identities visible to the specified holding identity.",
        responseDescription = "List of interop identities."
    )
    fun getInterOpIdentities(
        @RestPathParameter(description = "Short hash of holding identity to get interop identities for.")
        holdingIdentityShortHash: String
    ): List<InteropIdentityResponse>

    /**
     * Get the details of my interop identity.
     */
    @HttpGET(
        path = "{holdingIdentityShortHash}/export/identity/{interopIdentityShortHash}",
        title = "Export interop identity.",
        description = "Export an interop identity for import elsewhere.",
        responseDescription = "Interop identity in a format suitable for import."
    )
    fun exportInterOpIdentity(
        @RestPathParameter(description = "Short hash of the holding identity to export interop identity from.")
        holdingIdentityShortHash: String,
        @RestPathParameter(description = "Short hash of the interop identity to export.")
        interopIdentityShortHash: String
    ): ExportInteropIdentityRest.Response

    /**
     * Import the details of my interop identity into the system of another interop identity.
     */
    @HttpPUT(
        path = "{holdingIdentityShortHash}/import/identity",
        title = "Import interop identity.",
        description = "Import an interop identity which was previously exported from elsewhere.",
        responseDescription = "Response entity with the status of the request."
    )
    fun importInterOpIdentity(
        importInteropIdentityRestRequest: ImportInteropIdentityRest.Request,
        @RestPathParameter(
            description = "Short hash of the holding identity of the virtual node into which the interop " +
            "identity will be imported."
        )
        holdingIdentityShortHash: String
    ): ResponseEntity<String>
}