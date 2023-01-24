package net.corda.libs.permissions.endpoints.v1.user

import net.corda.httprpc.RestResource
import net.corda.httprpc.annotations.HttpRpcDELETE
import net.corda.httprpc.annotations.HttpRpcGET
import net.corda.httprpc.annotations.HttpRpcPOST
import net.corda.httprpc.annotations.HttpRpcPUT
import net.corda.httprpc.annotations.HttpRpcPathParameter
import net.corda.httprpc.annotations.HttpRpcQueryParameter
import net.corda.httprpc.annotations.HttpRpcRequestBodyParameter
import net.corda.httprpc.annotations.HttpRpcResource
import net.corda.httprpc.response.ResponseEntity
import net.corda.libs.permissions.endpoints.v1.user.types.CreateUserType
import net.corda.libs.permissions.endpoints.v1.user.types.UserPermissionSummaryResponseType
import net.corda.libs.permissions.endpoints.v1.user.types.UserResponseType

/**
 * User endpoint exposes HTTP endpoints for management of Users in the RBAC permission system.
 */
@HttpRpcResource(
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
    @HttpRpcPOST(description = "This method creates a new user.",
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
        @HttpRpcRequestBodyParameter(
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
    @HttpRpcGET(description = "This method returns a user based on the specified login name.",
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
        @HttpRpcQueryParameter(description = "The login name of the user to be returned")
        loginName: String
    ): UserResponseType

    /**
     * Assign a Role to a User in the RBAC permission system.
     */
    @HttpRpcPUT(path = "{loginName}/role/{roleId}",
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
        @HttpRpcPathParameter(description = "The login name of the user")
        loginName: String,
        @HttpRpcPathParameter(description = "The ID of the role to assign to the user")
        roleId: String
    ): ResponseEntity<UserResponseType>

    /**
     * Un-assign a Role from a User in the RBAC permission system.
     */
    @HttpRpcDELETE(path = "{loginName}/role/{roleId}",
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
        @HttpRpcPathParameter(description = "The login name of the user")
        loginName: String,
        @HttpRpcPathParameter(description = "The ID of the role to remove from the user")
        roleId: String
    ): ResponseEntity<UserResponseType>

    /**
     * Get a summary of a user's permissions.
     */
    @HttpRpcGET(path = "{loginName}/permissionSummary",
        description = "This method returns a summary of the user's permissions.",
        responseDescription = """
            enabled: If true, the user account is enabled; false, the account is disabled
            lastUpdateTimestamp: The date and time when the user was last updated
            loginName: The login name of the user
            permissions: An array of one or more permissions associated with the user
        """)
    fun getPermissionSummary(
        @HttpRpcPathParameter(description = "The login name of the user")
        loginName: String
    ): UserPermissionSummaryResponseType
}