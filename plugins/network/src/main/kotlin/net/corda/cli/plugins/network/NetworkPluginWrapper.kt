package net.corda.cli.plugins.network

import net.corda.cli.api.CordaCliPlugin
import net.corda.cli.api.serviceUsers.HttpServiceUser
import net.corda.cli.api.services.HttpService
import org.pf4j.Extension
import org.pf4j.Plugin
import org.pf4j.PluginWrapper
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import picocli.CommandLine
import picocli.CommandLine.Mixin

class NetworkPluginWrapper(wrapper: PluginWrapper) : Plugin(wrapper) {
    companion object {
        private val logger: Logger = LoggerFactory.getLogger(NetworkPlugin::class.java)
    }

    override fun start() {
        logger.debug("Network plugin started.")
    }

    override fun stop() {
        logger.debug("Network plugin stopped.")
    }

    @Extension
    @CommandLine.Command(
        name = "network",
        mixinStandardHelpOptions = true,
        description = ["Plugin for interacting with a network."]
    )
    class NetworkPlugin : CordaCliPlugin, HttpServiceUser {
        @Mixin
        override lateinit var service: HttpService

        @CommandLine.Command(
            name = "members-list",
            description = ["Shows the list of members on the network."]
        )
        fun getMembersList(
            @CommandLine.Option(
                names = ["-h", "--holding-identity-id"],
                arity = "1",
                description = ["ID of the holding identity to be checked."]
            ) holdingIdentityId: String?
        ) {
            require(holdingIdentityId != null)
            service.get("/members?holdingIdentityId=$holdingIdentityId")
        }
    }
}
