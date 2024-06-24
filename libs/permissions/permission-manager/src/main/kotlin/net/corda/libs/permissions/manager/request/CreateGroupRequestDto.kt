package net.corda.libs.permissions.manager.request

/**
 * Request object for creating a Group in the permission system.
 */
data class CreateGroupRequestDto(
    /**
     * ID of the user making the request.
     */
    val requestedBy: String,
    /**
     * Name of the Group.
     */
    val groupName: String,
    /**
     * Id of the new group's parent group.
     */
    val parentGroupId: String?,
)
