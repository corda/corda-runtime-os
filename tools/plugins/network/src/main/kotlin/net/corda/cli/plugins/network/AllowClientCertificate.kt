package net.corda.cli.plugins.network

import net.corda.cli.plugins.common.RestClientUtils.createRestClient
import net.corda.cli.plugins.common.RestCommand
import net.corda.cli.plugins.network.utils.PrintUtils.verifyAndPrintError
import net.corda.membership.rest.v1.MGMRestResource
import picocli.CommandLine.Command
import picocli.CommandLine.Parameters

@Command(
    name = "allow-client-certificate",
    description = [
        "Allow client certificate in mutual TLS.",
    ],
    mixinStandardHelpOptions = true,
)
class AllowClientCertificate : Runnable, RestCommand() {
    @Parameters(
        description = ["The MGM holding identity short hash"],
        paramLabel = "MGM_HASH",
        index = "0",
    )
    lateinit var mgmShortHash: String

    @Parameters(
        description = ["The certificate subject to allow"],
        paramLabel = "SUBJECTS",
        index = "1..*",
    )
    var subjects: Collection<String> = emptyList()

    override fun run() {
        verifyAndPrintError {
            allowAndListCertificates()
        }
    }

    private fun allowAndListCertificates() {
        if (subjects.isEmpty()) {
            println("No subjects to allow")
            return
        }

        createRestClient(MGMRestResource::class).use { it ->

            val proxy = it.start().proxy
            println("Allowing certificates...")

            subjects.forEach { subject ->
                println("\t Allowing $subject")
                proxy.mutualTlsAllowClientCertificate(mgmShortHash, subject)
            }
            println("Success!")

            proxy.mutualTlsListClientCertificate(mgmShortHash).forEach { subject ->
                println("Certificate with subject $subject is allowed")
            }
        }
    }
}
