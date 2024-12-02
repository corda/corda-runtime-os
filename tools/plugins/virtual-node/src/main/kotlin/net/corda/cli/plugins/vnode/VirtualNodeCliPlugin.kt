package net.corda.cli.plugins.vnode

import net.corda.cli.plugins.vnode.commands.PlatformMigration
import net.corda.cli.plugins.vnode.commands.ResetCommand
import picocli.CommandLine

@CommandLine.Command(
    name = "vnode",
    subcommands = [ResetCommand::class, PlatformMigration::class],
    mixinStandardHelpOptions = true,
    description = ["Manages a virtual node"],
)
class VirtualNodeCliPlugin
