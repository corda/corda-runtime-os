package net.corda.cli.plugins.profile.commands

import net.corda.cli.plugins.profile.ProfileUtils.loadProfiles
import net.corda.cli.plugins.profile.ProfileUtils.saveProfiles
import picocli.CommandLine
import picocli.CommandLine.Option

@CommandLine.Command(
    name = "activate",
    description = ["Activate a profile."],
    mixinStandardHelpOptions = true
)
class ActivateProfile : Runnable {

    @Option(names = ["-n", "--name"], description = ["Profile name"], required = true)
    lateinit var profileName: String

    override fun run() {
        val profiles = loadProfiles()

        if (!profiles.containsKey(profileName)) {
            println("Profile '$profileName' does not exist.")
            return
        }

        val updatedProfiles = profiles.toMutableMap()
        updatedProfiles["current-profile"] = profileName

        saveProfiles(updatedProfiles)
        println("Profile '$profileName' activated.")
    }
}
