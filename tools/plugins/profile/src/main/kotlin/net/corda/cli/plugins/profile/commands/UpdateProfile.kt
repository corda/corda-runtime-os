package net.corda.cli.plugins.profile.commands

import com.fasterxml.jackson.module.kotlin.readValue
import net.corda.cli.plugins.profile.ProfileUtils
import net.corda.permissions.password.impl.PasswordServiceImpl
import net.corda.sdk.profile.ProfileConfig
import picocli.CommandLine
import picocli.CommandLine.Option
import java.security.SecureRandom

@CommandLine.Command(
    name = "update",
    description = ["Update an existing profile."],
    mixinStandardHelpOptions = true
)
class UpdateProfile : Runnable {

    @Option(names = ["-n", "--name"], description = ["Profile name"], required = true)
    lateinit var profileName: String

    @Option(names = ["-p", "--property"], description = ["Profile property (key=value)"])
    var properties: Array<String> = emptyArray()

    private val passwordService = PasswordServiceImpl(SecureRandom())

    override fun run() {
        val profiles = ProfileUtils.loadProfiles().toMutableMap()

        if (!profiles.containsKey(profileName)) {
            println("Profile '$profileName' does not exist.")
            return
        }

        val profile = ProfileUtils.objectMapper.readValue<MutableMap<String, Any>>(
            ProfileUtils.objectMapper.writeValueAsString(profiles[profileName])
        )

        properties.forEach { property ->
            val (key, value) = property.split("=")
            if (!ProfileUtils.isValidKey(key)) {
                throw IllegalArgumentException("Invalid key '$key'. Allowed keys are: ${ProfileConfig.VALID_KEYS}")
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
        println("Profile '$profileName' updated successfully.")
    }
}
