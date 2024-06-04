package net.corda.sdk.bootstrap.rbac

import net.corda.libs.permissions.endpoints.v1.permission.types.BulkCreatePermissionsRequestType
import net.corda.libs.permissions.endpoints.v1.permission.types.CreatePermissionType
import net.corda.libs.permissions.endpoints.v1.permission.types.PermissionType
import net.corda.libs.permissions.endpoints.v1.role.types.CreateRoleType
import net.corda.libs.permissions.endpoints.v1.role.types.RoleResponseType
import net.corda.restclient.CordaRestClient
import net.corda.sdk.rest.RestClientUtils.executeWithRetry
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class RoleAndPermissionsCreator(val restClient: CordaRestClient) {

    private companion object {
        val logger: Logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    /**
     * Creates a Role and grants Permissions
     * If role exists, do nothing
     * @param roleToCreate of type CreateRoleType, set role name and groupVisibility
     * @param permissionsToCreate set of type PermissionTemplate
     * @param wait Duration before timing out, default 10 seconds
     * @return of type RoleResponseType
     */
    fun createRoleAndPermissions(
        roleToCreate: CreateRoleType,
        permissionsToCreate: Set<PermissionTemplate>,
        wait: Duration = 10.seconds
    ): RoleResponseType {
        val allRoles = executeWithRetry(
            waitDuration = wait,
            operationName = "List all roles"
        ) {
            restClient.rbacRoleClient.getRole()
        }

        val existingRole = allRoles.singleOrNull { it.roleName == roleToCreate.roleName }
        if (existingRole != null) {
            logger.info("${roleToCreate.roleName} already exists - nothing to do.")
            return existingRole
        }

        val createRoleResponse = restClient.rbacRoleClient.postRole(roleToCreate)
        executeWithRetry(
            waitDuration = wait,
            operationName = "Wait until role '${createRoleResponse.id}' is created"
        ) {
            restClient.rbacRoleClient.getRoleId(createRoleResponse.id)
        }

        val bulkRequest = BulkCreatePermissionsRequestType(
            permissionsToCreate.map { entry ->
                CreatePermissionType(
                    PermissionType.ALLOW,
                    entry.permissionString,
                    roleToCreate.groupVisibility,
                    entry.vnodeShortHash
                )
            }.toSet(),
            setOf(createRoleResponse.id)
        )
        val createdPermissions = restClient.rbacPermissionClient.postPermissionBulk(bulkRequest).permissionIds
        executeWithRetry(
            waitDuration = wait,
            operationName = "Wait until permissions are created"
        ) {
            val getRoleResponse = restClient.rbacRoleClient.getRoleId(createRoleResponse.id)
            val rolePermissions = getRoleResponse.permissions.map { it.id }
            if (!rolePermissions.containsAll(createdPermissions)) {
                throw IllegalStateException("Not all permissions created, expected $createdPermissions, got $rolePermissions")
            }
        }

        return createRoleResponse
    }
}
