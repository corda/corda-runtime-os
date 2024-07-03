package net.corda.libs.permissions.endpoints.v1.user

import net.corda.libs.permissions.endpoints.v1.user.types.CreateUserType
import net.corda.libs.permissions.endpoints.v1.user.types.PropertyResponseType
import net.corda.libs.permissions.endpoints.v1.user.types.UserPermissionSummaryResponseType
import net.corda.libs.permissions.endpoints.v1.user.types.UserResponseType
import net.corda.rest.RestResource
import net.corda.rest.SC_CREATED
import net.corda.rest.annotations.ClientRequestBodyParameter
import net.corda.rest.annotations.HttpDELETE
import net.corda.rest.annotations.HttpGET
import net.corda.rest.annotations.HttpPOST
import net.corda.rest.annotations.HttpPUT
import net.corda.rest.annotations.HttpRestResource
import net.corda.rest.annotations.RestApiVersion
import net.corda.rest.annotations.RestPathParameter
import net.corda.rest.response.ResponseEntity

/**
 * User endpoint exposes HTTP endpoints for management of Users in the RBAC permission system.
 */
@HttpRestResource(
    name = "RBAC User",
    description = "The RBAC User API consists of a number of endpoints enabling user management in the RBAC " +
        "(role-based access control) permission system. You can get details of specified users, create new users, " +
        "assign roles to users and remove roles from users.",
    path = "user"
)
interface UserEndpoint : RestResource {

    /**
     * Create a user in the RBAC permission system.
     */
    @HttpPOST(
        description = "This method creates a new user.",
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
            roleAssociations: A set of roles associated with the user account""",
        successCode = SC_CREATED
    )
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
                    value of null means that the password does not expire"""
        )
        createUserType: CreateUserType
    ): ResponseEntity<UserResponseType>

    @HttpGET(
        path = "{loginName}",
        description = "This method returns a user based on the specified login name.",
        responseDescription = """
            A user with the following attributes:
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
            roleAssociations: A set of roles associated with the user account""",
        minVersion = RestApiVersion.C5_1
    )
    fun getUserPath(
        @RestPathParameter(description = "The login name of the user to be returned")
        loginName: String
    ): UserResponseType

    @HttpDELETE(
        path = "{loginName}",
        description = "This method deletes a user based on the specified login name.",
        responseDescription = """
            A deleted user with the following attributes:
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
            roleAssociations: A set of roles associated with the user account""",
        minVersion = RestApiVersion.C5_3
    )
    fun deleteUser(
        @RestPathParameter(description = "The login name of the user to be deleted")
        loginName: String
    ): ResponseEntity<UserResponseType>

    @HttpPOST(
        path = "/selfpassword",
        description = "This method updates a users own password.",
        responseDescription = """
            A user with the following attributes:
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
            roleAssociations: A set of roles associated with the user account""",
        minVersion = RestApiVersion.C5_2
    )
    fun changeUserPasswordSelf(
        @ClientRequestBodyParameter(
            description = "The new password to apply.",
            required = true,
            name = "password"
        )
        password: String,
    ): UserResponseType

    @HttpPOST(
        path = "/otheruserpassword",
        description = "This method updates another user's password, only usable by admin.",
        responseDescription = """
            A user with the following attributes:
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
            roleAssociations: A set of roles associated with the user account""",
        minVersion = RestApiVersion.C5_2
    )
    fun changeOtherUserPassword(
        @ClientRequestBodyParameter(
            description = "Username for the password change.",
            required = true,
            name = "username"
        )
        username: String,
        @ClientRequestBodyParameter(
            description = "The new password to apply.",
            required = true,
            name = "password"
        )
        password: String,
    ): UserResponseType

    /**
     * Assign a Role to a User in the RBAC permission system.
     */
    @HttpPUT(
        path = "{loginName}/role/{roleId}",
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
            roleAssociations: A set of roles associated with the user account"""
    )
    fun addRole(
        @RestPathParameter(description = "The login name of the user")
        loginName: String,
        @RestPathParameter(description = "The ID of the role to assign to the user")
        roleId: String
    ): ResponseEntity<UserResponseType>

    /**
     * Un-assign a Role from a User in the RBAC permission system.
     */
    @HttpDELETE(
        path = "{loginName}/role/{roleId}",
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
            roleAssociations: A set of roles associated with the user account"""
    )
    fun removeRole(
        @RestPathParameter(description = "The login name of the user")
        loginName: String,
        @RestPathParameter(description = "The ID of the role to remove from the user")
        roleId: String
    ): ResponseEntity<UserResponseType>

    /**
     * Get a summary of a user's permissions.
     */
    @HttpGET(
        path = "{loginName}/permissionSummary",
        description = "This method returns a summary of the user's permissions.",
        responseDescription = """
            enabled: If true, the user account is enabled; false, the account is disabled
            lastUpdateTimestamp: The date and time when the user was last updated
            loginName: The login name of the user
            permissions: An array of one or more permissions associated with the user
        """
    )
    fun getPermissionSummary(
        @RestPathParameter(description = "The login name of the user")
        loginName: String
    ): UserPermissionSummaryResponseType

    /**
     * Add properties to a user
     */
    @HttpPOST(
        path = "{loginName}/property/",
        description = "This method adds a property to a user.",
        responseDescription = """
            Added properties to a user with the following attributes:
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
            roleAssociations: A set of roles associated with the user account
            
        """,
        minVersion = RestApiVersion.C5_3
    )
    fun addProperty(
        @RestPathParameter(description = "The login name of the user")
        loginName: String,
        @ClientRequestBodyParameter(
            description = "Property to add.",
            required = true,
            name = "property"
        )properties: Map<String, String>
    ): ResponseEntity<UserResponseType>

    /**
     * Removes properties from a user
     */
    @HttpDELETE(
        path = "{loginName}/property/{propertyKey}",
        description = "This method removes a property from a user.",
        responseDescription = """
            Removed properties from a user with the following attributes:
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
            roleAssociations: A set of roles associated with the user account

        """,
        minVersion = RestApiVersion.C5_3
    )
    fun removeProperty(
        @RestPathParameter(description = "The login name of the user")
        loginName: String,
        @RestPathParameter(description = "The property to remove from the user")
        propertyKey: String
    ): ResponseEntity<UserResponseType>

    /**
     * Lists properties of a user
     */
    @HttpGET(
        path = "{loginName}/properties",
        description = "This method lists the properties of a user.",
        responseDescription = """
            List of properties, each in the following format:
            key: The name of the property.
            lastChangedTimestamp: The time at which the property was last changed.
            value: The value for the property.

        """,
        minVersion = RestApiVersion.C5_3
    )
    fun getUserProperties(
        @RestPathParameter(description = "The login name of the user")
        loginName: String,
    ): ResponseEntity<PropertyResponseType>

    /**
     * Gets all users for propertyKey = value
     */
    @HttpGET(
        path = "property",
        description = "This method gets all the users that have a specific value given a propertyKey.",
        responseDescription = """
            List of users, each with the following attributes:
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
            roleAssociations: A set of roles associated with the user account

        """,
        minVersion = RestApiVersion.C5_3
    )
    fun getUsersByPropertyKey(
        @ClientRequestBodyParameter(
            description = "Property key to look for.",
            required = true,
            name = "propertyKey"
        ) propertyKey: String,
        @ClientRequestBodyParameter(
            description = "Property value to match on.",
            required = true,
            name = "propertyValue"
        ) propertyValue: String
    ): ResponseEntity<UserResponseType>
}
