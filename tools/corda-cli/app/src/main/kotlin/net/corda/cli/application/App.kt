package net.corda.cli.application

import net.corda.cli.commands.cpi.CPICliCommand
import net.corda.cli.commands.dbconfig.DatabaseBootstrapAndUpgradeCommand
import net.corda.cli.commands.initialRbac.InitialRbacCommand
import net.corda.cli.commands.initialconfig.InitialConfigCommand
import net.corda.cli.commands.network.NetworkCommandWrapper
import net.corda.cli.commands.packaging.PackageCommand
import net.corda.cli.commands.preinstall.PreInstallCommand
import net.corda.cli.commands.secretconfig.SecretConfigCommand
import net.corda.cli.commands.topicconfig.TopicConfigCommand
import net.corda.cli.commands.vnode.VirtualNodeCommand
import picocli.CommandLine
import kotlin.system.exitProcess

fun main(vararg args: String) {
    App.run(*args)
}

@CommandLine.Command(
    name = "corda-cli",
    versionProvider = CordaCliVersionProvider::class
)
class Command {
    @CommandLine.Option(
        names = ["-h", "--help", "-?", "-help"],
        usageHelp = true,
        description = ["Display help and exit."]
    )
    @Suppress("unused")
    var help = false

    @CommandLine.Option(names = ["-V", "--version"], versionHelp = true, description = ["Display version and exit."])
    var showVersion = false
}

/**
 * A boot class that starts picocli and loads the sub commands.
 */
object App {

    @CommandLine.Spec
    @Suppress("unused")
    lateinit var spec: CommandLine.Model.CommandSpec

    fun run(vararg args: String) {

        val commandLine = CommandLine(Command())
        commandLine.addSubcommand(CPICliCommand())
        commandLine.addSubcommand(DatabaseBootstrapAndUpgradeCommand())
        commandLine.addSubcommand(InitialConfigCommand())
        commandLine.addSubcommand(InitialRbacCommand())
        commandLine.addSubcommand(NetworkCommandWrapper.NetworkCommand())
        commandLine.addSubcommand(NetworkCommandWrapper.MgmCommand())
        commandLine.addSubcommand(PackageCommand())
        commandLine.addSubcommand(PreInstallCommand())
        commandLine.addSubcommand(SecretConfigCommand())
        commandLine.addSubcommand(TopicConfigCommand())
        commandLine.addSubcommand(VirtualNodeCommand())

        val commandResult = commandLine
            .setCaseInsensitiveEnumValuesAllowed(true)
            .execute(*args)
        exitProcess(commandResult)
    }
}
