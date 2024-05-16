package net.corda.cli.application

import picocli.CommandLine
import kotlin.system.exitProcess

fun main(vararg args: String) {
    App.run(*args)
}

class VersionProvider : AbstractCordaCliVersionProvider()

@CommandLine.Command(
    name = "corda-cli",
    versionProvider = VersionProvider::class
)
class Command {
    @CommandLine.Option(names = ["-h", "--help", "-?", "-help"], usageHelp = true, description = ["Display help and exit."])
    @Suppress("unused")
    var help = false

    @CommandLine.Option(names = ["-V", "--version"], versionHelp = true, description = ["Display version and exit."])
    var showVersion = false
}

/**
 * A boot class that starts picocli and loads in plugin sub commands.
 */
object App {

    @CommandLine.Spec
    @Suppress("unused")
    lateinit var spec: CommandLine.Model.CommandSpec

    fun run(vararg args: String) {

        val commandLine = CommandLine(Command())
//        cordaCliPlugins.forEach { cordaCliPlugin ->
//            commandLine.addSubcommand(cordaCliPlugin)
//        }

        val commandResult = commandLine
            .setCaseInsensitiveEnumValuesAllowed(true)
            .execute(*args)
        exitProcess(commandResult)
    }
}
