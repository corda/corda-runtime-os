package net.corda.libs.permissions.manager.response

import java.time.Instant

/**
 * Response object containing information for an identifier for Permission entity.
 */
data class PermissionAssocResponseDto(
    val id: String,
    val createdTimestamp: Instant
)