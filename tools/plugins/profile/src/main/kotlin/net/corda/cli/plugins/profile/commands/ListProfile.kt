package net.corda.cli.plugins.profile.commands

import net.corda.cli.plugins.profile.ProfileUtils.loadProfiles
import picocli.CommandLine

@CommandLine.Command(
    name = "list",
    description = ["List all profiles."],
    mixinStandardHelpOptions = true
)
class ListProfile : Runnable {
    override fun run() {
        val profiles = loadProfiles()

        if (profiles.isEmpty()) {
            println("No profiles found.")
        } else {
            println("Available profiles:")
            profiles.keys.forEach { println("- $it") }
        }
    }
}
