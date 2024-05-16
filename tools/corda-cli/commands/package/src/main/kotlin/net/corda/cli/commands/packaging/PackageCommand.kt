package net.corda.cli.commands.packaging

import picocli.CommandLine

@Suppress("unused")
@CommandLine.Command(
    name = "package",
    subcommands = [CreateCpiV2::class, Verify::class, CreateCpb::class, SignCpx::class],
    mixinStandardHelpOptions = true,
    description = ["Plugin for CPB, CPI operations."],
)
class PackageCommand
