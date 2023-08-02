package net.corda.cli.plugins.network

import net.corda.cli.plugins.common.RestClientUtils.createRestClient
import net.corda.cli.plugins.common.RestCommand
import net.corda.cli.plugins.network.output.ConsoleOutput
import net.corda.membership.rest.v1.MemberLookupRestResource
import net.corda.membership.rest.v1.types.response.RestMemberInfo
import net.corda.cli.plugins.network.output.Output
import net.corda.cli.plugins.network.utils.PrintUtils.Companion.printJsonOutput
import net.corda.v5.base.types.MemberX500Name
import picocli.CommandLine
import java.io.File

@CommandLine.Command(name = "members", description = ["Shows the list of members on the network."])
class Members(private val output: Output = ConsoleOutput()) : RestCommand(), Runnable {

    @CommandLine.Option(
        names = ["-h", "--holding-identity-short-hash"],
        arity = "1",
        description = ["Short hash of the holding identity to be checked."]
    )
    var holdingIdentityShortHash: String? = null

    @CommandLine.Option(
        names = ["-g", "--group"],
        arity = "1",
        description = ["Group ID of holding identity performing the lookup. " +
                "Required if running this command with X.500 name. Defaults to last created group."]
    )
    var group: String? = null

    @CommandLine.Option(
        names = ["-n"],
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

    @CommandLine.Option(
        names = ["-s", "--status"],
        arity = "1..*",
        description = ["Status (\"ACTIVE\", \"SUSPENDED\") to filter members by. " +
                "Multiple -s arguments may be provided. By default, only ACTIVE members are filtered. " +
                "Only an MGM can view suspended members."]
    )
    var status: List<String>? = null

    private fun performMembersLookup(): List<RestMemberInfo> {
        val result: List<RestMemberInfo> = createRestClient(MemberLookupRestResource::class).use { client ->
            val memberLookupProxy = client.start().proxy
            memberLookupProxy.lookup(
                getHoldingIdentity(),
                commonName,
                organization,
                organizationUnit,
                locality,
                state,
                country,
                status.orEmpty()
            ).members
        }

        return result
    }

    private fun getHoldingIdentity(): String {
        return holdingIdentityShortHash ?: commonName?.let {
            val x500Name = MemberX500Name.parse(it)
            val holdingIdentity = group?.let { group ->
                net.corda.virtualnode.HoldingIdentity(x500Name, group)
            } ?: net.corda.virtualnode.HoldingIdentity(x500Name, readDefaultGroup())
            holdingIdentity.shortHash.toString()
        } ?: throw IllegalArgumentException("Either 'holdingIdentityShortHash' or 'name' must be specified.")
    }

    private fun readDefaultGroup(): String {
        val groupIdFile = File(
            File(File(File(System.getProperty("user.home")), ".corda"), "groupId"),
            "groupId.txt"
        )
        return if (groupIdFile.exists()) {
            groupIdFile.readText().trim()
        } else {
            throw IllegalArgumentException("Group ID was not specified, and the last created group could not be found.")
        }
    }

    override fun run() {
        val result = performMembersLookup()
        printJsonOutput(result, output)
    }
}