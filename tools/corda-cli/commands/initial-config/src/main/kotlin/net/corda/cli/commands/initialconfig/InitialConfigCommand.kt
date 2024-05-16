package net.corda.cli.commands.initialconfig

import picocli.CommandLine.Command

@Command(
    name = "initial-config",
    subcommands = [RbacConfigSubcommand::class, DbConfigSubcommand::class, CryptoConfigSubcommand::class],
    mixinStandardHelpOptions = true,
    description = ["Create SQL files to write the initial config to a new cluster"],
)
class InitialConfigCommand
