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
    name = "create",
    description = ["Create a new profile."],
    mixinStandardHelpOptions = true
)
class CreateProfile : Runnable {

    private companion object {
        val logger: Logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
        val sysOut: Logger = LoggerFactory.getLogger("SystemOut")
        val sysErr: Logger = LoggerFactory.getLogger("SystemErr")
    }

    @Option(names = ["-n", "--name"], description = ["Profile name"], required = true)
    lateinit var profileName: String

    @Option(
        names = ["-p", "--property"],
        description = ["Profile property (key=value). Valid keys are: ${ProfileKey.CONST_KEYS_WITH_DESCRIPTIONS}"],
        required = true
    )
    lateinit var properties: Array<String>

    private val secretEncryptionUtil = SecretEncryptionUtil()
    private val salt = UUID.randomUUID().toString()

    override fun run() {
        logger.debug("Creating profile: $profileName")
        val profiles = ProfileUtils.loadProfiles().toMutableMap()

        if (profiles.containsKey(profileName)) {
            sysOut.info("Profile '$profileName' already exists. Overwrite? (y/n)")
            val confirmation = readlnOrNull()
            if (confirmation?.lowercase() != "y") {
                sysOut.info("Profile creation aborted.")
                return
            }
        }

        val profileProperties = mutableMapOf<String, String>()
        properties.forEach { property ->
            val (key, value) = property.split("=")
            if (!ProfileKey.isValidKey(key)) {
                val error = "Invalid key '$key'. Allowed keys are:\n ${ProfileKey.CONST_KEYS_WITH_DESCRIPTIONS}"
                sysErr.error(error)
                throw IllegalArgumentException(error)
            }
            if (key.lowercase().contains("password")) {
                val encryptedPassword = secretEncryptionUtil.encrypt(value, salt, salt)
                profileProperties[key] = encryptedPassword
                profileProperties["${key}_salt"] = salt
            } else {
                profileProperties[key] = value
            }
        }

        profiles[profileName] = CliProfile(profileProperties)

        ProfileUtils.saveProfiles(profiles)
        sysOut.info("Profile '$profileName' created successfully.")
    }
}
