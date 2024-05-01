package net.corda.cli.plugins.profile

import net.corda.cli.api.AbstractCordaCliVersionProvider
import net.corda.cli.api.CordaCliPlugin
import net.corda.cli.plugins.profile.commands.ActivateProfile
import net.corda.cli.plugins.profile.commands.CreateProfile
import net.corda.cli.plugins.profile.commands.DeleteProfile
import net.corda.cli.plugins.profile.commands.ListProfile
import net.corda.cli.plugins.profile.commands.UpdateProfile
import org.pf4j.Extension
import org.pf4j.Plugin
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import picocli.CommandLine

class VersionProvider : AbstractCordaCliVersionProvider()

@Suppress("unused")
class ProfilePluginWrapper : Plugin() {

    private companion object {
        val logger: Logger = LoggerFactory.getLogger(this::class.java)
    }

    override fun start() {
        logger.debug("starting profile plugin")
    }

    override fun stop() {
        logger.debug("stopping profile plugin")
    }

    @Extension
    @CommandLine.Command(
        name = "profile",
        subcommands = [
            CreateProfile::class,
            ListProfile::class,
            ActivateProfile::class,
            DeleteProfile::class,
            UpdateProfile::class,
        ],
        mixinStandardHelpOptions = true,
        description = ["Plugin for profile operations."],
        versionProvider = VersionProvider::class
    )
    class PluginEntryPoint : CordaCliPlugin
}
