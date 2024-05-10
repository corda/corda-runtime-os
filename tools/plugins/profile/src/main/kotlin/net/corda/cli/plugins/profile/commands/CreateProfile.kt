package net.corda.cli.plugins.profile.commands

import net.corda.permissions.password.impl.PasswordServiceImpl
import net.corda.sdk.profile.ProfileUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import picocli.CommandLine
import picocli.CommandLine.Option
import java.security.SecureRandom

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

    @Option(names = ["-p", "--property"], description = ["Profile property (key=value)"], required = true)
    lateinit var properties: Array<String>

    private val passwordService = PasswordServiceImpl(SecureRandom())

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

        val profile = mutableMapOf<String, Any>()
        properties.forEach { property ->
            val (key, value) = property.split("=")
            if (!ProfileUtils.isValidKey(key)) {
                val error = "Invalid key '$key'. Allowed keys are: ${ProfileUtils.VALID_KEYS}"
                sysErr.error(error)
                throw IllegalArgumentException(error)
            }
            if (key.lowercase().contains("password")) {
                val passwordHash = passwordService.saltAndHash(value)
                profile[key] = passwordHash.value
                profile["${key}_salt"] = passwordHash.salt
            } else {
                profile[key] = value
            }
        }

        profiles[profileName] = profile

        ProfileUtils.saveProfiles(profiles)
        sysOut.info("Profile '$profileName' created successfully.")
        println("Profile '$profileName' created successfully.")
    }
}
