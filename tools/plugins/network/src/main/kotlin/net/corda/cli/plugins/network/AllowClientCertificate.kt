package net.corda.cli.plugins.network

import net.corda.cli.plugins.common.RestCommand
import net.corda.cli.plugins.network.utils.PrintUtils.verifyAndPrintError
import net.corda.cli.plugins.typeconverter.ShortHashConverter
import net.corda.cli.plugins.typeconverter.X500NameConverter
import net.corda.crypto.core.ShortHash
import net.corda.restclient.CordaRestClient
import net.corda.sdk.network.ClientCertificates
import net.corda.v5.base.types.MemberX500Name
import picocli.CommandLine.Command
import picocli.CommandLine.Parameters
import java.net.URI
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
        converter = [ShortHashConverter::class],
    )
    lateinit var mgmShortHash: ShortHash

    @Parameters(
        description = ["The certificate subject to allow"],
        paramLabel = "SUBJECTS",
        index = "1..*",
        converter = [X500NameConverter::class],
    )
    var subjects: Collection<MemberX500Name> = emptyList()

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

        val restClient = CordaRestClient.createHttpClient(
            baseUrl = URI.create(targetUrl),
            username = username,
            password = password,
            insecure = insecure
        )
        val clientCertificates = ClientCertificates(restClient)

        println("Allowing certificates...")
        clientCertificates.allowMutualTlsForSubjects(
            holdingIdentityShortHash = mgmShortHash,
            subjects = subjects,
            wait = waitDurationSeconds.seconds
        )
        println("Success!")
        clientCertificates.listMutualTlsClientCertificates(
            holdingIdentityShortHash = mgmShortHash,
            wait = waitDurationSeconds.seconds
        ).forEach { subject ->
            println("Certificate with subject $subject is allowed")
        }
    }
}
