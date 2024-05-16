package net.corda.cli.commands.cpi

import net.corda.cli.commands.cpi.commands.CPIList
import net.corda.cli.commands.cpi.commands.CPIUpload
import picocli.CommandLine

@CommandLine.Command(
    name = "cpi",
    subcommands = [CPIUpload::class, CPIList::class],
    mixinStandardHelpOptions = true,
    description = ["CPI(Corda Package Installer) related operations"],
)
class CPICliCommand
