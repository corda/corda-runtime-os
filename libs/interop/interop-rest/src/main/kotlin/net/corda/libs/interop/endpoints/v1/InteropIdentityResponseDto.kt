package net.corda.libs.interop.endpoints.v1

import java.util.UUID

/**
 * Response object containing information for an InteropIdentity.
 */
data class InteropIdentityResponseDto(
    val x500Name: String,
    val groupId: UUID
)