package net.corda.cli.commands.network

import net.corda.cli.commands.common.RestCommand
import net.corda.cli.commands.network.utils.PrintUtils.verifyAndPrintError
import net.corda.cli.commands.typeconverter.ShortHashConverter
import net.corda.cli.commands.typeconverter.X500NameConverter
import net.corda.crypto.core.ShortHash
import net.corda.membership.rest.v1.MGMRestResource
import net.corda.sdk.network.ClientCertificates
import net.corda.sdk.rest.RestClientUtils
import net.corda.v5.base.types.MemberX500Name
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
