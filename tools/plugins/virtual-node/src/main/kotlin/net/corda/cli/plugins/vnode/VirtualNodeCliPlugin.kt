package net.corda.cli.plugins.vnode

import net.corda.cli.api.CordaCliPlugin
import net.corda.cli.plugins.vnode.commands.ResetCommand
import org.pf4j.Extension
import org.pf4j.Plugin
import org.pf4j.PluginWrapper
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import picocli.CommandLine

class VirtualNodeCliPlugin(wrapper: PluginWrapper) : Plugin(wrapper) {

    companion object {
        val classLoader = this::class.java.classLoader
        private val logger: Logger = LoggerFactory.getLogger(this::class.java)
    }

    override fun start() {
        logger.info("Virtual Node plugin stopped.")
    }

    override fun stop() {
        logger.info("Virtual Node plugin stopped.")
    }

    @Extension
    @CommandLine.Command(name = "vnode", subcommands = [ResetCommand::class], description = ["manages a virtual node"])
    class PluginEntryPoint : CordaCliPlugin
}