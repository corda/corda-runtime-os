package net.corda.cli.plugins.profile.commands

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

    override fun run() {
        val profiles = loadProfiles()

        if (profiles.isEmpty()) {
            sysOut.info("No profiles found.")
        } else {
            sysOut.info("Available profiles:")
            profiles.keys.forEach { sysOut.info("- $it") }
        }
    }
}
