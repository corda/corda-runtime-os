package net.corda.libs.permissions.manager.response

import java.time.Instant

/**
 * Response object containing information for a Group.
 */
data class GroupResponseDto(
    val id: String,
    val lastUpdatedTimestamp: Instant,
    val groupName: String,
    val parentGroupId: String,
)