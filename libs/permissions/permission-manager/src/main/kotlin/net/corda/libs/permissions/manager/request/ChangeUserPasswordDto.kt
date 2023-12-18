package net.corda.libs.permissions.manager.request

data class ChangeUserPasswordDto(
    /**
     * ID of the user making the request.
     */
    val requestedBy: String,

    /**
     * UserName of user whose password is to be updated.
     */
    val username: String,

    /**
     * New password to be applied for user
     */
    val newPassword: String
)
