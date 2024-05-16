package net.corda.cli.commands.vnode

import net.corda.cli.commands.vnode.commands.PlatformMigration
import net.corda.cli.commands.vnode.commands.ResetCommand
import picocli.CommandLine

@Suppress("unused")
@CommandLine.Command(
    name = "vnode",
    subcommands = [ResetCommand::class, PlatformMigration::class],
    mixinStandardHelpOptions = true,
    description = ["Manages a virtual node"],
)
class VirtualNodeComand

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
    val pluginClassLoader = VirtualNodeComand::class.java.classLoader

    Thread.currentThread().contextClassLoader = pluginClassLoader
    try {
        block()
    } finally {
        Thread.currentThread().contextClassLoader = originalThreadContextClassLoader
    }
}
