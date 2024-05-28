package net.corda.cli.plugins.cpi

import net.corda.cli.plugins.cpi.commands.CPIList
import net.corda.cli.plugins.cpi.commands.CPIUpload
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import picocli.CommandLine

@CommandLine.Command(
    name = "cpi",
    subcommands = [CPIUpload::class, CPIList::class],
    mixinStandardHelpOptions = true,
    description = ["CPI(Corda Package Installer) related operations"],
)
class CPICliPlugin {

    private companion object {
        val logger: Logger = LoggerFactory.getLogger(this::class.java)
    }
}
