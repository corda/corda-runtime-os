package net.corda.cli.plugins.profile

import net.corda.cli.api.AbstractCordaCliVersionProvider
import net.corda.cli.api.CordaCliPlugin
import org.pf4j.Extension
import org.pf4j.Plugin
import picocli.CommandLine

class VersionProvider : AbstractCordaCliVersionProvider()

@Suppress("unused")
class PackagePluginWrapper : Plugin() {
    @Extension
    @CommandLine.Command(
        name = "profile",
        subcommands = [CreateProfile::class],
        mixinStandardHelpOptions = true,
        description = ["Plugin for profile operations."],
        versionProvider = VersionProvider::class
    )
    class ProfilePlugin : CordaCliPlugin
}
