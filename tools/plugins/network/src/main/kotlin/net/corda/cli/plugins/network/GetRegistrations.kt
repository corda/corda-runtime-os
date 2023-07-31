package net.corda.cli.plugins.network

import net.corda.cli.plugins.common.RestClientUtils.createRestClient
import net.corda.membership.rest.v1.MemberRegistrationRestResource
import net.corda.membership.rest.v1.types.response.RestRegistrationRequestStatus
import picocli.CommandLine
import net.corda.cli.plugins.common.RestCommand
import net.corda.cli.plugins.network.output.ConsoleOutput
import net.corda.cli.plugins.network.output.Output
import net.corda.cli.plugins.network.utils.InvariantUtils.checkInvariant
import net.corda.cli.plugins.network.utils.PrintUtils.Companion.printJsonOutput
import net.corda.cli.plugins.network.utils.PrintUtils.Companion.verifyAndPrintError
import net.corda.rest.exception.ResourceNotFoundException
import net.corda.rest.exception.ServiceUnavailableException
import net.corda.virtualnode.HoldingIdentity
import net.corda.v5.base.types.MemberX500Name
import java.io.File

@CommandLine.Command(
    name = "get-registrations",
    description = ["Check the status of a registration request."]
)
class GetRegistrations(private val output: Output = ConsoleOutput()) : RestCommand(),
    Runnable {

    @CommandLine.Option(
        names = ["-h", "--holding-identity-short-hash"],
        arity = "1",
        description = [
            "Short hash of the holding identity whose view of the registration progress is to be checked.",
            "Alternatively, you can use --name (optionally with --group) instead of this option."
        ]
    )
    var holdingIdentityShortHash: String? = null

    @CommandLine.Option(
        names = ["-n", "--name"],
        arity = "1",
        description = ["X.500 name of the holding identity whose view of the registration progress is to be checked."]
    )
    var name: String? = null

    @CommandLine.Option(
        names = ["-g", "--group"],
        arity = "1",
        description = ["Group ID of holding identity performing the lookup. " +
                "Required if running this command with X.500 name. " +
                "Defaults to last created group."]
    )
    var group: String? = null

    @CommandLine.Option(
        names = ["--request-id"],
        arity = "1",
        description = ["ID of the registration request. Returns all visible requests if not specified."]
    )
    var requestId: String? = null

    private fun getRegistrations(): List<RestRegistrationRequestStatus> {
        return createRestClient(MemberRegistrationRestResource::class).use { client ->
            checkInvariant(
                maxAttempts = MAX_ATTEMPTS,
                waitInterval = WAIT_INTERVAL,
                errorMessage = "Could not find the specified registration " +
                        "request(s) for member '$holdingIdentityShortHash'."
            ) {
                try {
                    val registrationProxy = client.start().proxy
                    val holdingIdentity = getHoldingIdentity()
                    if (requestId != null) {
                        listOf(
                            registrationProxy.checkSpecificRegistrationProgress(
                                holdingIdentity,
                                requestId!!
                            )
                        )
                    } else {
                        registrationProxy.checkRegistrationProgress(holdingIdentity)
                    }
                } catch (e: ResourceNotFoundException) {
                    null
                } catch (e: ServiceUnavailableException) {
                    null
                }
            }
        }
    }

    private fun getHoldingIdentity(): String {
        return holdingIdentityShortHash ?: name?.let {
            val x500Name = MemberX500Name.parse(it)
            val holdingIdentity = group?.let { group ->
                HoldingIdentity(x500Name, group)
            } ?: HoldingIdentity(x500Name, readDefaultGroup())
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
        verifyAndPrintError {
            val result = getRegistrations()
            printJsonOutput(result, output)
        }
    }
}