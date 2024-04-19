package net.corda.cli.plugins.preinstall

import net.corda.cli.api.AbstractCordaCliVersionProvider
import net.corda.cli.api.CordaCliPlugin
import org.pf4j.Extension
import org.pf4j.Plugin
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import picocli.CommandLine

class VersionProvider : AbstractCordaCliVersionProvider()

class PreInstallPlugin : Plugin() {

    private companion object {
        private val logger: Logger = LoggerFactory.getLogger(PreInstallPlugin::class.java)
    }

    override fun start() {
        logger.debug("starting preinstall plugin")
    }

    override fun stop() {
        logger.debug("stopping preinstall plugin")
    }

    @Extension
    @CommandLine.Command(name = "preinstall",
        subcommands = [CheckLimits::class, CheckPostgres::class, CheckKafka::class, RunAll::class],
        mixinStandardHelpOptions = true,
        description = ["Preinstall checks for Corda."],
        versionProvider = VersionProvider::class)
    class PreInstallPluginEntry : CordaCliPlugin
}
