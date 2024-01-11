package net.corda.cli.plugin.initialRbac.commands

import net.corda.cli.plugin.initialRbac.commands.RestApiVersionUtils.VERSION_PATH_REGEX
import net.corda.cli.plugin.initialRbac.commands.RoleCreationUtils.checkOrCreateRole
import net.corda.cli.plugins.common.RestCommand
import net.corda.rbac.schema.RbacKeys.USER_URL_REGEX
import net.corda.rbac.schema.RbacKeys.UUID_REGEX
import picocli.CommandLine
import java.util.concurrent.Callable

const val USER_ADMIN_ROLE = "UserAdminRole"

@CommandLine.Command(
    name = "user-admin",
    description = [
        """Creates a role ('$USER_ADMIN_ROLE') which will permit: 
        - creation/deletion of users
        - creation/deletion of permissions
        - creation/deletion of roles
        - changing password of users
        - assigning/un-assigning roles to users
        - assigning/un-assigning permissions to roles"""
    ],
    mixinStandardHelpOptions = true
)
class UserAdminSubcommand : RestCommand(), Callable<Int> {

    private val permissionsToCreate: Map<String, String> = listOf(
        // User manipulation permissions
        "CreateUsers" to "POST:/api/$VERSION_PATH_REGEX/user",
        "GetUsersV1" to "GET:/api/v1/user\\?loginName=$USER_URL_REGEX",
        "GetUsers" to "GET:/api/$VERSION_PATH_REGEX/user/$USER_URL_REGEX",
        "ChangeOtherUserPassword" to "POST:/api/$VERSION_PATH_REGEX/user/otheruserpassword",
        "AddRoleToUser" to "PUT:/api/$VERSION_PATH_REGEX/user/$USER_URL_REGEX/role/$UUID_REGEX",
        "DeleteRoleFromUser" to "DELETE:/api/$VERSION_PATH_REGEX/user/$USER_URL_REGEX/role/$UUID_REGEX",
        "GetPermissionsSummary" to "GET:/api/$VERSION_PATH_REGEX/user/$USER_URL_REGEX/permissionSummary",

        // Permission manipulation permissions ;-)
        "CreatePermission" to "POST:/api/$VERSION_PATH_REGEX/permission",
        "BulkCreatePermissions" to "POST:/api/$VERSION_PATH_REGEX/permission/bulk",
        "QueryPermissions" to "GET:/api/$VERSION_PATH_REGEX/permission\\?.*",
        "GetPermission" to "GET:/api/$VERSION_PATH_REGEX/permission/$UUID_REGEX",

        // Role manipulation permissions
        "GetRoles" to "GET:/api/$VERSION_PATH_REGEX/role",
        "CreateRole" to "POST:/api/$VERSION_PATH_REGEX/role",
        "GetRole" to "GET:/api/$VERSION_PATH_REGEX/role/$UUID_REGEX",
        "AddPermissionToRole" to "PUT:/api/$VERSION_PATH_REGEX/role/$UUID_REGEX/permission/$UUID_REGEX",
        "DeletePermissionFromRole" to "DELETE:/api/$VERSION_PATH_REGEX/role/$UUID_REGEX/permission/$UUID_REGEX"
    ).toMap()

    override fun call(): Int {
        return checkOrCreateRole(USER_ADMIN_ROLE, permissionsToCreate)
    }
}
