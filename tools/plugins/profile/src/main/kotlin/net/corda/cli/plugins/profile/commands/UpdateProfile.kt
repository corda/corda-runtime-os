package net.corda.cli.plugins.profile.commands

import com.fasterxml.jackson.module.kotlin.readValue
import net.corda.libs.configuration.secret.SecretEncryptionUtil
import net.corda.sdk.profile.ProfileUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import picocli.CommandLine
import picocli.CommandLine.Option
import java.util.UUID

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

    private val secretEncryptionUtil = SecretEncryptionUtil()
    private val salt = UUID.randomUUID().toString()

    private fun processProperty(profile: MutableMap<String, String>, property: String) {
        val (key, value) = property.split("=")
        if (!ProfileUtils.isValidKey(key)) {
            val error = "Invalid key '$key'. Allowed keys are:\n ${ProfileUtils.getProfileKeysWithDescriptions()}"
            sysErr.error(error)
            throw IllegalArgumentException(error)
        }

        if (key.lowercase().contains("password")) {
            if (profile.containsKey(key)) {
                val oldEncryptedPassword = profile[key] as String
                val oldSalt = profile["${key}_salt"] as String
                val oldPassword = secretEncryptionUtil.decrypt(oldEncryptedPassword, oldSalt, oldSalt)

                // only update salt and encrypted password if the password has changed
                if (value != oldPassword) {
                    val encryptedPassword = secretEncryptionUtil.encrypt(value, salt, salt)
                    profile[key] = encryptedPassword
                    profile["${key}_salt"] = salt
                }
            } else {
                val encryptedPassword = secretEncryptionUtil.encrypt(value, salt, salt)
                profile[key] = encryptedPassword
                profile["${key}_salt"] = salt
            }
        } else {
            profile[key] = value
        }
    }

    override fun run() {
        val profiles = ProfileUtils.loadProfiles().toMutableMap()

        val profile = if (profiles.containsKey(profileName)) {
            ProfileUtils.getProfileProperties(profileName, profiles)
        } else {
            mutableMapOf()
        }

        properties.forEach { property ->
            processProperty(profile, property)
        }

        profiles[profileName] = profile

        ProfileUtils.saveProfiles(profiles)
        sysOut.info("Profile '$profileName' updated successfully.")
    }
}
