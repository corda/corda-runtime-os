package net.corda.cli.plugin.initialRbac.commands

import net.corda.cli.plugin.initialRbac.commands.RoleCreationUtils.checkOrCreateRole
import net.corda.cli.plugins.common.HttpRpcCommand
import picocli.CommandLine
import java.util.concurrent.Callable

private const val CORDA_DEV_ROLE = "CordaDeveloperRole"

@CommandLine.Command(
    name = "corda-developer",
    description = ["""Creates a role ('$CORDA_DEV_ROLE') which will permit:
        - vNode reset"""]
)
class CordaDeveloperSubcommand : HttpRpcCommand(), Callable<Int> {

    private val permissionsToCreate: Map<String, String> = listOf(
        "Force CPI upload" to "POST:/api/v1/maintenance/virtualnode/forcecpiupload"
    ).toMap()

    override fun call(): Int {
        return checkOrCreateRole(CORDA_DEV_ROLE, permissionsToCreate)
    }
}
