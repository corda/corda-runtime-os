package net.corda.cli.plugins.packaging

import net.corda.cli.api.CordaCliPlugin
import org.pf4j.Extension
import org.pf4j.Plugin
import org.pf4j.PluginWrapper
import picocli.CommandLine

@Suppress("unused")
class PackagePluginWrapper(wrapper: PluginWrapper) : Plugin(wrapper) {
    @Extension
    @CommandLine.Command(
        name = "package",
        subcommands = [CreateCpi::class, VerifyCpi::class],
        description = ["Plugin for CPI operations."]
    )
    class PackagePlugin : CordaCliPlugin
}
