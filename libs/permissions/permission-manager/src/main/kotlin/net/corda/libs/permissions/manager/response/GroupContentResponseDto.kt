package net.corda.libs.permissions.manager.response

import java.time.Instant

/**
 * Response object containing information for a Group, with added users and subgroup context.
 */
data class GroupContentResponseDto(
    val id: String,
    val lastUpdatedTimestamp: Instant,
    val groupName: String,
    val parentGroupId: String,
    val properties: List<PropertyResponseDto>,
    val roleAssociations: List<RoleAssociationResponseDto>,
    val users: List<String>,
    val subgroups: List<String>
)