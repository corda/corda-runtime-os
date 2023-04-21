package net.corda.libs.permissions.endpoints.v1.role

import net.corda.rest.RestResource
import net.corda.rest.annotations.HttpDELETE
import net.corda.rest.annotations.HttpGET
import net.corda.rest.annotations.HttpPOST
import net.corda.rest.annotations.HttpPUT
import net.corda.rest.annotations.RestPathParameter
import net.corda.rest.annotations.ClientRequestBodyParameter
import net.corda.rest.annotations.HttpRestResource
import net.corda.rest.response.ResponseEntity
import net.corda.libs.permissions.endpoints.v1.role.types.CreateRoleType
import net.corda.libs.permissions.endpoints.v1.role.types.RoleResponseType

/**
 * Role endpoint exposes HTTP operations for management of Roles in the RBAC permission system.
 */
@HttpRestResource(
    name = "RBAC Role API",
    description = "The RBAC Role API consists of a number of endpoints enabling role management in the RBAC " +
            "(role-based access control) permission system. You can get all roles in the system, " +
            "create new roles and add and delete permissions from roles.",
    path = "role"
)
interface RoleEndpoint : RestResource {

    /**
     * Get all the roles available in RBAC permission system.
     */
    @HttpGET(description = "This method returns an array with information about all roles in the permission system.",
    responseDescription = """
        Set of roles with each role having the following attributes: 
        id: The unique identifier of the role
        version: The version number of the role
        updateTimestamp: The date and time when the role was last updated
        roleName: The name of the role
        groupVisibility: An optional group visibility of the role
        permissions: The list of permissions associated with the role
    """)
    fun getRoles(): Set<RoleResponseType>

    /**
     * Create a role in the RBAC permission system.
     */
    @HttpPOST(description = "The method creates a new role in the RBAC permission system.",
    responseDescription = """
        Newly created role with attributes:
        id: The unique identifier of the role
        version: The version number of the role
        updateTimestamp: The date and time when the role was last updated
        roleName: The name of the role
        groupVisibility: An optional group visibility of the role
        permissions: The list of permissions associated with the role""")
    fun createRole(
        @ClientRequestBodyParameter(
            description =
            """
                Details of the role to be created: 
                roleName - name of the role
                groupVisibility - optional group visibility of the role
            """)
        createRoleType: CreateRoleType
    ): ResponseEntity<RoleResponseType>

    /**
     * Get a role by its identifier in the RBAC permission system.
     */
    @HttpGET(path = "{id}", description = "This method gets the details of a role specified by its ID.",
    responseDescription = """
        Role with attributes:
        id: The unique identifier of the role
        version: The version number of the role
        updateTimestamp: The date and time when the role was last updated
        roleName: The name of the role
        groupVisibility: An optional group visibility of the role
        permissions: The list of permissions associated with the role"""
    )
    fun getRole(
        @RestPathParameter(description = "ID of the role to be returned.")
        id: String
    ): RoleResponseType

    /**
     * Associates a role with a permission
     */
    @HttpPUT(path = "{roleId}/permission/{permissionId}",
        description = "This method adds the specified permission to the specified role.",
        responseDescription = """
            Role with attributes:
            id: The unique identifier of the role
            version: The version number of the role
            updateTimestamp: The date and time when the role was last updated
            roleName: The name of the role
            groupVisibility: An optional group visibility of the role
            permissions: The list of permissions associated with the role""")
    fun addPermission(
        @RestPathParameter(description = "Identifier for an existing role")
        roleId: String,
        @RestPathParameter(description = "Identifier for an existing permission")
        permissionId: String
    ): ResponseEntity<RoleResponseType>

    /**
     * Removes Association between a role and a permission
     */
    @HttpDELETE(path = "{roleId}/permission/{permissionId}",
        description = "This method removes the specified permission from the specified role.",
        responseDescription = """
            Role with attributes:
            id: The unique identifier of the role
            version: The version number of the role
            updateTimestamp: The date and time when the role was last updated
            roleName: The name of the role
            groupVisibility: An optional group visibility of the role
            permissions: The list of permissions associated with the role""")
    fun removePermission(
        @RestPathParameter(description = "Identifier for an existing role")
        roleId: String,
        @RestPathParameter(description = "Identifier for an existing permission")
        permissionId: String
    ): ResponseEntity<RoleResponseType>
}