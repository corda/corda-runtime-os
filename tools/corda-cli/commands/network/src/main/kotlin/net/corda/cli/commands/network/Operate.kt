package net.corda.cli.commands.network

import picocli.CommandLine.Command

@Command(
    name = "operate",
    description = [
        "MGM operations for managing application networks",
    ],
    subcommands = [
        net.corda.cli.commands.network.AllowClientCertificate::class,
        ExportGroupPolicy::class,
    ],
    mixinStandardHelpOptions = true,
)
class Operate
