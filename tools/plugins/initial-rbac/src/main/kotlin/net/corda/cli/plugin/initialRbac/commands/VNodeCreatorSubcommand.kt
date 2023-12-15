package net.corda.cli.plugin.initialRbac.commands

import net.corda.cli.plugin.initialRbac.commands.RestApiVersionUtils.VERSION_PATH_REGEX
import net.corda.cli.plugin.initialRbac.commands.RoleCreationUtils.checkOrCreateRole
import net.corda.cli.plugins.common.RestCommand
import net.corda.rbac.schema.RbacKeys.UUID_REGEX
import net.corda.rbac.schema.RbacKeys.VNODE_SHORT_HASH_REGEX
import net.corda.rbac.schema.RbacKeys.VNODE_STATE_REGEX
import picocli.CommandLine
import java.util.concurrent.Callable

const val VNODE_CREATOR_ROLE = "VNodeCreatorRole"

@CommandLine.Command(
    name = "vnode-creator",
    description = [
        """Creates a role ('$VNODE_CREATOR_ROLE') which will permit: 
        - CPI upload
        - vNode creation
        - vNode update"""
    ],
    mixinStandardHelpOptions = true
)
class VNodeCreatorSubcommand : RestCommand(), Callable<Int> {

    private val permissionsToCreate: Map<String, String> = listOf(
        // CPI related
        "Get all CPIs" to "GET:/api/$VERSION_PATH_REGEX/cpi",
        "CPI upload" to "POST:/api/$VERSION_PATH_REGEX/cpi",
        "CPI upload status" to "GET:/api/$VERSION_PATH_REGEX/cpi/status/$UUID_REGEX",

        // vNode related
        "Create vNode" to "POST:/api/$VERSION_PATH_REGEX/virtualnode",
        "Get all vNodes" to "GET:/api/$VERSION_PATH_REGEX/virtualnode",
        "Get a vNode" to "GET:/api/$VERSION_PATH_REGEX/virtualnode/$VNODE_SHORT_HASH_REGEX",
        "Update vNode" to "PUT:/api/$VERSION_PATH_REGEX/virtualnode/$VNODE_SHORT_HASH_REGEX", // TBC
        "Update virtual node state" to "PUT:/api/$VERSION_PATH_REGEX/virtualnode/$VNODE_SHORT_HASH_REGEX/state/${VNODE_STATE_REGEX}"
    ).toMap()

    override fun call(): Int {
        return checkOrCreateRole(VNODE_CREATOR_ROLE, permissionsToCreate)
    }
}
