package net.corda.libs.permissions.endpoints.v1.user

import net.corda.httprpc.RestResource
import net.corda.httprpc.annotations.HttpDELETE
import net.corda.httprpc.annotations.HttpGET
import net.corda.httprpc.annotations.HttpPOST
import net.corda.httprpc.annotations.HttpPUT
import net.corda.httprpc.annotations.RestPathParameter
import net.corda.httprpc.annotations.RestQueryParameter
import net.corda.httprpc.annotations.ClientRequestBodyParameter
import net.corda.httprpc.annotations.HttpRestResource
import net.corda.httprpc.response.ResponseEntity
import net.corda.libs.permissions.endpoints.v1.user.types.CreateUserType
import net.corda.libs.permissions.endpoints.v1.user.types.UserPermissionSummaryResponseType
import net.corda.libs.permissions.endpoints.v1.user.types.UserResponseType

/**
 * User endpoint exposes HTTP endpoints for management of Users in the RBAC permission system.
 */
@HttpRestResource(
    name = "RBAC User API",
    description = "The RBAC User API consists of a number of endpoints enabling user management in the RBAC " +
            "(role-based access control) permission system. You can get details of specified users, create new users, " +
            "assign roles to users and remove roles from users.",
    path = "user"
)
interface UserEndpoint : RestResource {

    /**
     * Create a user in the RBAC permission system.
     */
    @HttpPOST(description = "This method creates a new user.",
        responseDescription = """
            A newly created user with the following attributes:
            id: Unique server generated identifier for the user
            version: The version of the user; version 0 is assigned to a newly created user
            updateTimestamp: The date and time when the user was last updated
            fullName: The full name for the new user
            loginName: The login name for the new user
            enabled: If true, the user account is enabled; false, the account is disabled
            ssoAuth: If true, the user account is enabled for SSO authentication; 
                false, the account is enabled for password authentication
            passwordExpiry: The date and time when the password should expire, specified as an ISO-8601 string;
                    value of null means that the password does not expire
            parentGroup: An optional identifier of the user group for the new user to be included;
                    value of null means that the user will belong to the root group
            properties: An optional set of key/value properties associated with a user account
            roleAssociations: A set of roles associated with the user account""")
    fun createUser(
        @ClientRequestBodyParameter(
            description = """
                Details of the user to be created with the following parameters:
                enabled: If true, the user account is enabled; false, the account is disabled
                fullName: The full name for the new user
                initialPassword: The initial password for the new user; 
                    the value can be null for Single Sign On (SSO) users
                loginName: The login name for the new user
                parentGroup: An optional identifier of the user group for the new user to be included;
                    value of null means that the user will belong to the root group
                passwordExpiry: The date and time when the password should expire, specified as an ISO-8601 string;
                    value of null means that the password does not expire""")
        createUserType: CreateUserType
    ): ResponseEntity<UserResponseType>

    /**
     * Get a user by loginName in the RBAC permission system.
     */
    @HttpGET(description = "This method returns a user based on the specified login name.",
        responseDescription = """
            A newly created user with the following attributes:
            id: Unique server generated identifier for the user
            version: The version of the user; version 0 is assigned to a newly created user
            updateTimestamp: The date and time when the user was last updated
            fullName: The full name for the new user
            loginName: The login name for the new user
            enabled: If true, the user account is enabled; false, the account is disabled
            ssoAuth: If true, the user account is enabled for SSO authentication; 
                false, the account is enabled for password authentication
            passwordExpiry: The date and time when the password should expire, specified as an ISO-8601 string;
                    value of null means that the password does not expire
            parentGroup: An optional identifier of the user group for the new user to be included;
                    value of null means that the user will belong to the root group
            properties: An optional set of key/value properties associated with a user account
            roleAssociations: A set of roles associated with the user account""")
    fun getUser(
        @RestQueryParameter(description = "The login name of the user to be returned")
        loginName: String
    ): UserResponseType

    /**
     * Assign a Role to a User in the RBAC permission system.
     */
    @HttpPUT(path = "{loginName}/role/{roleId}",
        description = "This method assigns a specified role to a specified user.",
        responseDescription = """
            A newly created user with the following attributes:
            id: Unique server generated identifier for the user
            version: The version of the user; version 0 is assigned to a newly created user
            updateTimestamp: The date and time when the user was last updated
            fullName: The full name for the new user
            loginName: The login name for the new user
            enabled: If true, the user account is enabled; false, the account is disabled
            ssoAuth: If true, the user account is enabled for SSO authentication; 
                false, the account is enabled for password authentication
            passwordExpiry: The date and time when the password should expire, specified as an ISO-8601 string;
                    value of null means that the password does not expire
            parentGroup: An optional identifier of the user group for the new user to be included;
                    value of null means that the user will belong to the root group
            properties: An optional set of key/value properties associated with a user account
            roleAssociations: A set of roles associated with the user account""")
    fun addRole(
        @RestPathParameter(description = "The login name of the user")
        loginName: String,
        @RestPathParameter(description = "The ID of the role to assign to the user")
        roleId: String
    ): ResponseEntity<UserResponseType>

    /**
     * Un-assign a Role from a User in the RBAC permission system.
     */
    @HttpDELETE(path = "{loginName}/role/{roleId}",
        description = "This method removes the specified role from the specified user.",
        responseDescription = """
            A newly created user with the following attributes:
            id: Unique server generated identifier for the user
            version: The version of the user; version 0 is assigned to a newly created user
            updateTimestamp: The date and time when the user was last updated
            fullName: The full name for the new user
            loginName: The login name for the new user
            enabled: If true, the user account is enabled; false, the account is disabled
            ssoAuth: If true, the user account is enabled for SSO authentication; 
                false, the account is enabled for password authentication
            passwordExpiry: The date and time when the password should expire, specified as an ISO-8601 string;
                    value of null means that the password does not expire
            parentGroup: An optional identifier of the user group for the new user to be included;
                    value of null means that the user will belong to the root group
            properties: An optional set of key/value properties associated with a user account
            roleAssociations: A set of roles associated with the user account""")
    fun removeRole(
        @RestPathParameter(description = "The login name of the user")
        loginName: String,
        @RestPathParameter(description = "The ID of the role to remove from the user")
        roleId: String
    ): ResponseEntity<UserResponseType>

    /**
     * Get a summary of a user's permissions.
     */
    @HttpGET(path = "{loginName}/permissionSummary",
        description = "This method returns a summary of the user's permissions.",
        responseDescription = """
            enabled: If true, the user account is enabled; false, the account is disabled
            lastUpdateTimestamp: The date and time when the user was last updated
            loginName: The login name of the user
            permissions: An array of one or more permissions associated with the user
        """)
    fun getPermissionSummary(
        @RestPathParameter(description = "The login name of the user")
        loginName: String
    ): UserPermissionSummaryResponseType
}