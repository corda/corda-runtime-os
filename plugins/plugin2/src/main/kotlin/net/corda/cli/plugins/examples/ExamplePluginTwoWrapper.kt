package net.corda.cli.plugins.examples

import org.pf4j.Extension
import org.pf4j.Plugin
import org.pf4j.PluginWrapper
import net.corda.cli.api.CordaCliPlugin
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import picocli.CommandLine

/**
 * An Example Plugin that uses class based subcommands
 */
class ExamplePluginTwoWrapper(wrapper: PluginWrapper) : Plugin(wrapper) {

    companion object {
        private val logger: Logger = LoggerFactory.getLogger(ExamplePluginTwoWrapper::class.java)
    }

    override fun start() {
        logger.debug("ExamplePluginTwo.start()")
    }

    override fun stop() {
        logger.debug("ExamplePluginTwo.stop()")
    }

    @Extension
    @CommandLine.Command(
        name = "pluginTwo",
        subcommands = [SubCommandOne::class],
        description = ["Example Plugin two using class based subcommands"]
    )
    class ExamplePluginTwo : CordaCliPlugin {}
}
