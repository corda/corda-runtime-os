package net.corda.cli.plugins.network

import net.corda.cli.plugins.common.RestCommand
import net.corda.cli.plugins.network.output.ConsoleOutput
import net.corda.cli.plugins.network.output.Output
import net.corda.cli.plugins.network.utils.HoldingIdentityUtils.getHoldingIdentity
import net.corda.cli.plugins.network.utils.PrintUtils.printJsonOutput
import net.corda.cli.plugins.network.utils.PrintUtils.verifyAndPrintError
import net.corda.cli.plugins.typeconverter.RequestIdConverter
import net.corda.cli.plugins.typeconverter.ShortHashConverter
import net.corda.cli.plugins.typeconverter.X500NameConverter
import net.corda.crypto.core.ShortHash
import net.corda.membership.rest.v1.MemberRegistrationRestResource
import net.corda.membership.rest.v1.types.response.RestRegistrationRequestStatus
import net.corda.sdk.data.RequestId
import net.corda.sdk.network.RegistrationsLookup
import net.corda.sdk.rest.RestClientUtils.createRestClient
import net.corda.v5.base.types.MemberX500Name
import picocli.CommandLine
import kotlin.time.Duration.Companion.seconds

@CommandLine.Command(
    name = "get-registrations",
    description = ["Check the status of a registration request."],
    mixinStandardHelpOptions = true,
)
class GetRegistrations(private val output: Output = ConsoleOutput()) :
    RestCommand(),
    Runnable {

    @CommandLine.Option(
        names = ["-h", "--holding-identity-short-hash"],
        arity = "1",
        description = [
            "Short hash of the holding identity whose view of the registration progress is to be checked.",
            "Alternatively, you can use --name (optionally with --group).",
        ],
        converter = [ShortHashConverter::class],
    )
    var holdingIdentityShortHash: ShortHash? = null

    @CommandLine.Option(
        names = ["-n", "--name"],
        arity = "1",
        description = ["X.500 name of the holding identity whose view of the registration progress is to be checked."],
        converter = [X500NameConverter::class],
    )
    var name: MemberX500Name? = null

    @CommandLine.Option(
        names = ["-g", "--group"],
        arity = "1",
        description = [
            "Group ID of holding identity performing the lookup. " +
                "Required if running this command with X.500 name. " +
                "Defaults to last created group.",
        ],
    )
    var group: String? = null

    @CommandLine.Option(
        names = ["--request-id"],
        arity = "1",
        description = ["ID of the registration request. Returns all visible requests if not specified."],
        converter = [RequestIdConverter::class],
    )
    var requestId: RequestId? = null

    private fun getRegistrations(): List<RestRegistrationRequestStatus> {
        val holdingIdentity = getHoldingIdentity(holdingIdentityShortHash, name, group)
        val restClient = createRestClient(
            MemberRegistrationRestResource::class,
            insecure = insecure,
            minimumServerProtocolVersion = minimumServerProtocolVersion,
            username = username,
            password = password,
            targetUrl = targetUrl
        )
        return if (requestId != null) {
            listOf(RegistrationsLookup().checkRegistration(restClient, holdingIdentity, requestId!!, waitDurationSeconds.seconds))
        } else {
            RegistrationsLookup().checkRegistrations(restClient, holdingIdentity, waitDurationSeconds.seconds)
        }
    }

    override fun run() {
        verifyAndPrintError {
            printJsonOutput(getRegistrations(), output)
        }
    }
}
