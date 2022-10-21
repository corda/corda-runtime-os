package net.corda.cli.plugin.initialRbac.commands

import net.corda.cli.plugin.initialRbac.commands.RoleCreationUtils.UUID_REGEX
import net.corda.cli.plugin.initialRbac.commands.RoleCreationUtils.VNODE_SHORT_HASH_REGEX
import net.corda.cli.plugin.initialRbac.commands.RoleCreationUtils.checkOrCreateRole
import net.corda.cli.plugins.common.HttpRpcCommand
import picocli.CommandLine
import java.util.concurrent.Callable

private const val VNODE_CREATOR_ROLE = "VNodeCreatorRole"

@CommandLine.Command(
    name = "vnode-creator",
    description = ["""Creates a role ('$VNODE_CREATOR_ROLE') which will permit: 
        - CPI upload
        - vNode creation
        - vNode update"""]
)
class VNodeCreatorSubcommand : HttpRpcCommand(), Callable<Int> {

    private val permissionsToCreate: Map<String, String> = listOf(
        // CPI related
        "Get all CPIs" to "GET:/api/v1/cpi",
        "CPI upload" to "POST:/api/v1/cpi",
        "CPI upload status" to "GET:/api/v1/cpi/status/$UUID_REGEX",

        // vNode related
        "Create vNode" to "POST:/api/v1/virtualnode",
        "Get all vNodes" to "GET:/api/v1/virtualnode",
        "Update vNode" to "PUT:/api/v1/virtualnode/$VNODE_SHORT_HASH_REGEX" // TBC
    ).toMap()

    override fun call(): Int {
        return checkOrCreateRole(VNODE_CREATOR_ROLE, permissionsToCreate)
    }
}
