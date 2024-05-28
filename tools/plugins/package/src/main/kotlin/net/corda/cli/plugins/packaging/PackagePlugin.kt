package net.corda.cli.plugins.packaging

import picocli.CommandLine

@CommandLine.Command(
    name = "package",
    subcommands = [CreateCpiV2::class, Verify::class, CreateCpb::class, SignCpx::class],
    mixinStandardHelpOptions = true,
    description = ["Plugin for CPB, CPI operations."],
)
class PackagePlugin

