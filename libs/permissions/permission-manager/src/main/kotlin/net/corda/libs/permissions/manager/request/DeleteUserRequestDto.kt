package net.corda.libs.permissions.manager.request

data class DeleteUserRequestDto(
    /**
     * ID of the user making the request.
     */
    val requestedBy: String,
    /**
     * Login name of the User to delete.
     */
    val loginName: String,
)
