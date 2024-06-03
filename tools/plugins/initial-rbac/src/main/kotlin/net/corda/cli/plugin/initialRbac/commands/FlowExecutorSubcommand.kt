package net.corda.cli.plugin.initialRbac.commands

import net.corda.cli.plugin.initialRbac.commands.RoleCreationUtils.checkOrCreateRole
import net.corda.cli.plugins.common.RestCommand
import net.corda.rbac.schema.RbacKeys.VNODE_SHORT_HASH_REGEX
import net.corda.sdk.bootstrap.rbac.Permissions.flowExecutor
import picocli.CommandLine
import java.util.concurrent.Callable

private const val FLOW_EXECUTOR_ROLE = "FlowExecutorRole"

@CommandLine.Command(
    name = "flow-executor",
    description = [
        """Creates a role ('$FLOW_EXECUTOR_ROLE') which will permit for a vNode supplied: 
        - Starting any flow
        - Enquire about the status of the running flow"""
    ],
    mixinStandardHelpOptions = true
)
@Suppress("unused")
class FlowExecutorSubcommand : RestCommand(), Callable<Int> {

    @CommandLine.Option(
        names = ["-v", "--v-node-id"],
        description = ["vNode short hash identifier"],
        required = true
    )
    lateinit var vnodeShortHash: String

    override fun call(): Int {
        super.call()
        if (!wildcardMatch(vnodeShortHash, VNODE_SHORT_HASH_REGEX)) {
            throw IllegalArgumentException(
                """Supplied vNode ID "$vnodeShortHash" is invalid,""" +
                    """ it must conform to the pattern "$VNODE_SHORT_HASH_REGEX"."""
            )
        }

        return checkOrCreateRole("$FLOW_EXECUTOR_ROLE-$vnodeShortHash", flowExecutor(vnodeShortHash))
    }

    private fun wildcardMatch(input: String, regex: String): Boolean {
        return input.matches(regex.toRegex(RegexOption.IGNORE_CASE))
    }
}
