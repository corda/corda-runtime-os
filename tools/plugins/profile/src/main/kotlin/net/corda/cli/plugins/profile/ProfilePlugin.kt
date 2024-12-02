package net.corda.cli.plugins.profile

import net.corda.cli.plugins.profile.commands.CreateProfile
import net.corda.cli.plugins.profile.commands.DeleteProfile
import net.corda.cli.plugins.profile.commands.ListProfile
import net.corda.cli.plugins.profile.commands.UpdateProfile
import picocli.CommandLine

@CommandLine.Command(
    name = "profile",
    subcommands = [
        CreateProfile::class,
        ListProfile::class,
        DeleteProfile::class,
        UpdateProfile::class,
    ],
    mixinStandardHelpOptions = true,
    description = ["Plugin for profile operations."],
)
class ProfilePlugin
