package net.corda.cli.plugins.network

import net.corda.cli.plugins.common.RestCommand
import net.corda.cli.plugins.network.utils.PrintUtils.verifyAndPrintError
import net.corda.membership.rest.v1.MGMRestResource
import net.corda.sdk.network.ClientCertificates
import net.corda.sdk.rest.RestClientUtils
import picocli.CommandLine.Command
import picocli.CommandLine.Parameters
import kotlin.time.Duration.Companion.seconds

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

        val restClient = RestClientUtils.createRestClient(
            MGMRestResource::class,
            insecure = insecure,
            minimumServerProtocolVersion = minimumServerProtocolVersion,
            username = username,
            password = password,
            targetUrl = targetUrl
        )
        val clientCertificates = ClientCertificates()

        println("Allowing certificates...")
        clientCertificates.allowMutualTlsForSubjects(restClient, mgmShortHash, subjects, waitDurationSeconds.seconds)
        println("Success!")
        clientCertificates.listMutualTlsClientCertificates(restClient, mgmShortHash, waitDurationSeconds.seconds).forEach { subject ->
            println("Certificate with subject $subject is allowed")
        }
    }
}
