package net.corda.cli.plugins.network

import net.corda.cli.plugins.common.RestCommand
import net.corda.cli.plugins.network.output.ConsoleOutput
import net.corda.cli.plugins.network.output.Output
import net.corda.cli.plugins.network.utils.HoldingIdentityUtils.getHoldingIdentity
import net.corda.cli.plugins.network.utils.PrintUtils.printJsonOutput
import net.corda.cli.plugins.network.utils.PrintUtils.verifyAndPrintError
import net.corda.membership.rest.v1.MemberLookupRestResource
import net.corda.membership.rest.v1.types.RestGroupParameters
import net.corda.sdk.network.GroupParametersLookup
import net.corda.sdk.rest.RestClientUtils.createRestClient
import picocli.CommandLine

@CommandLine.Command(
    name = "group-parameters",
    description = ["Look up group parameters."],
    mixinStandardHelpOptions = true,
)
class GroupParametersLookup(private val output: Output = ConsoleOutput()) : RestCommand(), Runnable {

    @CommandLine.Option(
        names = ["-h", "--holding-identity-short-hash"],
        arity = "1",
        description = ["Short hash of the holding identity performing the lookup."],
    )
    var holdingIdentityShortHash: String? = null

    @CommandLine.Option(
        names = ["-n", "--name"],
        arity = "1",
        description = ["X.500 name of the holding identity performing the lookup."],
    )
    var name: String? = null

    @CommandLine.Option(
        names = ["-g", "--group"],
        arity = "1",
        description = [
            "Group ID of holding identity performing the lookup. " +
                "Required if running this command with X.500 name. Defaults to last created group.",
        ],
    )
    var group: String? = null

    private fun performGroupParametersLookup(): RestGroupParameters {
        val holdingIdentity = getHoldingIdentity(holdingIdentityShortHash, name, group)
        val restClient = createRestClient(
            MemberLookupRestResource::class,
            insecure = insecure,
            minimumServerProtocolVersion = minimumServerProtocolVersion,
            username = username,
            password = password,
            targetUrl = targetUrl
        )
        return GroupParametersLookup().lookupGroupParameters(restClient, holdingIdentity)
    }

    override fun run() {
        verifyAndPrintError {
            printJsonOutput(performGroupParametersLookup(), output)
        }
    }
}
