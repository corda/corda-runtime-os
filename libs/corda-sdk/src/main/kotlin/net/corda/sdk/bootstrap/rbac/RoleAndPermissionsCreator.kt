package net.corda.sdk.bootstrap.rbac

import net.corda.libs.permissions.endpoints.v1.permission.PermissionEndpoint
import net.corda.libs.permissions.endpoints.v1.permission.types.BulkCreatePermissionsRequestType
import net.corda.libs.permissions.endpoints.v1.permission.types.CreatePermissionType
import net.corda.libs.permissions.endpoints.v1.permission.types.PermissionType
import net.corda.libs.permissions.endpoints.v1.role.RoleEndpoint
import net.corda.libs.permissions.endpoints.v1.role.types.CreateRoleType
import net.corda.libs.permissions.endpoints.v1.role.types.RoleResponseType
import net.corda.rest.client.RestClient
import net.corda.sdk.rest.RestClientUtils.executeWithRetry
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class RoleAndPermissionsCreator {

    private companion object {
        val logger: Logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    /**
     * Creates a Role and grants Permissions
     * If role exists, do nothing
     * @param roleRestClient of type RoleEndpoint
     * @param permissionsToCreate of type PermissionEndpoint
     * @param roleToCreate of type CreateRoleType, set role name and groupVisibility
     * @param permissionsToCreate set of type PermissionTemplate
     * @param wait Duration before timing out, default 10 seconds
     * @return of type RoleResponseType
     */
    fun createRoleAndPermissions(
        roleRestClient: RestClient<RoleEndpoint>,
        permissionRestClient: RestClient<PermissionEndpoint>,
        roleToCreate: CreateRoleType,
        permissionsToCreate: Set<PermissionTemplate>,
        wait: Duration = 10.seconds
    ): RoleResponseType {
        val allRoles = roleRestClient.use { client ->
            executeWithRetry(
                waitDuration = wait,
                operationName = "List all roles"
            ) {
                val resource = client.start().proxy
                resource.getRoles()
            }
        }

        val exitingRole = allRoles.singleOrNull { it.roleName == roleToCreate.roleName }
        if (exitingRole != null) {
            logger.info("${roleToCreate.roleName} already exists - nothing to do.")
            return exitingRole
        }

        val createRoleResponse = roleRestClient.use { client ->
            executeWithRetry(
                waitDuration = wait,
                operationName = "Creating role: ${roleToCreate.roleName}"
            ) {
                val resource = client.start().proxy
                resource.createRole(roleToCreate).responseBody
            }
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

        permissionRestClient.use { client ->
            executeWithRetry(
                waitDuration = wait,
                operationName = "Creating and assigning permissions to the role"
            ) {
                val resource = client.start().proxy
                resource.createAndAssignPermissions(bulkRequest)
            }
        }

        return createRoleResponse
    }
}
