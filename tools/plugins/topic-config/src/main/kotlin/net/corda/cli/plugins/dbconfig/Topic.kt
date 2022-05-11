package net.corda.cli.plugins.topicconfig

import net.corda.cli.api.CordaCliPlugin
import org.pf4j.Extension
import org.pf4j.Plugin
import org.pf4j.PluginWrapper
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import picocli.CommandLine

class Topic(wrapper: PluginWrapper) : Plugin(wrapper) {

    companion object {
        private val logger: Logger = LoggerFactory.getLogger(this::class.java)
    }

    override fun start() {
        Thread.currentThread().contextClassLoader = this::class.java.classLoader
        logger.info("Topic plugin started.")
    }

    override fun stop() {
        logger.info("Topic plugin stopped.")
    }

    @Extension
    @CommandLine.Command(name = "topic", subcommands = [Create::class], description = ["Does Topic Bootstrapping"])
    class PluginEntryPoint : CordaCliPlugin
}
