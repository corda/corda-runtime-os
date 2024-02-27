package net.corda.cli.plugins.vnode

import net.corda.cli.api.AbstractCordaCliVersionProvider
import net.corda.cli.api.CordaCliPlugin
import net.corda.cli.plugins.vnode.commands.PlatformMigration
import net.corda.cli.plugins.vnode.commands.ResetCommand
import org.pf4j.Extension
import org.pf4j.Plugin
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import picocli.CommandLine

class VersionProvider : AbstractCordaCliVersionProvider()

@Suppress("unused")
class VirtualNodeCliPlugin : Plugin() {

    private companion object {
        val logger: Logger = LoggerFactory.getLogger(this::class.java)
    }

    override fun start() {
        logger.debug("starting virtual node plugin")
    }

    override fun stop() {
        logger.debug("stopping virtual node plugin")
    }

    @Extension
    @CommandLine.Command(
        name = "vnode",
        subcommands = [ResetCommand::class, PlatformMigration::class],
        mixinStandardHelpOptions = true,
        description = ["Manages a virtual node"],
        versionProvider = VersionProvider::class
    )
    class PluginEntryPoint : CordaCliPlugin
}

/**
 * Plugins are loaded with a PF4J class loader, and as such seemingly are their declared dependencies.
 * Plugins are executed in a thread which has the standard AppClassLoader set as its context.
 * This means we cannot grab any runtime dependent classes from libs that were declared as dependencies in the project
 * because they cannot be found in the AppClassLoader only the PF4J class loader.
 * To work around this we temporarily set the context to the PF4J class loader we can extract from a class loaded by the
 * PF4J framework.
 * In this plugin this would affect classes like the logger or JDBC drivers.
 */
internal fun withPluginClassLoader(block: () -> Unit) {
    val originalThreadContextClassLoader = Thread.currentThread().contextClassLoader
    val pluginClassLoader = VirtualNodeCliPlugin::class.java.classLoader

    Thread.currentThread().contextClassLoader = pluginClassLoader
    try {
        block()
    } finally {
        Thread.currentThread().contextClassLoader = originalThreadContextClassLoader
    }
}
