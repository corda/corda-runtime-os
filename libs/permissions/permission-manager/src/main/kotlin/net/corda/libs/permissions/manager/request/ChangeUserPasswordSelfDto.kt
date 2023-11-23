package net.corda.libs.permissions.manager.request

data class ChangeUserPasswordSelfDto(
    /**
     * ID of the user making the request.
     */
    val requestedBy: String,

    /**
     * New password to be applied for user
     */
    val newPassword: String
)
