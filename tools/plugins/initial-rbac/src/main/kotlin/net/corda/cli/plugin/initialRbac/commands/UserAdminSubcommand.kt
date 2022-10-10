package net.corda.cli.plugin.initialRbac.commands

import net.corda.cli.plugin.initialRbac.commands.RoleCreationUtils.checkOrCreateRole
import net.corda.cli.plugins.common.HttpRpcCommand
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

    private val permissionsToCreate: Map<String, String> = listOf(
        // User manipulation permissions
        "CreateUsers" to "POST:/api/v1/user",
        "GetUsers" to "GET:/api/v1/user.*",
        "AddRoleToUser" to "PUT:/api/v1/user/.*/role/.*",
        "DeleteRoleFromUser" to "DELETE:/api/v1/user/.*/role/.*",
        "GetPermissionsSummary" to "GET:/api/v1/user/.*/permissionSummary",

        // Permission manipulation permissions ;-)
        "CreatePermission" to "POST:/api/v1/permission",
        "QueryPermissions" to "GET:/api/v1/permission",
        "GetPermission" to "GET:/api/v1/permission/.*",

        // Role manipulation permissions
        "GetRoles" to "GET:/api/v1/role",
        "CreateRole" to "POST:/api/v1/role",
        "GetRole" to "GET:/api/v1/role/.*",
        "AddPermissionToRole" to "PUT:/api/v1/role/.*/permission/.*",
        "DeletePermissionFromRole" to "DELETE:/api/v1/role/.*/permission/.*"
    ).toMap()

    override fun call(): Int {
        return checkOrCreateRole(USER_ADMIN_ROLE, permissionsToCreate)
    }
}
