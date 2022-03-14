package net.corda.cli.plugins.examples

import org.pf4j.Extension
import org.pf4j.Plugin
import org.pf4j.PluginWrapper
import net.corda.cli.api.CordaCliPlugin
import picocli.CommandLine

/**
 * An Example Plugin that uses class based subcommands
 */
class ExamplePluginTwo(wrapper: PluginWrapper) : Plugin(wrapper) {

    override fun start() {
    }

    override fun stop() {
    }

    @Extension
    @CommandLine.Command(
        name = "plugin-two",
        subcommands = [SubCommandOne::class],
        description = ["Example Plugin two using class based subcommands"]
    )
    class ExamplePluginTwoEntry : CordaCliPlugin {}
}
