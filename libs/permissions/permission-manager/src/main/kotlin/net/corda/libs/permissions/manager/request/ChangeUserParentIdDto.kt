package net.corda.libs.permissions.manager.request

data class ChangeUserParentIdDto(
    /**
     * ID of the user making the request.
     */
    val requestedBy: String,
    /**
     * Login name of the User to change.
     */
    val loginName: String,
    /**
     * ID of the new parent Group.
     */
    val newParentGroupId: String?
)
