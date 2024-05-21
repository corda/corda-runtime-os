package net.corda.cli.commands.vnode

import net.corda.cli.commands.vnode.commands.PlatformMigration
import net.corda.cli.commands.vnode.commands.ResetCommand
import picocli.CommandLine

@Suppress("unused")
@CommandLine.Command(
    name = "vnode",
    subcommands = [ResetCommand::class, PlatformMigration::class],
    mixinStandardHelpOptions = true,
    description = ["Manages a virtual node"],
)
class VirtualNodeCommand
