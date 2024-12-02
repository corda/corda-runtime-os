package net.corda.cli.application

import net.corda.cli.plugin.initialRbac.InitialRbacPlugin
import net.corda.cli.plugin.initialconfig.InitialConfigPlugin
import net.corda.cli.plugin.secretconfig.SecretConfigPlugin
import net.corda.cli.plugins.cpi.CPICliPlugin
import net.corda.cli.plugins.dbconfig.DatabaseBootstrapAndUpgrade
import net.corda.cli.plugins.network.NetworkPluginWrapper
import net.corda.cli.plugins.packaging.PackagePlugin
import net.corda.cli.plugins.preinstall.PreInstallPlugin
import net.corda.cli.plugins.profile.ProfilePlugin
import net.corda.cli.plugins.topicconfig.TopicPlugin
import net.corda.cli.plugins.vnode.VirtualNodeCliPlugin
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
        // Setup loggers to redirect sysOut and sysErr
        LoggerStream.redirectSystemAndErrorOut()

        val commandLine = CommandLine(Command())
        commandLine.addSubcommand(VirtualNodeCliPlugin())
        commandLine.addSubcommand(PackagePlugin())
        commandLine.addSubcommand(DatabaseBootstrapAndUpgrade())
        commandLine.addSubcommand(PreInstallPlugin())
        commandLine.addSubcommand(ProfilePlugin())
        commandLine.addSubcommand(TopicPlugin())
        commandLine.addSubcommand(SecretConfigPlugin())
        commandLine.addSubcommand(CPICliPlugin())
        commandLine.addSubcommand(InitialConfigPlugin())
        commandLine.addSubcommand(InitialRbacPlugin())
        commandLine.addSubcommand(NetworkPluginWrapper.NetworkPlugin())
        commandLine.addSubcommand(NetworkPluginWrapper.MgmPlugin())

        val commandResult = commandLine
            .setCaseInsensitiveEnumValuesAllowed(true)
            .execute(*args)
        exitProcess(commandResult)
    }
}
