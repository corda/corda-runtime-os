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

        @Suppress("LongParameterList")
        @CommandLine.Command(
            name = "members-list",
            description = ["Shows the list of members on the network."]
        )
        fun getMembersList(
            @CommandLine.Option(
                names = ["-h", "--holding-identity-id"],
                arity = "1",
                description = ["ID of the holding identity to be checked."]
            ) holdingIdentityId: String?,
            @CommandLine.Option(
                names = ["-cn"],
                arity = "0..1",
                description = ["Optional. Common Name (CN) attribute of the X.500 name to filter members by."]
            ) commonName: String?,
            @CommandLine.Option(
                names = ["-ou"],
                arity = "0..1",
                description = ["Optional. Organisation Unit (OU) attribute of the X.500 name to filter members by."]
            ) organisationUnit: String?,
            @CommandLine.Option(
                names = ["-o"],
                arity = "0..1",
                description = ["Optional. Organisation (O) attribute of the X.500 name to filter members by."]
            ) organisation: String?,
            @CommandLine.Option(
                names = ["-l"],
                arity = "0..1",
                description = ["Optional. Locality (L) attribute of the X.500 name to filter members by."]
            ) locality: String?,
            @CommandLine.Option(
                names = ["-st"],
                arity = "0..1",
                description = ["Optional. State (ST) attribute of the X.500 name to filter members by."]
            ) state: String?,
            @CommandLine.Option(
                names = ["-c"],
                arity = "0..1",
                description = ["Optional. Country (C) attribute of the X.500 name to filter members by."]
            ) country: String?
        ) {
            require(holdingIdentityId != null)
            val params = mapOf(
                "cn" to commonName,
                "ou" to organisationUnit,
                "o" to organisation,
                "l" to locality,
                "st" to state,
                "c" to country
            )

            val url = "/members/$holdingIdentityId" + params.filter {
                it.value != null
            }.entries.mapIndexed { index, entry ->
                val prefix = if (index == 0) "?" else ""
                prefix + "${entry.key}=${entry.value}"
            }.joinToString("&")

            service.get(url)
        }
    }
}
