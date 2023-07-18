package net.corda.cli.plugins.network

import net.corda.cli.plugins.common.RestClientUtils.createRestClient
import net.corda.cli.plugins.common.RestClientUtils.executeWithRetry
import net.corda.membership.rest.v1.MemberRegistrationRestResource
import net.corda.membership.rest.v1.types.response.RestRegistrationRequestStatus
import picocli.CommandLine
import java.time.Duration
import java.time.temporal.ChronoUnit
import net.corda.cli.plugins.common.RestCommand

@CommandLine.Command(
    name = "check-registration-status",
    description = ["Check the status of a registration request."]
)
class CheckRegistrationStatus : RestCommand(), Runnable {

    @CommandLine.Option(
        names = ["-h", "--holding-identity-short-hash"],
        arity = "1",
        description = ["Short hash of the holding identity whose view of the registration progress is to be checked."]
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
        description = ["Group ID of holding identity performing the lookup. Required if running this command with X.500 name. Defaults to last created group."]
    )
    var group: String? = null

    @CommandLine.Option(
        names = ["--request-id"],
        arity = "1",
        description = ["ID of the registration request. Returns all visible requests if not specified."]
    )
    var requestId: String? = null

    private val waitDuration: Duration = Duration.of(300, ChronoUnit.SECONDS)

    private fun checkRegistrationStatus() {
        val results: List<RestRegistrationRequestStatus> =
            createRestClient(MemberRegistrationRestResource::class).use { client ->
                executeWithRetry(waitDuration, "Registration status check") {
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
                }
            }

        results.forEach { result ->
            println("Registration Id: ${result.registrationId}")
            println("Registration Sent: ${result.registrationSent}")
            println("Registration Updated: ${result.registrationUpdated}")
            println("Registration Status: ${result.registrationStatus}")
            println("Member Info Submitted: ${result.memberInfoSubmitted}")
            println("Reason: ${result.reason}")
            println("Serial: ${result.serial}")
            println("------")
        }
    }

    override fun run() {
        checkRegistrationStatus()
    }
}
