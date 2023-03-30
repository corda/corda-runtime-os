package net.corda.cli.plugins.preinstall

import net.corda.cli.api.CordaCliPlugin
import org.pf4j.Extension
import org.pf4j.Plugin
import org.pf4j.PluginWrapper
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import picocli.CommandLine

class PreInstallPlugin(wrapper: PluginWrapper) : Plugin(wrapper) {

    private companion object {
        private val logger: Logger = LoggerFactory.getLogger(this::class.java)
    }

    override fun start() {
        logger.info("starting preinstall plugin")
    }

    override fun stop() {
        logger.info("stopping preinstall plugin")
    }

    @Extension
    @CommandLine.Command(name = "preinstall",
        subcommands = [CheckLimits::class, CheckPostgres::class],
        description = ["Preinstall checks for corda."])
    class PreInstallPluginEntry : CordaCliPlugin

}