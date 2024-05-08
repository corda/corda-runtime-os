package net.corda.cli.plugins.profile.commands

import net.corda.cli.plugins.profile.ProfileUtils
import net.corda.permissions.password.impl.PasswordServiceImpl
import picocli.CommandLine
import picocli.CommandLine.Option
import java.security.SecureRandom

@CommandLine.Command(
    name = "create",
    description = ["Create a new profile."],
    mixinStandardHelpOptions = true
)
class CreateProfile : Runnable {

    @Option(names = ["-n", "--name"], description = ["Profile name"], required = true)
    lateinit var profileName: String

    @Option(names = ["-p", "--property"], description = ["Profile property (key=value)"], required = true)
    lateinit var properties: Array<String>

    private val passwordService = PasswordServiceImpl(SecureRandom())

    override fun run() {
        val profiles = ProfileUtils.loadProfiles().toMutableMap()

        if (profiles.containsKey(profileName)) {
            println("Profile '$profileName' already exists. Overwrite? (y/n)")
            val confirmation = readLine()
            if (confirmation?.lowercase() != "y") {
                println("Profile creation aborted.")
                return
            }
        }

        val profile = mutableMapOf<String, Any>()
        properties.forEach { property ->
            val (key, value) = property.split("=")
            if (!ProfileUtils.isValidKey(key)) {
                throw IllegalArgumentException("Invalid key '$key'. Allowed keys are: ${ProfileUtils.validKeys}")
            }
            if (key.lowercase().contains("password")) {
                val passwordHash = passwordService.saltAndHash(value)
                profile[key] = passwordHash.value
                profile["${key}Salt"] = passwordHash.salt
            } else {
                profile[key] = value
            }
        }

        profiles[profileName] = profile

        ProfileUtils.saveProfiles(profiles)
        println("Profile '$profileName' created successfully.")
    }
}
