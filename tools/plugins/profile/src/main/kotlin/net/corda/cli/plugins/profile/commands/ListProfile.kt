package net.corda.cli.plugins.profile.commands

import net.corda.libs.configuration.secret.SecretEncryptionUtil
import net.corda.sdk.profile.ProfileUtils.loadProfiles
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import picocli.CommandLine

@CommandLine.Command(
    name = "list",
    description = ["List all profiles and profile properties."],
    mixinStandardHelpOptions = true
)
class ListProfile : Runnable {

    private companion object {
        val sysOut: Logger = LoggerFactory.getLogger("SystemOut")
    }

    @CommandLine.Option(names = ["-e", "--encrypted"], description = ["Show passwords in encrypted form"])
    var showEncrypted: Boolean = false

    private val secretEncryptionUtil = SecretEncryptionUtil()

    @Suppress("NestedBlockDepth")
    private fun printProfile(profile: Map<String, String>) {
        profile.forEach { (key, value) ->
            if (key.lowercase().endsWith("_salt")) {
                // Skip printing the salt
                return@forEach
            }

            if (key.lowercase().contains("password")) {
                val salt = profile["${key}_salt"]
                if (salt != null) {
                    if (showEncrypted) {
                        sysOut.info("  $key: $value")
                    } else {
                        val decryptedPassword = secretEncryptionUtil.decrypt(value, salt, salt)
                        sysOut.info("  $key: $decryptedPassword")
                    }
                } else {
                    sysOut.info("  $key: Unable to decrypt password")
                }
            } else {
                sysOut.info("  $key: $value")
            }
        }
    }

    override fun run() {
        val profiles = loadProfiles().toMutableMap()

        if (profiles.isEmpty()) {
            sysOut.info("No profiles found.")
        } else {
            sysOut.info("Available profiles:")
            profiles.keys.forEach { profileName ->
                sysOut.info("- $profileName")
                printProfile(profiles[profileName]!!.properties)
            }
        }
    }
}
