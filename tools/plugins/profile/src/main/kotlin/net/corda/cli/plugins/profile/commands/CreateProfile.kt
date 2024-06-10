package net.corda.cli.plugins.profile.commands

import net.corda.sdk.profile.CliProfile
import net.corda.sdk.profile.ProfileKey
import net.corda.sdk.profile.ProfileUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import picocli.CommandLine
import picocli.CommandLine.Option

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
    lateinit var properties: Set<String>

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

        val profileProperties = ProfileUtils.createPropertiesMapFromCliArguments(properties)
        profiles[profileName] = CliProfile(profileProperties)

        ProfileUtils.saveProfiles(profiles)
        sysOut.info("Profile '$profileName' created successfully.")
    }
}
