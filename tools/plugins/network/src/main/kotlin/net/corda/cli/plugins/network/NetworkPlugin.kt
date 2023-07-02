package net.corda.cli.plugins.network

import net.corda.cli.api.CordaCliPlugin
import net.corda.cli.plugins.common.RestClientUtils.createRestClient
import net.corda.cli.plugins.common.RestCommand
import net.corda.membership.rest.v1.MemberLookupRestResource
import net.corda.membership.rest.v1.types.response.RestMemberInfo
import picocli.CommandLine

class NetworkPlugin : RestCommand(), CordaCliPlugin {

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

        var result: List<RestMemberInfo>
        createRestClient(MemberLookupRestResource::class).use {
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
                    NetworkPluginWrapper.logger.error(e.stackTrace.toString())
                    return
                }
            }
        }
        println(result)
    }
}