package net.corda.cli.plugins.dbconfig

import net.corda.cli.api.CordaCliPlugin
import org.pf4j.ExtensionPoint
import org.pf4j.Plugin
import org.pf4j.PluginWrapper
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import picocli.CommandLine

class DatabaseBootstrapAndUpgrade(wrapper: PluginWrapper) : Plugin(wrapper) {

    companion object {
        val classLoader = this::class.java.classLoader
        private val logger: Logger = LoggerFactory.getLogger(this::class.java)
    }

    override fun start() {
        logger.info("Bootstrap plugin started.")
    }

    override fun stop() {
        logger.info("Bootstrap plugin stopped.")
    }

    @CommandLine.Command(name = "database", subcommands = [Spec::class], description = ["Does Database bootstrapping and upgrade"])
    class PluginEntryPoint : CordaCliPlugin, ExtensionPoint
}
