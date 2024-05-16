package net.corda.cli.commands.initialRbac.commands

import net.corda.cli.commands.common.RestCommand
import picocli.CommandLine
import java.util.concurrent.Callable
import kotlin.reflect.KMutableProperty
import kotlin.reflect.full.declaredMemberProperties

@CommandLine.Command(
    name = "all-cluster-roles",
    description = [
        """Creates all of the cluster-scoped roles:
        - '$CORDA_DEV_ROLE'
        - '$USER_ADMIN_ROLE'
        - '$VNODE_CREATOR_ROLE'"""
    ],
    mixinStandardHelpOptions = true
)
class AllClusterRolesSubcommand : RestCommand(), Callable<Int> {

    override fun call(): Int {
        // If a subcommand fails with a return code of 5 (role already exists),
        // continue on to process the other roles. All other failures
        // (e.g. due to lack of connectivity) result in an exception being propagated.
        return setProperties(CordaDeveloperSubcommand()).call() +
            setProperties(UserAdminSubcommand()).call() +
            setProperties(VNodeCreatorSubcommand()).call()
    }

    private fun <T : RestCommand> setProperties(other: T): T {
        RestCommand::class.declaredMemberProperties.forEach { property ->
            if (property is KMutableProperty<*>) {
                property.setter.call(other, property.get(this))
            }
        }
        return other
    }
}
