package net.corda.cli.plugins.network

import net.corda.cli.plugins.common.RestClientUtils.createRestClient
import net.corda.membership.rest.v1.MemberRegistrationRestResource
import net.corda.membership.rest.v1.types.response.RestRegistrationRequestStatus
import picocli.CommandLine
import net.corda.cli.plugins.common.RestCommand
import net.corda.cli.plugins.network.utils.InvariantUtils.checkInvariant
import net.corda.cli.plugins.network.utils.PrintUtils.Companion.printJsonOutput

@CommandLine.Command(
    name = "get-registrations",
    description = ["Check the status of a registration request."]
)
class GetRegistrations(private val output: GetRegistrationsOutput = ConsoleGetRegistrationsOutput()) : RestCommand(),
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
                    if (requestId != null) {
                        listOf(
                            registrationProxy.checkSpecificRegistrationProgress(
                                holdingIdentityShortHash!!,
                                requestId!!
                            )
                        )
                    } else {
                        registrationProxy.checkRegistrationProgress(holdingIdentityShortHash!!)
                    }
                } catch (e: Exception) {
                    null
                }
            }
        }
    }

    interface GetRegistrationsOutput {
        fun generateOutput(content: String)
    }

    class ConsoleGetRegistrationsOutput : GetRegistrationsOutput {
        /**
         * Receives the content of the file and prints it to the console output. It makes the testing easier.
         */
        override fun generateOutput(content: String) {
            content.lines().forEach {
                println(it)
            }
        }
    }

    override fun run() {
        val result = getRegistrations()
        if (group != null) {
            println(group)
        }
        printJsonOutput(result, output)
    }
}
