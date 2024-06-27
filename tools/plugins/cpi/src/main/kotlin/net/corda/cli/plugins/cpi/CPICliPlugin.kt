package net.corda.cli.plugins.cpi

import net.corda.cli.plugins.cpi.commands.CPIList
import net.corda.cli.plugins.cpi.commands.CPIUpload
import picocli.CommandLine

@CommandLine.Command(
    name = "cpi",
    subcommands = [CPIUpload::class, CPIList::class],
    mixinStandardHelpOptions = true,
    description = ["CPI(Corda Package Installer) related operations"],
)
class CPICliPlugin
