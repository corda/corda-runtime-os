package net.corda.cli.plugins.profile.commands

import net.corda.libs.configuration.secret.SecretEncryptionUtil
import net.corda.sdk.profile.CliProfile
import net.corda.sdk.profile.ProfileKey
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
        if (!ProfileKey.isValidKey(key)) {
            val error = "Invalid key '$key'. Allowed keys are:\n ${ProfileKey.getKeysWithDescriptions()}"
            sysErr.error(error)
            throw IllegalArgumentException(error)
        }
        if (key.lowercase().contains("password")) {
            val encryptedPassword = secretEncryptionUtil.encrypt(value, salt, salt)
            profile[key] = encryptedPassword
            profile["${key}_salt"] = salt
        } else {
            profile[key] = value
        }
    }

    override fun run() {
        val profiles = ProfileUtils.loadProfiles().toMutableMap()

        val profile = if (profiles.containsKey(profileName)) {
            profiles[profileName]?.properties?.toMutableMap()!!
        } else {
            mutableMapOf()
        }

        properties.forEach { property ->
            processProperty(profile, property)
        }

        profiles[profileName] = CliProfile(profile)

        ProfileUtils.saveProfiles(profiles)
        sysOut.info("Profile '$profileName' updated successfully.")
    }
}
