package net.corda.cli.plugins.network

import net.corda.cli.plugins.common.RestCommand
import net.corda.cli.plugins.network.output.ConsoleOutput
import net.corda.cli.plugins.network.output.Output
import net.corda.cli.plugins.network.utils.HoldingIdentityUtils.getHoldingIdentity
import net.corda.cli.plugins.network.utils.PrintUtils.printJsonOutput
import net.corda.cli.plugins.network.utils.PrintUtils.verifyAndPrintError
import net.corda.cli.plugins.typeconverter.ShortHashConverter
import net.corda.cli.plugins.typeconverter.X500NameConverter
import net.corda.crypto.core.ShortHash
import net.corda.membership.rest.v1.types.response.RestMemberInfo
import net.corda.restclient.CordaRestClient
import net.corda.sdk.network.MemberLookup
import net.corda.v5.base.types.MemberX500Name
import picocli.CommandLine
import java.net.URI
import kotlin.time.Duration.Companion.seconds

@CommandLine.Command(
    name = "members",
    description = ["Shows the list of members on the network."],
    mixinStandardHelpOptions = true,
)
class MemberLookup(private val output: Output = ConsoleOutput()) : RestCommand(), Runnable {

    @CommandLine.Option(
        names = ["-h", "--holding-identity-short-hash"],
        arity = "1",
        description = ["Short hash of the holding identity performing the lookup."],
        converter = [ShortHashConverter::class],
    )
    var holdingIdentityShortHash: ShortHash? = null

    @CommandLine.Option(
        names = ["-g", "--group"],
        arity = "1",
        description = [
            "Group ID of holding identity performing the lookup. " +
                "Required if running this command with X.500 name. Defaults to last created group.",
        ],
    )
    var group: String? = null

    @CommandLine.Option(
        names = ["-n", "--name"],
        arity = "0..1",
        description = ["X.500 name of the holding identity performing the lookup"],
        converter = [X500NameConverter::class],
    )
    var name: MemberX500Name? = null

    @CommandLine.Option(
        names = ["-cn"],
        arity = "0..1",
        description = ["Optional. Common Name (CN) attribute of the X.500 name to filter members by."],
    )
    var commonName: String? = null

    @CommandLine.Option(
        names = ["-ou"],
        arity = "0..1",
        description = ["Optional. Organization Unit (OU) attribute of the X.500 name to filter members by."],
    )
    var organizationUnit: String? = null

    @CommandLine.Option(
        names = ["-o"],
        arity = "0..1",
        description = ["Optional. Organization (O) attribute of the X.500 name to filter members by."],
    )
    var organization: String? = null

    @CommandLine.Option(
        names = ["-l"],
        arity = "0..1",
        description = ["Optional. Locality (L) attribute of the X.500 name to filter members by."],
    )
    var locality: String? = null

    @CommandLine.Option(
        names = ["-st"],
        arity = "0..1",
        description = ["Optional. State (ST) attribute of the X.500 name to filter members by."],
    )
    var state: String? = null

    @CommandLine.Option(
        names = ["-c"],
        arity = "0..1",
        description = ["Optional. Country (C) attribute of the X.500 name to filter members by."],
    )
    var country: String? = null

    @CommandLine.Option(
        names = ["-s", "--status"],
        arity = "1..*",
        description = [
            "Status (\"ACTIVE\", \"SUSPENDED\") to filter members by. " +
                "Multiple -s arguments may be provided. By default, only ACTIVE members are filtered. " +
                "Only an MGM can view suspended members.",
        ],
    )
    var status: List<String>? = null

    private fun performMembersLookup(): List<RestMemberInfo> {
        val restClient = CordaRestClient.createHttpClient(
            baseUrl = URI.create(targetUrl),
            username = username,
            password = password,
            insecure = insecure
        )
        val holdingIdentity = getHoldingIdentity(holdingIdentityShortHash, name, group)
        return MemberLookup(restClient).lookupMember(
            holdingIdentity,
            commonName,
            organization,
            organizationUnit,
            locality,
            state,
            country,
            status.orEmpty(),
            waitDurationSeconds.seconds
        ).members
    }

    override fun run() {
        verifyAndPrintError {
            printJsonOutput(performMembersLookup(), output)
        }
    }
}
