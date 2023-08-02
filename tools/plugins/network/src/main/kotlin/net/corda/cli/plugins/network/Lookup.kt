package net.corda.cli.plugins.network

import picocli.CommandLine.Command

@Command(
    name = "lookup",
    subcommands = [
        Members::class,
        GroupParameters::class,
    ],
    description = ["Lookup members or group parameters."]
)
class Lookup