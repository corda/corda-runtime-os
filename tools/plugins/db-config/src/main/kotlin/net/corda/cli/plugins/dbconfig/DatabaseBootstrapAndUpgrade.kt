package net.corda.cli.plugins.dbconfig

import picocli.CommandLine


@CommandLine.Command(
    name = "database",
    subcommands = [Spec::class],
    mixinStandardHelpOptions = true,
    description = ["Does Database bootstrapping and upgrade"],
)
class DatabaseBootstrapAndUpgrade
