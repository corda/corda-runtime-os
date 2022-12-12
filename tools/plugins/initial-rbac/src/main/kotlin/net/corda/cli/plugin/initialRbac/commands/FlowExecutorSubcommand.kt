package net.corda.cli.plugin.initialRbac.commands

import net.corda.cli.plugin.initialRbac.commands.RoleCreationUtils.checkOrCreateRole
import net.corda.cli.plugin.initialRbac.commands.RoleCreationUtils.wildcardMatch
import net.corda.cli.plugins.common.HttpRpcCommand
import net.corda.rbac.schema.RbacKeys.CLIENT_REQ_REGEX
import net.corda.rbac.schema.RbacKeys.FLOW_NAME_REGEX
import net.corda.rbac.schema.RbacKeys.PREFIX_SEPARATOR
import net.corda.rbac.schema.RbacKeys.START_FLOW_PREFIX
import net.corda.rbac.schema.RbacKeys.VNODE_SHORT_HASH_REGEX
import picocli.CommandLine
import java.util.concurrent.Callable

private const val FLOW_EXECUTOR_ROLE = "FlowExecutorRole"

@CommandLine.Command(
    name = "flow-executor",
    description = ["""Creates a role ('$FLOW_EXECUTOR_ROLE') which will permit for a vNode supplied: 
        - Starting any flow
        - Enquire about the status of the running flow"""]
)
@Suppress("unused")
class FlowExecutorSubcommand : HttpRpcCommand(), Callable<Int> {

    @CommandLine.Option(
        names = ["-v", "--vNodeId"],
        description = ["vNode short hash identifier"],
        required = true
    )
    lateinit var vnodeShortHash: String

    private val permissionsToCreate: Set<PermissionTemplate> get() = setOf(
        // Endpoint level commands
        PermissionTemplate("Start Flow endpoint", "POST:/api/v1/flow/$vnodeShortHash", null),
        PermissionTemplate("Get status for all flows", "GET:/api/v1/flow/$vnodeShortHash", null),
        PermissionTemplate("Get status for a specific flow", "GET:/api/v1/flow/$vnodeShortHash/$CLIENT_REQ_REGEX", null),
        PermissionTemplate("Get a list of startable flows", "GET:/api/v1/flowclass/$vnodeShortHash", null),
        PermissionTemplate(
            "Get status for a specific flow via WebSocket",
            "WS:/api/v1/flow/$vnodeShortHash/$CLIENT_REQ_REGEX",
            null
        ),

        // Flow start related
        PermissionTemplate("Start any flow",
            "$START_FLOW_PREFIX$PREFIX_SEPARATOR$FLOW_NAME_REGEX", vnodeShortHash)
    )

    override fun call(): Int {

        if (!wildcardMatch(vnodeShortHash, VNODE_SHORT_HASH_REGEX)) {
            throw IllegalArgumentException(
                """Supplied vNode ID "$vnodeShortHash" is invalid,""" +
                        """ it must conform to the pattern "$VNODE_SHORT_HASH_REGEX".""")
        }

        return checkOrCreateRole("$FLOW_EXECUTOR_ROLE-$vnodeShortHash", permissionsToCreate)
    }
}
