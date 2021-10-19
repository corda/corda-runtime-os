package net.corda.cli.application

import org.pf4j.CompoundPluginDescriptorFinder
import org.pf4j.DefaultPluginManager
import org.pf4j.ManifestPluginDescriptorFinder
import org.pf4j.PluginWrapper
import net.corda.cli.api.CordaCliCommand
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import picocli.CommandLine
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.system.exitProcess

fun main(vararg args: String) {
    Boot.runDemo(*args)
}

@CommandLine.Command(
    name = "corda"
)
class App

/**
 * A boot class that starts picocli and loads in plugin sub commands.
 */
object Boot {
    private val logger: Logger = LoggerFactory.getLogger(Boot::class.java)

    private class PluginManager(importPaths: List<Path>) : DefaultPluginManager(importPaths) {
        override fun createPluginDescriptorFinder(): CompoundPluginDescriptorFinder {
            return CompoundPluginDescriptorFinder()
                .add(ManifestPluginDescriptorFinder());
        }
    }

    fun runDemo(vararg args: String) {

        val pluginsDir = System.getProperty("pf4j.pluginsDir", "./plugins")
        logger.info("Plugin directory: $pluginsDir")
        // create the plugin manager
        val pluginManager = PluginManager(listOf(Paths.get(pluginsDir)))

        // load the plugins
        pluginManager.loadPlugins()

        // start (active/resolved) the plugins
        pluginManager.startPlugins()

        // retrieves the extensions for Greeting extension point
        val cordaCliCommands: List<CordaCliCommand> = pluginManager.getExtensions(CordaCliCommand::class.java)
        logger.info(
            String.format(
                "Found %d extensions for extension point '%s'",
                cordaCliCommands.size,
                CordaCliCommand::class.java.name
            )
        )
        val commandLine = CommandLine(App())
        cordaCliCommands.forEach { cordaCommand ->
            logger.info("Adding subcommands from >>> ${cordaCommand.pluginID}")
            commandLine.addSubcommand(cordaCommand)
        }

        // print extensions for each started plugin
        val startedPlugins: List<PluginWrapper> = pluginManager.startedPlugins
        startedPlugins.forEach { plugin ->
            val pluginId: String = plugin.descriptor.pluginId
            logger.info(String.format("Extensions added by plugin '%s':", pluginId))
             val extensionClassNames = pluginManager.getExtensionClassNames(pluginId);
                 extensionClassNames.forEach { extension ->
                 logger.info("   $extension");
             }
        }

        val commandResult = commandLine
            .setCaseInsensitiveEnumValuesAllowed(true)
            .execute(*args)
        pluginManager.stopPlugins()
        exitProcess(commandResult)
    }
}
