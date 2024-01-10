package net.corda.cli.plugins.packaging

import net.corda.cli.api.CordaCliPlugin
import org.pf4j.Extension
import org.pf4j.Plugin
import picocli.CommandLine

@Suppress("unused")
class PackagePluginWrapper : Plugin() {
    @Extension
    @CommandLine.Command(
        name = "package",
        subcommands = [CreateCpiV2::class, Verify::class, CreateCpb::class, SignCpx::class],
        mixinStandardHelpOptions = true,
        description = ["Plugin for CPB, CPI operations."]
    )
    class PackagePlugin : CordaCliPlugin
}
