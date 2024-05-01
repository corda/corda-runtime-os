package net.corda.cli.plugins.profile.commands

import net.corda.cli.plugins.profile.ProfileUtils.loadProfiles
import net.corda.cli.plugins.profile.ProfileUtils.saveProfiles
import picocli.CommandLine
import picocli.CommandLine.Option

@CommandLine.Command(
    name = "create",
    description = ["Create a new profile."],
    mixinStandardHelpOptions = true
)
class CreateProfile : Runnable {

    @Option(names = ["-n", "--name"], description = ["Profile name"], required = true)
    lateinit var profileName: String

    @Option(names = ["-u", "--username"], description = ["Username"], required = true)
    lateinit var username: String

    @Option(names = ["-p", "--password"], description = ["Password"], required = true)
    lateinit var password: String

    @Option(names = ["-e", "--endpoint"], description = ["Endpoint URL"])
    var endpoint: String? = null

    override fun run() {
        val profiles = loadProfiles().toMutableMap()

        if (profiles.containsKey(profileName)) {
            println("Profile '$profileName' already exists. Overwrite? (y/n)")
            val confirmation = readlnOrNull()
            if (confirmation?.lowercase() != "y") {
                println("Profile creation aborted.")
                return
            }
        }

        val profile = mapOf(
            "username" to username,
            "password" to password,
            "endpoint" to endpoint
        )

        profiles[profileName] = profile

        saveProfiles(profiles)
        println("Profile '$profileName' created successfully.")
    }
}
