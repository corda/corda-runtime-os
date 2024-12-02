package net.corda.libs.permissions.manager.request

data class ChangeGroupParentIdDto(
    /**
     * ID of the user making the request.
     */
    val requestedBy: String,
    /**
     * ID of the Group to change.
     */
    val groupId: String,
    /**
     * ID of the new parent Group.
     */
    val newParentGroupId: String?
)
