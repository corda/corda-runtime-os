package net.corda.cli.plugin.initialconfig

import picocli.CommandLine.Command

@Command(
    name = "initial-config",
    subcommands = [RbacConfigSubcommand::class, DbConfigSubcommand::class, CryptoConfigSubcommand::class],
    mixinStandardHelpOptions = true,
    description = ["Create SQL files to write the initial config to a new cluster"],
)
class InitialConfigPlugin
