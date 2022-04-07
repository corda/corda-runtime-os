package net.corda.cli.plugins.dbconfig

import net.corda.cli.api.CordaCliPlugin
import org.pf4j.Extension
import org.pf4j.Plugin
import org.pf4j.PluginWrapper
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import picocli.CommandLine

class Database(wrapper: PluginWrapper) : Plugin(wrapper) {

    companion object {
        private val logger: Logger = LoggerFactory.getLogger(this::class.java)
    }

    override fun start() {
        Thread.currentThread().contextClassLoader = this::class.java.classLoader
        logger.info("Bootstrap plugin started.")
    }

    override fun stop() {
        logger.info("Bootstrap plugin stopped.")
    }

    @Extension
    @CommandLine.Command(name = "database", subcommands = [Spec::class], description = ["Does Database Bootstrapping"])
    class PluginEntryPoint : CordaCliPlugin
}
