package net.corda.cli.application

import net.corda.cli.api.CordaCliPlugin
import net.corda.cli.api.serviceUsers.HttpServiceUser
import net.corda.cli.application.commands.SetCurrentNodeCommand
import net.corda.cli.application.services.Files
import net.corda.cli.application.services.HttpRpcService
import org.pf4j.CompoundPluginDescriptorFinder
import org.pf4j.DefaultPluginManager
import org.pf4j.ManifestPluginDescriptorFinder
import picocli.CommandLine
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.system.exitProcess

fun main(vararg args: String) {
    Boot.run(*args)
}

@CommandLine.Command(
    name = "corda",
    subcommands = [SetCurrentNodeCommand::class]
)
class App

/**
 * A boot class that starts picocli and loads in plugin sub commands.
 */
object Boot {
    private class PluginManager(importPaths: List<Path>) : DefaultPluginManager(importPaths) {
        override fun createPluginDescriptorFinder(): CompoundPluginDescriptorFinder {
            return CompoundPluginDescriptorFinder()
                .add(ManifestPluginDescriptorFinder())
        }
    }

    fun run(vararg args: String) {

        //create storage dir if it doesn't exist
        Files.cliHomeDir().mkdirs()
        Files.profile.createNewFile()

        //create http service
        val httpService = HttpRpcService()

        // Find and load the CLI plugins
        val pluginsDir = System.getProperty("pf4j.pluginsDir", "./plugins")
        val pluginManager = PluginManager(listOf(Paths.get(pluginsDir)))
        pluginManager.loadPlugins()
        pluginManager.startPlugins()

        // Retrieves the extensions for CordaCliPlugin extension point
        val cordaCliPlugins: List<CordaCliPlugin> = pluginManager.getExtensions(CordaCliPlugin::class.java)

        // Extract httpServiceUsers for service injection
        val httpServiceUsers =
            cordaCliPlugins.filter { plugin -> plugin is HttpServiceUser }.map { plugin -> plugin as HttpServiceUser }
        httpServiceUsers.forEach { serviceUser -> serviceUser.service = httpService }

        // Create the Command line app and add in the subcommands from the plugins.
        val commandLine = CommandLine(App())
        cordaCliPlugins.forEach { cordaCliPlugin ->
            commandLine.addSubcommand(cordaCliPlugin)
        }

        val commandResult = commandLine
            .setCaseInsensitiveEnumValuesAllowed(true)
            .execute(*args)
        pluginManager.stopPlugins()
        exitProcess(commandResult)
    }
}
