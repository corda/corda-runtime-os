package net.corda.cli.plugin.initialconfig

import net.corda.cli.api.CordaCliPlugin
import org.pf4j.Extension
import org.pf4j.Plugin
import org.pf4j.PluginWrapper
import picocli.CommandLine.Command

class InitialConfigPlugin(wrapper: PluginWrapper) : Plugin(wrapper) {
    override fun start() {
    }

    override fun stop() {
    }

    @Extension
    @Command(
        name = "initial-config",
        subcommands = [RbacConfigSubcommand::class, DbConfigSubcommand::class],
        description = ["Create SQL files to write the initial config to a new cluster"]
    )
    class PluginEntryPoint : CordaCliPlugin
}