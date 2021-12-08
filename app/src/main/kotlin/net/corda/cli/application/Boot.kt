package net.corda.cli.application

import org.pf4j.CompoundPluginDescriptorFinder
import org.pf4j.DefaultPluginManager
import org.pf4j.ManifestPluginDescriptorFinder
import org.pf4j.PluginWrapper
import net.corda.cli.api.CordaCliPlugin
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
        logger.debug("Plugin directory: $pluginsDir")
        // create the plugin manager
        val pluginManager = PluginManager(listOf(Paths.get(pluginsDir)))

        // load the plugins
        pluginManager.loadPlugins()

        // start (active/resolved) the plugins
        pluginManager.startPlugins()

        // retrieves the extensions for Greeting extension point
        val cordaCliPlugins: List<CordaCliPlugin> = pluginManager.getExtensions(CordaCliPlugin::class.java)
        logger.debug(
            String.format(
                "Found %d extensions for extension point '%s'",
                cordaCliPlugins.size,
                CordaCliPlugin::class.java.name
            )
        )
        val commandLine = CommandLine(App())
        cordaCliPlugins.forEach { cordaCliPlugin ->
            logger.debug("Adding subcommands from >>> ${cordaCliPlugin.pluginId}")
            commandLine.addSubcommand(cordaCliPlugin)
        }

        // print extensions for each started plugin
        val startedPlugins: List<PluginWrapper> = pluginManager.startedPlugins
        startedPlugins.forEach { plugin ->
            val pluginId: String = plugin.descriptor.pluginId
            logger.debug(String.format("Extensions added by plugin '%s':", pluginId))
             val extensionClassNames = pluginManager.getExtensionClassNames(pluginId);
                 extensionClassNames.forEach { extension ->
                 logger.debug("   $extension");
             }
        }

        val commandResult = commandLine
            .setCaseInsensitiveEnumValuesAllowed(true)
            .execute(*args)
        pluginManager.stopPlugins()
        exitProcess(commandResult)
    }
}
