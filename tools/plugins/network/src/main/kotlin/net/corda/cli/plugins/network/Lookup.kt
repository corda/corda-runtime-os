package net.corda.cli.plugins.network

import picocli.CommandLine.Command

@Command(
    name = "lookup",
    subcommands = [
        MemberLookup::class,
        GroupParametersLookup::class,
    ],
    mixinStandardHelpOptions = true,
    description = ["Look up members or group parameters."],
)
class Lookup
