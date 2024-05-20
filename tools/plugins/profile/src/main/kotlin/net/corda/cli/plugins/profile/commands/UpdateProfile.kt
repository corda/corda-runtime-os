package net.corda.cli.plugins.profile.commands

import com.fasterxml.jackson.module.kotlin.readValue
import net.corda.permissions.password.PasswordHash
import net.corda.permissions.password.impl.PasswordServiceImpl
import net.corda.sdk.profile.ProfileUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import picocli.CommandLine
import picocli.CommandLine.Option
import java.security.SecureRandom

@CommandLine.Command(
    name = "update",
    description = ["Update an existing profile."],
    mixinStandardHelpOptions = true
)
class UpdateProfile : Runnable {

    private companion object {
        val sysOut: Logger = LoggerFactory.getLogger("SystemOut")
        val sysErr: Logger = LoggerFactory.getLogger("SystemErr")
    }

    @Option(names = ["-n", "--name"], description = ["Profile name"], required = true)
    lateinit var profileName: String

    @Option(names = ["-p", "--property"], description = ["Profile property (key=value)"])
    var properties: Array<String> = emptyArray()

    private val passwordService = PasswordServiceImpl(SecureRandom())

    override fun run() {
        val profiles = ProfileUtils.loadProfiles().toMutableMap()

        val profile = if (profiles.containsKey(profileName)) {
            ProfileUtils.objectMapper.readValue<MutableMap<String, Any>>(
                ProfileUtils.objectMapper.writeValueAsString(profiles[profileName])
            )
        } else {
            mutableMapOf<String, Any>()
        }

        properties.forEach { property ->
            val (key, value) = property.split("=")
            if (!ProfileUtils.isValidKey(key)) {
                val error = "Invalid key '$key'. Allowed keys are:\n ${ProfileUtils.getProfileKeysWithDescriptions()}"
                sysErr.error(error)
                throw IllegalArgumentException(error)
            }
            if (key.lowercase().contains("password")) {
                if (profile.containsKey(key)) {
                    val oldHash = profile[key] as String
                    val oldSalt = profile["${key}_salt"] as String
                    val oldPasswordHash = PasswordHash(oldSalt, oldHash)

                    // only update salt and hash if the password has changed
                    if (!passwordService.verifies(value, oldPasswordHash)) {
                        val passwordHash = passwordService.saltAndHash(value)
                        profile[key] = passwordHash.value
                        profile["${key}_salt"] = passwordHash.salt
                    }
                } else {
                    val passwordHash = passwordService.saltAndHash(value)
                    profile[key] = passwordHash.value
                    profile["${key}_salt"] = passwordHash.salt
                }
            } else {
                profile[key] = value
            }
        }

        profiles[profileName] = profile

        ProfileUtils.saveProfiles(profiles)
        sysOut.info("Profile '$profileName' updated successfully.")
    }
}
