package net.corda.cli.commands.initialRbac.commands

import net.corda.cli.commands.common.RestCommand
import net.corda.cli.commands.initialRbac.commands.RoleCreationUtils.checkOrCreateRole
import net.corda.sdk.bootstrap.rbac.Permissions.userAdmin
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

    override fun call(): Int {
        return checkOrCreateRole(USER_ADMIN_ROLE, userAdmin)
    }
}
