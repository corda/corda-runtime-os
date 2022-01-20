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
class TemplatePlugin(wrapper: PluginWrapper) : Plugin(wrapper) {

    override fun start() {}
    override fun stop() {}

    @Extension
    @CommandLine.Command(
        name = "template",
        description = ["Empty template plugin."]
    )
    class TemplatePluginEntry : CordaCliPlugin {}
}
