package net.corda.cli.plugins.vnode

import net.corda.cli.api.CordaCliPlugin
import net.corda.cli.plugins.vnode.commands.PlatformMigration
import net.corda.cli.plugins.vnode.commands.ResetCommand
import org.pf4j.Extension
import org.pf4j.Plugin
import org.pf4j.PluginWrapper
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import picocli.CommandLine

@Suppress("unused")
class VirtualNodeCliPlugin(wrapper: PluginWrapper) : Plugin(wrapper) {

    companion object {
        val classLoader = this::class.java.classLoader
        private val logger: Logger = LoggerFactory.getLogger(this::class.java)
    }

    override fun start() {
        logger.info("starting virtual node plugin")
    }

    override fun stop() {
        logger.info("stopping virtual node plugin")
    }

    @Extension
    @CommandLine.Command(
        name = "vnode",
        subcommands = [ResetCommand::class, PlatformMigration::class],
        description = ["Manages a virtual node"]
    )
    class PluginEntryPoint : CordaCliPlugin
}
