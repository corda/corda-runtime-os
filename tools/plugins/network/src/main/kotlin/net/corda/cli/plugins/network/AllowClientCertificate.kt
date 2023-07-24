package net.corda.cli.plugins.network

import net.corda.cli.plugins.common.RestClientUtils.createRestClient
import net.corda.cli.plugins.common.RestCommand
import net.corda.membership.rest.v1.MGMRestResource
import net.corda.cli.plugins.network.utils.InvariantUtils.checkInvariant
import picocli.CommandLine.Command
import picocli.CommandLine.Parameters

@Command(
    name = "allow-client-certificate",
    description = [
        "Allow client certificate in mutual TLS.",
        "This sub command should only be used for internal development"
    ]
)
class AllowClientCertificate : Runnable, RestCommand() {
    @Parameters(
        description = ["The MGM holding identity short hash"],
        paramLabel = "MGM_HASH",
        index = "0"
    )
    lateinit var mgmShortHash: String

    @Parameters(
        description = ["The certificate subject to allow"],
        paramLabel = "SUBJECTS",
        index = "1..*"
    )
    var subjects: Collection<String> = emptyList()

    override fun run() {
        allowAndListCertificates()
    }

    private fun allowAndListCertificates() {
        if (subjects.isEmpty()) {
            println("No subjects to allow")
            return
        }

        val mgm = createRestClient(MGMRestResource::class).start().proxy

        checkInvariant(
            maxAttempts = MAX_ATTEMPTS,
            waitInterval = WAIT_INTERVAL,
            errorMessage = "Allow and list certificates: Invariant check failed after maximum attempts."
        ) {
            println("Allowing certificates...")
            subjects.forEach { subject ->
                println("\t Allowing $subject")
                mgm.mutualTlsAllowClientCertificate(mgmShortHash, subject)
            }
            println("Success!")
            true // Return true to indicate the invariant is satisfied
        }

        checkInvariant(
            maxAttempts = MAX_ATTEMPTS,
            waitInterval = WAIT_INTERVAL,
            errorMessage = "Allow and list certificates: Invariant check failed after maximum attempts."
        ) {
            mgm.mutualTlsListClientCertificate(mgmShortHash).forEach { subject ->
                println("Certificate with subject $subject is allowed")
            }
            true // Return true to indicate the invariant is satisfied
        }
    }
}
