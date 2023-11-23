package net.corda.libs.permissions.manager.request

data class ChangeUserPasswordOtherDto(
    /**
     * ID of the user making the request.
     */
    val requestedBy: String,

    /**
     * UserName of user who's password is being updated.
     */
    val otherUser: String,

    /**
     * New password to be applied for user
     */
    val newPassword: String
)
