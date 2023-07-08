package net.corda.cli.plugins.network

import net.corda.cli.plugins.common.RestClientUtils.createRestClient
import net.corda.cli.plugins.common.RestCommand
import net.corda.membership.rest.v1.MemberLookupRestResource
import net.corda.membership.rest.v1.types.response.RestMemberInfo
import picocli.CommandLine

data class MembersListOptions(
    val holdingIdentityShortHash: String?,
    val commonName: String?,
    val organizationUnit: String?,
    val organization: String?,
    val locality: String?,
    val state: String?,
    val country: String?
)

@CommandLine.Command(name = "members-list", description = ["Shows the list of members on the network."])
class MemberList : RestCommand(), Runnable {
    override fun run() {
        val options = MembersListOptions(
            holdingIdentityShortHash = holdingIdentityShortHash,
            commonName = commonName,
            organizationUnit = organizationUnit,
            organization = organization,
            locality = locality,
            state = state,
            country = country
        )
        val result = performMembersLookup(options)
        println(result)
    }

    private fun performMembersLookup(options: MembersListOptions): List<RestMemberInfo> {
        require(options.holdingIdentityShortHash != null) { "Holding identity short hash was not provided." }

        createRestClient(MemberLookupRestResource::class).use { restClient ->
            val connection = restClient.start()
            with(connection.proxy) {
                try {
                    return lookup(
                        options.holdingIdentityShortHash,
                        options.commonName,
                        options.organization,
                        options.organizationUnit,
                        options.locality,
                        options.state,
                        options.country
                    ).members
                } catch (e: Exception) {
                    println(e.message)
                    NetworkPluginWrapper.logger.error(e.stackTrace.toString())
                }
            }
        }
        return emptyList()
    }

    @CommandLine.Option(
        names = ["-h", "--holding-identity-short-hash"],
        arity = "1",
        description = ["Short hash of the holding identity to be checked."]
    )
    private var holdingIdentityShortHash: String? = null

    @CommandLine.Option(
        names = ["-cn"],
        arity = "0..1",
        description = ["Optional. Common Name (CN) attribute of the X.500 name to filter members by."]
    )
    private var commonName: String? = null

    @CommandLine.Option(
        names = ["-ou"],
        arity = "0..1",
        description = ["Optional. Organization Unit (OU) attribute of the X.500 name to filter members by."]
    )
    private var organizationUnit: String? = null

    @CommandLine.Option(
        names = ["-o"],
        arity = "0..1",
        description = ["Optional. Organization (O) attribute of the X.500 name to filter members by."]
    )
    private var organization: String? = null

    @CommandLine.Option(
        names = ["-l"],
        arity = "0..1",
        description = ["Optional. Locality (L) attribute of the X.500 name to filter members by."]
    )
    private var locality: String? = null

    @CommandLine.Option(
        names = ["-st"],
        arity = "0..1",
        description = ["Optional. State (ST) attribute of the X.500 name to filter members by."]
    )
    private var state: String? = null

    @CommandLine.Option(
        names = ["-c"],
        arity = "0..1",
        description = ["Optional. Country (C) attribute of the X.500 name to filter members by."]
    )
    private var country: String? = null
}