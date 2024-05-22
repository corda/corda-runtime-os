package net.corda.cli.plugin.initialRbac.commands

import net.corda.cli.plugin.initialRbac.commands.RoleCreationUtils.checkOrCreateRole
import net.corda.cli.plugins.common.RestCommand
import net.corda.sdk.bootstrap.rbac.Permissions.vNodeCreator
import picocli.CommandLine
import java.util.concurrent.Callable

const val VNODE_CREATOR_ROLE = "VNodeCreatorRole"

@CommandLine.Command(
    name = "vnode-creator",
    description = [
        """Creates a role ('$VNODE_CREATOR_ROLE') which will permit: 
        - CPI upload
        - vNode creation
        - vNode update
        - vNode CPI upgrade
        - BYODB SQL
        - Uploading certificates
        - Member registration
        - Generate key pair
        - Check flow status"""
    ],
    mixinStandardHelpOptions = true
)
class VNodeCreatorSubcommand : RestCommand(), Callable<Int> {

    override fun call(): Int {
        return checkOrCreateRole(VNODE_CREATOR_ROLE, vNodeCreator)
    }
}
