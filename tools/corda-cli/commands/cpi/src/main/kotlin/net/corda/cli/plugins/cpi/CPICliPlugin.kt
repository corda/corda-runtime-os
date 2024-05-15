package net.corda.cli.plugins.cpi

import net.corda.cli.api.AbstractCordaCliVersionProvider
import net.corda.cli.api.CordaCliPlugin
import net.corda.cli.plugins.cpi.commands.CPIList
import net.corda.cli.plugins.cpi.commands.CPIUpload
import org.pf4j.Extension
import org.pf4j.Plugin
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import picocli.CommandLine

class VersionProvider : AbstractCordaCliVersionProvider()

@Suppress("unused")
class CPICliPlugin : Plugin() {

    private companion object {
        val logger: Logger = LoggerFactory.getLogger(this::class.java)
    }

    override fun start() {
        logger.debug("starting cpi upload plugin")
    }

    override fun stop() {
        logger.debug("stopping cpi upload plugin")
    }

    @Extension
    @CommandLine.Command(
        name = "cpi",
        subcommands = [CPIUpload::class, CPIList::class],
        mixinStandardHelpOptions = true,
        description = ["CPI(Corda Package Installer) related operations"],
        versionProvider = VersionProvider::class
    )
    class PluginEntryPoint : CordaCliPlugin
}
