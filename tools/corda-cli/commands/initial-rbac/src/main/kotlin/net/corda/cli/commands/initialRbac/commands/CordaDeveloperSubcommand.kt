package net.corda.cli.commands.initialRbac.commands

import net.corda.cli.commands.initialRbac.commands.RoleCreationUtils.checkOrCreateRole
import net.corda.cli.commands.common.RestCommand
import net.corda.sdk.bootstrap.rbac.Permissions.cordaDeveloper
import picocli.CommandLine
import java.util.concurrent.Callable

const val CORDA_DEV_ROLE = "CordaDeveloperRole"

@CommandLine.Command(
    name = "corda-developer",
    description = [
        """Creates a role ('$CORDA_DEV_ROLE') which will permit:
        - vNode reset
        - vNode vault sync
        - Change state of the vNode"""
    ],
    mixinStandardHelpOptions = true
)
class CordaDeveloperSubcommand : RestCommand(), Callable<Int> {

    override fun call(): Int {
        return checkOrCreateRole(CORDA_DEV_ROLE, cordaDeveloper)
    }
}
