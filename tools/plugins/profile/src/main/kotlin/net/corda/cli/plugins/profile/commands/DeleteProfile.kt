package net.corda.cli.plugins.profile.commands

import net.corda.sdk.profile.ProfileUtils.loadProfiles
import net.corda.sdk.profile.ProfileUtils.saveProfiles
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import picocli.CommandLine
import picocli.CommandLine.Option

@CommandLine.Command(
    name = "delete",
    description = ["Delete a profile."],
    mixinStandardHelpOptions = true
)
class DeleteProfile : Runnable {

    private companion object {
        val logger: Logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
        val sysOut: Logger = LoggerFactory.getLogger("SystemOut")
    }

    @Option(names = ["-n", "--name"], description = ["Profile name"], required = true)
    lateinit var profileName: String

    override fun run() {
        logger.debug("Deleting profile: $profileName")
        val profiles = loadProfiles()

        sysOut.info("Are you sure you want to delete profile '$profileName'? (y/n)")
        val confirmation = readlnOrNull()
        if (confirmation?.lowercase() != "y") {
            sysOut.info("Profile deletion aborted.")
            return
        }

        val updatedProfiles = profiles.toMutableMap()
        updatedProfiles.remove(profileName)

        saveProfiles(updatedProfiles)
        sysOut.info("Profile '$profileName' deleted.")
    }
}
