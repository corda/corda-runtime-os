package net.corda.cli.plugins.profile.commands

import com.fasterxml.jackson.module.kotlin.readValue
import net.corda.permissions.password.impl.PasswordServiceImpl
import net.corda.sdk.profile.ProfileUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import picocli.CommandLine
import picocli.CommandLine.Option
import java.security.SecureRandom
import kotlin.system.exitProcess

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

        if (!profiles.containsKey(profileName)) {
            sysErr.error("Profile '$profileName' does not exist.")
            exitProcess(1)
        }

        val profile = ProfileUtils.objectMapper.readValue<MutableMap<String, Any>>(
            ProfileUtils.objectMapper.writeValueAsString(profiles[profileName])
        )

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
        sysOut.info("Profile '$profileName' updated successfully.")
    }
}
