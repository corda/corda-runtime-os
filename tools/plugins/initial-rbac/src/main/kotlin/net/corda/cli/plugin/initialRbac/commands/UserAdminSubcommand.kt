package net.corda.cli.plugin.initialRbac.commands

import net.corda.cli.plugins.common.HttpRpcClientUtils.createHttpRpcClient
import net.corda.cli.plugins.common.HttpRpcCommand
import net.corda.libs.permissions.endpoints.v1.permission.PermissionEndpoint
import net.corda.libs.permissions.endpoints.v1.permission.types.CreatePermissionType
import net.corda.libs.permissions.endpoints.v1.permission.types.PermissionType
import net.corda.libs.permissions.endpoints.v1.role.RoleEndpoint
import net.corda.libs.permissions.endpoints.v1.role.types.CreateRoleType
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import picocli.CommandLine
import java.util.concurrent.Callable

private const val USER_ADMIN_ROLE = "UserAdminRole"

@CommandLine.Command(
    name = "user-admin",
    description = ["""Creates a role ('$USER_ADMIN_ROLE') which will permit: 
        - creation/deletion of users
        - creation/deletion of permissions
        - creation/deletion of roles
        - assigning/un-assigning roles to users
        - assigning/un-assigning permissions to roles"""]
)
class UserAdminSubcommand : HttpRpcCommand(), Callable<Int> {

    private companion object {
        val logger: Logger = LoggerFactory.getLogger(this::class.java)
        val sysOut: Logger = LoggerFactory.getLogger("SystemOut")
        val errOut: Logger = LoggerFactory.getLogger("SystemErr")
    }

    private val permissionsToCreate: Map<String, String> = listOf(
        // User manipulation permissions
        "CreateUsers" to "POST:/api/v1/user",
        "GetUsers" to "GET:/api/v1/user.*",
        "AddRoleToUser" to "PUT:/api/v1/user/.*/role/.*",
        "DeleteRoleFromUser" to "DELETE:/api/v1/user/.*/role/.*",
        "GetPermissionsSummary" to "GET:/api/v1/user/.*/permissionSummary",

        // Permission manipulation permissions ;-)
        "CreatePermission" to "POST:/api/v1/permission",
        "GetPermission" to "GET:/api/v1/permission/.*",

        // Role manipulation permissions
        "GetRoles" to "GET:/api/v1/role",
        "CreateRole" to "POST:/api/v1/role",
        "GetRole" to "GET:/api/v1/role/.*",
        "AddPermissionToRole" to "PUT:/api/v1/role/.*/permission/.*",
        "DeletePermissionFromRole" to "DELETE:/api/v1/role/.*/permission/.*"
    ).toMap()

    override fun call(): Int {

        logger.info("Running UserAdminSubcommand")

        createHttpRpcClient(RoleEndpoint::class).use { roleEndpointClient ->
            val roleEndpoint = roleEndpointClient.start().proxy
            val allRoles = roleEndpoint.getRoles()
            if (allRoles.any { it.roleName == USER_ADMIN_ROLE }) {
                errOut.error("$USER_ADMIN_ROLE already exists - nothing to do.")
                return 5
            }

            val permissionIds = createHttpRpcClient(PermissionEndpoint::class).use { permissionEndpointClient ->
                val permissionEndpoint = permissionEndpointClient.start().proxy
                 permissionsToCreate.map { entry ->
                    permissionEndpoint.createPermission(
                        CreatePermissionType(
                            PermissionType.ALLOW,
                            entry.value,
                            null,
                            null
                        )
                    ).responseBody.id.also {
                        logger.info("Created permission: ${entry.key} with id: $it")
                    }
                }
            }

            val roleId = roleEndpoint.createRole(CreateRoleType(USER_ADMIN_ROLE, null)).responseBody.id
            permissionIds.forEach { permId ->
                roleEndpoint.addPermission(roleId, permId)
            }
            sysOut.info("Successfully created $USER_ADMIN_ROLE with id: $roleId and assigned permissions")
        }

        return 0
    }
}
