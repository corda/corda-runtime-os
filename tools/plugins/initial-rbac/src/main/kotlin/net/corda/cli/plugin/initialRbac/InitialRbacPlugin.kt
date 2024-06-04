package net.corda.cli.plugin.initialRbac

import net.corda.cli.plugin.initialRbac.commands.AllClusterRolesSubcommand
import net.corda.cli.plugin.initialRbac.commands.CordaDeveloperSubcommand
import net.corda.cli.plugin.initialRbac.commands.FlowExecutorSubcommand
import net.corda.cli.plugin.initialRbac.commands.UserAdminSubcommand
import net.corda.cli.plugin.initialRbac.commands.VNodeCreatorSubcommand
import picocli.CommandLine

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
class InitialRbacPlugin
