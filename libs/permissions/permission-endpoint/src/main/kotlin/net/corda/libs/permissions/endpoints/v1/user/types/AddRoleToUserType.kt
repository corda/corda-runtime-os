package net.corda.libs.permissions.endpoints.v1.user.types

/**
 * Request type for adding a Role to a User in the permission system.
 */
data class AddRoleToUserType(

    /**
     * Login name of the User.
     */
    val loginName: String,

    /**
     * Id of the role to associate with this user.
     */
    val roleId: String
)