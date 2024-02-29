@file:Suppress("DEPRECATION")

package net.corda.sdk.network

import net.corda.membership.rest.v1.CertificatesRestResource
import net.corda.membership.rest.v1.MGMRestResource
import net.corda.rest.HttpFileUpload
import net.corda.rest.client.RestClient
import net.corda.sdk.network.Keys.Companion.P2P_TLS_CERTIFICATE_ALIAS
import net.corda.sdk.rest.RestClientUtils.executeWithRetry
import org.bouncycastle.openssl.PEMParser
import org.bouncycastle.pkcs.PKCS10CertificationRequest
import java.io.ByteArrayInputStream
import java.security.InvalidKeyException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class ClientCertificates {

    fun allowMutualTlsForSubjects(
        restClient: RestClient<MGMRestResource>,
        holdingIdentityShortHash: String,
        subjects: Collection<String>,
        wait: Duration = 10.seconds
    ) {
        restClient.use { client ->
            executeWithRetry(
                waitDuration = wait,
                operationName = "Allow mutual TLS certificate"
            ) {
                val resource = client.start().proxy
                subjects.forEach { subject ->
                    resource.mutualTlsAllowClientCertificate(holdingIdentityShortHash, subject)
                }
            }
        }
    }

    fun listMutualTlsClientCertificates(
        restClient: RestClient<MGMRestResource>,
        holdingIdentityShortHash: String,
        wait: Duration = 10.seconds
    ): Collection<String> {
        return restClient.use { client ->
            executeWithRetry(
                waitDuration = wait,
                operationName = "List mutual TLS certificates"
            ) {
                val resource = client.start().proxy
                resource.mutualTlsListClientCertificate(holdingIdentityShortHash)
            }
        }
    }

    fun generateP2pCsr(
        restClient: RestClient<CertificatesRestResource>,
        tlsKeyId: String,
        subjectX500Name: String,
        p2pHostNames: List<String>,
        wait: Duration = 10.seconds
    ): PKCS10CertificationRequest {
        val csr = restClient.use { client ->
            executeWithRetry(
                waitDuration = wait,
                operationName = "Generate CSR"
            ) {
                val resource = client.start().proxy
                resource.generateCsr(
                    tenantId = "p2p",
                    keyId = tlsKeyId,
                    x500Name = subjectX500Name,
                    subjectAlternativeNames = p2pHostNames,
                    contextMap = null,
                )
            }
        }
        return csr.reader().use { reader ->
            PEMParser(reader).use { parser ->
                parser.readObject()
            }
        } as? PKCS10CertificationRequest ?: throw InvalidKeyException("CSR is not a valid CSR: $csr")
    }

    fun uploadTlsCertificate(
        restClient: RestClient<CertificatesRestResource>,
        certificate: ByteArrayInputStream,
        wait: Duration = 10.seconds
    ) {
        uploadCertificate(restClient, certificate, "p2p-tls", P2P_TLS_CERTIFICATE_ALIAS, wait)
    }

    fun uploadCertificate(
        restClient: RestClient<CertificatesRestResource>,
        certificate: ByteArrayInputStream,
        usage: String,
        alias: String,
        wait: Duration = 10.seconds
    ) {
        restClient.use { client ->
            executeWithRetry(
                waitDuration = wait,
                operationName = "Upload certificate $alias"
            ) {
                val resource = client.start().proxy
                resource.importCertificateChain(
                    usage = usage,
                    alias = alias,
                    certificates = listOf(
                        HttpFileUpload(
                            certificate,
                            "certificate.pem",
                        ),
                    )
                )
            }
        }
    }
}
