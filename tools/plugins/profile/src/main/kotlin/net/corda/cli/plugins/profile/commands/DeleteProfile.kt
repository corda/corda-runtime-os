package net.corda.cli.plugins.profile.commands

import net.corda.cli.plugins.profile.ProfileUtils.loadProfiles
import net.corda.cli.plugins.profile.ProfileUtils.saveProfiles
import picocli.CommandLine
import picocli.CommandLine.Option

@CommandLine.Command(
    name = "delete",
    description = ["Delete a profile."],
    mixinStandardHelpOptions = true
)
class DeleteProfile : Runnable {

    @Option(names = ["-n", "--name"], description = ["Profile name"], required = true)
    lateinit var profileName: String

    override fun run() {
        val profiles = loadProfiles()

        if (!profiles.containsKey(profileName)) {
            println("Profile '$profileName' does not exist.")
            return
        }

        println("Are you sure you want to delete profile '$profileName'? (y/n)")
        val confirmation = readlnOrNull()
        if (confirmation?.lowercase() != "y") {
            println("Profile deletion aborted.")
            return
        }

        val updatedProfiles = profiles.toMutableMap()
        updatedProfiles.remove(profileName)

        saveProfiles(updatedProfiles)
        println("Profile '$profileName' deleted.")
    }
}
