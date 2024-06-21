package net.corda.libs.permissions.endpoints.v1.group.types

/**
 * Request type for creating a Group in the permission system.
 */
data class CreateGroupType(
    /**
     * Human-readable name of the group.
     */
    val name: String,

    /**
     * The group to which the Group belongs.
     */
    val parentGroupId: String?
)