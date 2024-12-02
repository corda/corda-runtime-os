package net.corda.cli.plugins.preinstall

import picocli.CommandLine


@CommandLine.Command(name = "preinstall",
    subcommands = [CheckLimits::class, CheckPostgres::class, CheckKafka::class, RunAll::class],
    mixinStandardHelpOptions = true,
    description = ["Preinstall checks for Corda."],
)
class PreInstallPlugin
