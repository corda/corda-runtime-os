package net.corda.cli.commands.initialRbac

import net.corda.cli.commands.initialRbac.commands.AllClusterRolesSubcommand
import net.corda.cli.commands.initialRbac.commands.CordaDeveloperSubcommand
import net.corda.cli.commands.initialRbac.commands.FlowExecutorSubcommand
import net.corda.cli.commands.initialRbac.commands.UserAdminSubcommand
import net.corda.cli.commands.initialRbac.commands.VNodeCreatorSubcommand
import picocli.CommandLine

@Suppress("unused")
@CommandLine.Command(
    name = "initial-rbac",
    subcommands = [
        UserAdminSubcommand::class, VNodeCreatorSubcommand::class,
        CordaDeveloperSubcommand::class, FlowExecutorSubcommand::class,
        AllClusterRolesSubcommand::class
    ],
    mixinStandardHelpOptions = true,
    description = ["Creates common RBAC roles"],
)
class InitialRbacCommand
