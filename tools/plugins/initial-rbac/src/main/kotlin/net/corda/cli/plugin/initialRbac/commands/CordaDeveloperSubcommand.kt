package net.corda.cli.plugin.initialRbac.commands

import net.corda.cli.plugin.initialRbac.commands.RestApiVersionUtils.VERSION_PATH_REGEX
import net.corda.cli.plugin.initialRbac.commands.RoleCreationUtils.checkOrCreateRole
import net.corda.cli.plugins.common.RestCommand
import net.corda.rbac.schema.RbacKeys.VNODE_SHORT_HASH_REGEX
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

    private val permissionsToCreate: Map<String, String> = listOf(
        "Force CPI upload" to "POST:/api/$VERSION_PATH_REGEX/maintenance/virtualnode/forcecpiupload",
        "Resync the virtual node vault" to
            "POST:/api/$VERSION_PATH_REGEX/maintenance/virtualnode/$VNODE_SHORT_HASH_REGEX/vault-schema/force-resync",
    ).toMap()

    override fun call(): Int {
        return checkOrCreateRole(CORDA_DEV_ROLE, permissionsToCreate)
    }
}
