package net.corda.libs.interop.endpoints.v1

/**
 * Request object for creating an interop identity.
 */
data class CreateInteropIdentityRequestDto(
    /**
     * ID of the user making the request.
     */
    val requestedBy: String,
    /**
     * Name of the identity.
     */
    val x500Name: String,
    /**
     * Group id of the interop group.
     */
    val groupId: String
)