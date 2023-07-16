package net.corda.cli.plugins.network

import net.corda.cli.plugins.common.RestClientUtils.createRestClient
import net.corda.cli.plugins.common.RestCommand
import net.corda.membership.rest.v1.MGMRestResource
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

        val restClient = createRestClient(MGMRestResource::class)

        restClient.use { client ->
            println("Allowing certificates...")

            client.start().also { connection ->
                val mgm = connection.proxy

                subjects.forEach { subject ->
                    println("\t Allowing $subject")
                    mgm.mutualTlsAllowClientCertificate(mgmShortHash, subject)
                }

                println("Success!")

                mgm.mutualTlsListClientCertificate(mgmShortHash).forEach { subject ->
                    println("Certificate with subject $subject is allowed")
                }
            }
        }
    }
}
