package net.corda.libs.permissions.endpoints.v1.user.types

/**
 * Request type for removing a Role from a User in the permission system.
 */
data class RemoveRoleFromUserType(

    /**
     * Login name of the User.
     */
    val loginName: String,

    /**
     * Id of the role to remove from this user.
     */
    val roleId: String
)