package net.corda.cli.plugins.network

import picocli.CommandLine.Command

@Command(
    name = "operate",
    description = [
        "MGM operations for managing application networks",
    ],
    subcommands = [
        AllowClientCertificate::class,
        ExportGroupPolicy::class,
    ],
    mixinStandardHelpOptions = true,
)
class Operate
