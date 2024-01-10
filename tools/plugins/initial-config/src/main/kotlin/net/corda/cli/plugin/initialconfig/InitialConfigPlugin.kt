package net.corda.cli.plugin.initialconfig

import net.corda.cli.api.AbstractCordaCliVersionProvider
import net.corda.cli.api.CordaCliPlugin
import org.pf4j.Extension
import org.pf4j.Plugin
import picocli.CommandLine.Command

class VersionProvider : AbstractCordaCliVersionProvider()

class InitialConfigPlugin : Plugin() {
    override fun start() {
    }

    override fun stop() {
    }

    @Extension
    @Command(
        name = "initial-config",
        subcommands = [RbacConfigSubcommand::class, DbConfigSubcommand::class, CryptoConfigSubcommand::class],
        mixinStandardHelpOptions = true,
        description = ["Create SQL files to write the initial config to a new cluster"],
        versionProvider = VersionProvider::class
    )
    class PluginEntryPoint : CordaCliPlugin
}