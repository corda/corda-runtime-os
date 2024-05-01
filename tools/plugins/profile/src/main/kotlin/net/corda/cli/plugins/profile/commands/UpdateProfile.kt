package net.corda.cli.plugins.profile.commands

import com.fasterxml.jackson.module.kotlin.readValue
import net.corda.cli.plugins.profile.ProfileUtils
import picocli.CommandLine
import picocli.CommandLine.Option

@CommandLine.Command(
    name = "update",
    description = ["Update an existing profile."],
    mixinStandardHelpOptions = true
)
class UpdateProfile : Runnable {

    @Option(names = ["-n", "--name"], description = ["Profile name"], required = true)
    lateinit var profileName: String

    @Option(names = ["-u", "--username"], description = ["Username"])
    var username: String? = null

    @Option(names = ["-p", "--password"], description = ["Password"])
    var password: String? = null

    @Option(names = ["-e", "--endpoint"], description = ["Endpoint URL"])
    var endpoint: String? = null

    override fun run() {
        val profiles = ProfileUtils.loadProfiles().toMutableMap()

        if (!profiles.containsKey(profileName)) {
            println("Profile '$profileName' does not exist.")
            return
        }

        val profile = ProfileUtils.objectMapper.readValue<MutableMap<String, Any>>(
            ProfileUtils.objectMapper.writeValueAsString(profiles[profileName])
        )

        if (username != null) {
            profile["username"] = username!!
        }

        if (password != null) {
            profile["password"] = password!!
        }

        if (endpoint != null) {
            profile["endpoint"] = endpoint!!
        }

        profiles[profileName] = profile

        ProfileUtils.saveProfiles(profiles)
        println("Profile '$profileName' updated successfully.")
    }
}
