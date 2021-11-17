package net.corda.libs.permissions.manager.response

import java.time.Instant

/**
 * Response object containing information for a User.
 */
data class UserResponseDto(
    val lastUpdatedTimestamp: Instant,
    val fullName: String,
    val loginName: String,
    val enabled: Boolean,
    val ssoAuth: Boolean,
    val passwordExpiry: Instant?,
    val parentGroup: String?,
    val properties: List<PropertyResponseDto>
)