package net.corda.cli.commands.dbconfig

import picocli.CommandLine

@CommandLine.Command(
    name = "database",
    subcommands = [Spec::class],
    mixinStandardHelpOptions = true,
    description = ["Does Database bootstrapping and upgrade"],
)
class DatabaseBootstrapAndUpgradeCommand
