package net.corda.cli.plugin.initialRbac

import net.corda.cli.api.CordaCliPlugin
import net.corda.cli.plugin.initialRbac.commands.UserAdminSubcommand
import org.pf4j.Extension
import org.pf4j.Plugin
import org.pf4j.PluginWrapper
import picocli.CommandLine

@Suppress("unused")
class InitialRbacPlugin(wrapper: PluginWrapper) : Plugin(wrapper) {

    override fun start() {
    }

    override fun stop() {
    }

    @Extension
    @CommandLine.Command(
        name = "initial-rbac",
        subcommands = [UserAdminSubcommand::class],
        description = ["Creates common cluster-wide RBAC roles"]
    )
    class PluginEntryPoint : CordaCliPlugin
}