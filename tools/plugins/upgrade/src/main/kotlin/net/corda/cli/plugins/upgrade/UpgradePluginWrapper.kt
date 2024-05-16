package net.corda.cli.plugins.upgrade

import net.corda.cli.api.AbstractCordaCliVersionProvider
import net.corda.cli.api.CordaCliPlugin
import org.pf4j.Extension
import org.pf4j.Plugin
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import picocli.CommandLine

class VersionProvider : AbstractCordaCliVersionProvider()

@Suppress("unused")
class UpgradePluginWrapper : Plugin() {
    private companion object {
        val logger: Logger = LoggerFactory.getLogger(UpgradePlugin::class.java)
    }

    override fun start() {
        logger.debug("Upgrade plugin started.")
    }

    override fun stop() {
        logger.debug("Upgrade plugin stopped.")
    }

    @Extension
    @CommandLine.Command(
        name = "upgrade",
        subcommands = [MigrateHostedIdentities::class],
        mixinStandardHelpOptions = true,
        description = ["Plugin for operations related to platform upgrade"],
        versionProvider = VersionProvider::class,
    )
    class UpgradePlugin : CordaCliPlugin
}
