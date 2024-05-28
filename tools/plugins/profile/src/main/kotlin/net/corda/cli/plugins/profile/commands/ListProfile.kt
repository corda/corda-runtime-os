package net.corda.cli.plugins.profile.commands

import com.fasterxml.jackson.module.kotlin.readValue
import net.corda.libs.configuration.secret.SecretEncryptionUtil
import net.corda.sdk.profile.ProfileUtils
import net.corda.sdk.profile.ProfileUtils.loadProfiles
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import picocli.CommandLine

@CommandLine.Command(
    name = "list",
    description = ["List all profiles."],
    mixinStandardHelpOptions = true
)
class ListProfile : Runnable {

    private companion object {
        val sysOut: Logger = LoggerFactory.getLogger("SystemOut")
    }

    private val secretEncryptionUtil = SecretEncryptionUtil()

    private fun printProfile(profileName: String, profiles: MutableMap<String, Any>) {
        val profileProps = ProfileUtils.getProfileProperties(profileName, profiles)

        sysOut.info("- $profileName")
        profileProps.forEach { (key, value) ->
            if (key.lowercase().endsWith("_salt")) {
                // Skip printing the salt
                return@forEach
            }

            if (key.lowercase().contains("password")) {
                val salt = profileProps["${key}_salt"]
                if (salt != null) {
                    val decryptedPassword = secretEncryptionUtil.decrypt(value, salt, salt)
                    sysOut.info("  $key: $decryptedPassword")
                } else {
                    sysOut.info("  $key: Unable to decrypt password")
                }
            } else {
                sysOut.info("  $key: $value")
            }
        }
    }

    override fun run() {
        val profiles: MutableMap<String, Any> = loadProfiles().toMutableMap()

        if (profiles.isEmpty()) {
            sysOut.info("No profiles found.")
        } else {
            sysOut.info("Available profiles:")
            profiles.keys.forEach { profileName ->
                printProfile(profileName, profiles)
            }
        }
    }
}
