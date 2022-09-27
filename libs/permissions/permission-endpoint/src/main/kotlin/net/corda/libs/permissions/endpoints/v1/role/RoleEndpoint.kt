package net.corda.libs.permissions.endpoints.v1.role

import net.corda.httprpc.RpcOps
import net.corda.httprpc.annotations.HttpRpcDELETE
import net.corda.httprpc.annotations.HttpRpcGET
import net.corda.httprpc.annotations.HttpRpcPOST
import net.corda.httprpc.annotations.HttpRpcPUT
import net.corda.httprpc.annotations.HttpRpcPathParameter
import net.corda.httprpc.annotations.HttpRpcRequestBodyParameter
import net.corda.httprpc.annotations.HttpRpcResource
import net.corda.httprpc.response.ResponseEntity
import net.corda.libs.permissions.endpoints.v1.role.types.CreateRoleType
import net.corda.libs.permissions.endpoints.v1.role.types.RoleResponseType

/**
 * Role endpoint exposes HTTP operations for management of Roles in the RBAC permission system.
 */
@HttpRpcResource(
    name = "RBAC Role API",
    description = "The RBAC Role API consists of a number of endpoints enabling role management in the RBAC " +
            "(role-based access control) permission system. You can get all roles in the system, " +
            "create new roles and add and delete permissions from roles.",
    path = "role"
)
interface RoleEndpoint : RpcOps {

    /**
     * Get all the roles available in RBAC permission system.
     */
    @HttpRpcGET(description = "This method returns an array with information about all roles in the permission system.",
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
    @HttpRpcPOST(description = "The method creates a new role in the RBAC permission system.",
    responseDescription = """
        Newly created role with attributes:
        id: The unique identifier of the role
        version: The version number of the role
        updateTimestamp: The date and time when the role was last updated
        roleName: The name of the role
        groupVisibility: An optional group visibility of the role
        permissions: The list of permissions associated with the role""")
    fun createRole(
        @HttpRpcRequestBodyParameter(
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
    @HttpRpcGET(path = "{id}", description = "This method gets the details of a role specified by its ID.",
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
        @HttpRpcPathParameter(description = "ID of the role to be returned.")
        id: String
    ): RoleResponseType

    /**
     * Associates a role with a permission
     */
    @HttpRpcPUT(path = "{roleId}/permission/{permissionId}",
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
        @HttpRpcPathParameter(description = "Identifier for an existing role")
        roleId: String,
        @HttpRpcPathParameter(description = "Identifier for an existing permission")
        permissionId: String
    ): ResponseEntity<RoleResponseType>

    /**
     * Removes Association between a role and a permission
     */
    @HttpRpcDELETE(path = "{roleId}/permission/{permissionId}",
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
        @HttpRpcPathParameter(description = "Identifier for an existing role")
        roleId: String,
        @HttpRpcPathParameter(description = "Identifier for an existing permission")
        permissionId: String
    ): ResponseEntity<RoleResponseType>
}