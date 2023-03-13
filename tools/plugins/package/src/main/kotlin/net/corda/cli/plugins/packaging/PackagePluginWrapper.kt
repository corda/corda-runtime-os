package net.corda.cli.plugins.packaging

import net.corda.cli.api.CordaCliPlugin
import org.pf4j.ExtensionPoint
import org.pf4j.Plugin
import org.pf4j.PluginWrapper
import picocli.CommandLine

@Suppress("unused")
class PackagePluginWrapper(wrapper: PluginWrapper) : Plugin(wrapper) {
    @CommandLine.Command(
        name = "package",
        subcommands = [CreateCpiV2::class, Verify::class, CreateCpb::class, SignCpx::class],
        description = ["Plugin for CPB, CPI operations."]
    )
    class PackagePlugin : CordaCliPlugin, ExtensionPoint
}
