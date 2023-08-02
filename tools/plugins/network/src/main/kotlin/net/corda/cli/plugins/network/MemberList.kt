package net.corda.cli.plugins.network

import net.corda.cli.plugins.common.RestClientUtils.createRestClient
import net.corda.cli.plugins.common.RestCommand
import net.corda.cli.plugins.network.output.ConsoleOutput
import net.corda.membership.rest.v1.MemberLookupRestResource
import net.corda.membership.rest.v1.types.response.RestMemberInfo
import net.corda.cli.plugins.network.output.Output
import net.corda.cli.plugins.network.utils.PrintUtils.Companion.printJsonOutput
import picocli.CommandLine

@CommandLine.Command(name = "members-list", description = ["Shows the list of members on the network."])
class MemberList(private val output: Output = ConsoleOutput()) : RestCommand(), Runnable {

    @CommandLine.Option(
        names = ["-h", "--holding-identity-short-hash"],
        arity = "1",
        description = ["Short hash of the holding identity to be checked."]
    )
    var holdingIdentityShortHash: String? = null

    @CommandLine.Option(
        names = ["-cn"],
        arity = "0..1",
        description = ["Optional. Common Name (CN) attribute of the X.500 name to filter members by."]
    )
    var commonName: String? = null

    @CommandLine.Option(
        names = ["-ou"],
        arity = "0..1",
        description = ["Optional. Organization Unit (OU) attribute of the X.500 name to filter members by."]
    )
    var organizationUnit: String? = null

    @CommandLine.Option(
        names = ["-o"],
        arity = "0..1",
        description = ["Optional. Organization (O) attribute of the X.500 name to filter members by."]
    )
    var organization: String? = null

    @CommandLine.Option(
        names = ["-l"],
        arity = "0..1",
        description = ["Optional. Locality (L) attribute of the X.500 name to filter members by."]
    )
    var locality: String? = null

    @CommandLine.Option(
        names = ["-st"],
        arity = "0..1",
        description = ["Optional. State (ST) attribute of the X.500 name to filter members by."]
    )
    var state: String? = null

    @CommandLine.Option(
        names = ["-c"],
        arity = "0..1",
        description = ["Optional. Country (C) attribute of the X.500 name to filter members by."]
    )
    var country: String? = null

    private fun performMembersLookup(): List<RestMemberInfo> {
        requireNotNull(holdingIdentityShortHash) { "Holding identity short hash was not provided." }

        val result: List<RestMemberInfo> = createRestClient(MemberLookupRestResource::class).use { client ->
            val memberLookupProxy = client.start().proxy
            memberLookupProxy.lookup(
                holdingIdentityShortHash.toString(),
                commonName,
                organization,
                organizationUnit,
                locality,
                state,
                country
            ).members
        }

        return result
    }

    override fun run() {
        val result = performMembersLookup()
        printJsonOutput(result, output)
    }
}