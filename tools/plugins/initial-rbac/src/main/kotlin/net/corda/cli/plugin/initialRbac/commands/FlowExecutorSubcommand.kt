package net.corda.cli.plugin.initialRbac.commands

import net.corda.cli.plugin.initialRbac.commands.RoleCreationUtils.checkOrCreateRole
import net.corda.cli.plugins.common.HttpRpcCommand
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

    private companion object {
        const val CLIENT_REQ_REGEX = "[A-Za-z0-9_-.]{1,250}"
        const val FLOW_NAME_REGEX = "[.\$a-zA-Z0-9]{1,250}"
    }

    @CommandLine.Option(
        names = ["-v", "--vNodeId"],
        description = ["vNode short hash identifier"],
        required = true
    )
    lateinit var vnodeShortHash: String

    private val permissionsToCreate: Set<PermissionTemplate> = setOf(
        // Endpoint level commands
        PermissionTemplate("Start Flow endpoint", "POST:/api/v1/flow/$vnodeShortHash", null),
        PermissionTemplate("Get status for all flows", "GET:/api/v1/flow/$vnodeShortHash", null),
        PermissionTemplate("Get status for a specific flow", "GET:/api/v1/flow/$vnodeShortHash/$CLIENT_REQ_REGEX", null),
        PermissionTemplate(
            "Get status for a specific flow via WebSocket",
            "WS:/api/v1/flow/$vnodeShortHash/$CLIENT_REQ_REGEX",
            null
        ),

        // Flow start related
        PermissionTemplate("Start any flow", "StartFlow:$FLOW_NAME_REGEX", vnodeShortHash)
    )

    override fun call(): Int {
        return checkOrCreateRole(FLOW_EXECUTOR_ROLE, permissionsToCreate)
    }
}
