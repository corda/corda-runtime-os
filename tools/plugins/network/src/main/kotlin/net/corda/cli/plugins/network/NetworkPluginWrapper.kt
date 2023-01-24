package net.corda.cli.plugins.network

import net.corda.cli.api.CordaCliPlugin
import net.corda.cli.plugins.common.HttpRpcClientUtils.createHttpRpcClient
import net.corda.cli.plugins.common.HttpRpcCommand
import net.corda.membership.httprpc.v1.MemberLookupRestResource
import net.corda.membership.httprpc.v1.types.response.RpcMemberInfo
import org.pf4j.Extension
import org.pf4j.Plugin
import org.pf4j.PluginWrapper
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import picocli.CommandLine

@Suppress("unused")
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
    class NetworkPlugin : HttpRpcCommand(), CordaCliPlugin {

        @Suppress("LongParameterList")
        @CommandLine.Command(
            name = "members-list",
            description = ["Shows the list of members on the network."]
        )
        fun getMembersList(
            @CommandLine.Option(
                names = ["-h", "--holding-identity-short-hash"],
                arity = "1",
                description = ["Short hash of the holding identity to be checked."]
            ) holdingIdentityShortHash: String?,
            @CommandLine.Option(
                names = ["-cn"],
                arity = "0..1",
                description = ["Optional. Common Name (CN) attribute of the X.500 name to filter members by."]
            ) commonName: String?,
            @CommandLine.Option(
                names = ["-ou"],
                arity = "0..1",
                description = ["Optional. Organization Unit (OU) attribute of the X.500 name to filter members by."]
            ) organizationUnit: String?,
            @CommandLine.Option(
                names = ["-o"],
                arity = "0..1",
                description = ["Optional. Organization (O) attribute of the X.500 name to filter members by."]
            ) organization: String?,
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
            require(holdingIdentityShortHash != null) { "Holding identity short hash was not provided." }

            var result: List<RpcMemberInfo>
            createHttpRpcClient(MemberLookupRestResource::class).use {
                val connection = it.start()
                with(connection.proxy) {
                    try {
                        result = lookup(
                            holdingIdentityShortHash,
                            commonName,
                            organization,
                            organizationUnit,
                            locality,
                            state,
                            country
                        ).members
                    } catch (e: Exception) {
                        println(e.message)
                        logger.error(e.stackTrace.toString())
                        return
                    }
                }
            }
            println(result)
        }
    }
}
