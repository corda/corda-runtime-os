package net.corda.cli.plugin.initialRbac

import net.corda.cli.api.AbstractCordaCliVersionProvider
import net.corda.cli.api.CordaCliPlugin
import net.corda.cli.plugin.initialRbac.commands.AllClusterRolesSubcommand
import net.corda.cli.plugin.initialRbac.commands.CordaDeveloperSubcommand
import net.corda.cli.plugin.initialRbac.commands.FlowExecutorSubcommand
import net.corda.cli.plugin.initialRbac.commands.UserAdminSubcommand
import net.corda.cli.plugin.initialRbac.commands.VNodeCreatorSubcommand
import org.pf4j.Extension
import org.pf4j.Plugin
import picocli.CommandLine

class VersionProvider : AbstractCordaCliVersionProvider()

@Suppress("unused")
class InitialRbacPlugin : Plugin() {

    override fun start() {
    }

    override fun stop() {
    }

    @Extension
    @CommandLine.Command(
        name = "initial-rbac",
        subcommands = [
            UserAdminSubcommand::class, VNodeCreatorSubcommand::class,
            CordaDeveloperSubcommand::class, FlowExecutorSubcommand::class,
            AllClusterRolesSubcommand::class
        ],
        mixinStandardHelpOptions = true,
        description = ["Creates common RBAC roles"],
        versionProvider = VersionProvider::class
    )
    class PluginEntryPoint : CordaCliPlugin
}
