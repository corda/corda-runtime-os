package net.corda.cli.plugins.profile.commands

import net.corda.sdk.profile.CliProfile
import net.corda.sdk.profile.ProfileKey
import net.corda.sdk.profile.ProfileUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import picocli.CommandLine
import picocli.CommandLine.Option

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

    @Option(
        names = ["-p", "--property"],
        description = ["Profile property (key=value). Valid keys are: ${ProfileKey.CONST_KEYS_WITH_DESCRIPTIONS}"],
    )
    var properties: Array<String> = emptyArray()

    override fun run() {
        val profiles = ProfileUtils.loadProfiles().toMutableMap()

        val profileProperties = if (profiles.containsKey(profileName)) {
            profiles[profileName]?.properties?.toMutableMap()!!
        } else {
            mutableMapOf()
        }

        val updatedProperties = ProfileUtils.createPropertiesMapFromCliArguments(properties)
        profileProperties.putAll(updatedProperties)

        profiles[profileName] = CliProfile(profileProperties)

        ProfileUtils.saveProfiles(profiles)
        sysOut.info("Profile '$profileName' updated successfully.")
    }
}
