package net.corda.libs.permissions.manager.response

import java.time.Instant

/**
 * Response object containing information for an identifier for Role entity.
 */
data class RoleAssociationResponseDto(
    val roleId: String,
    val createdTimestamp: Instant
)