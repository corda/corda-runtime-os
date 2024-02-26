@file:Suppress("DEPRECATION")

package net.corda.sdk.network

import net.corda.membership.rest.v1.CertificatesRestResource
import net.corda.membership.rest.v1.MGMRestResource
import net.corda.rest.HttpFileUpload
import net.corda.rest.client.RestClient
import net.corda.sdk.network.Keys.Companion.P2P_TLS_CERTIFICATE_ALIAS
import net.corda.sdk.rest.InvariantUtils.MAX_ATTEMPTS
import net.corda.sdk.rest.InvariantUtils.checkInvariant
import org.bouncycastle.openssl.PEMParser
import org.bouncycastle.pkcs.PKCS10CertificationRequest
import java.io.ByteArrayInputStream
import java.security.InvalidKeyException

class ClientCertificates {

    fun allowMutualTlsForSubjects(restClient: RestClient<MGMRestResource>, holdingIdentityShortHash: String, subjects: Collection<String>) {
        restClient.use { client ->
            checkInvariant(
                errorMessage = "Failed to allow mutual TLS certificates after $MAX_ATTEMPTS attempts.",
            ) {
                try {
                    val resource = client.start().proxy
                    subjects.forEach { subject ->
                        resource.mutualTlsAllowClientCertificate(holdingIdentityShortHash, subject)
                    }
                } catch (e: Exception) {
                    null
                }
            }
        }
    }

    fun listMutualTlsClientCertificates(restClient: RestClient<MGMRestResource>, holdingIdentityShortHash: String): Collection<String> {
        return restClient.use { client ->
            checkInvariant(
                errorMessage = "Failed to list mutual TLS certificates after $MAX_ATTEMPTS attempts.",
            ) {
                try {
                    val resource = client.start().proxy
                    resource.mutualTlsListClientCertificate(holdingIdentityShortHash)
                } catch (e: Exception) {
                    null
                }
            }
        }
    }

    fun generateP2pCsr(
        restClient: RestClient<CertificatesRestResource>,
        tlsKeyId: String,
        subjectX500Name: String,
        p2pHostNames: List<String>
    ): PKCS10CertificationRequest {
        val csr = restClient.use { client ->
            checkInvariant(
                errorMessage = "Failed generate CSR after $MAX_ATTEMPTS attempts."
            ) {
                try {
                    val resource = client.start().proxy
                    resource.generateCsr(
                        tenantId = "p2p",
                        keyId = tlsKeyId,
                        x500Name = subjectX500Name,
                        subjectAlternativeNames = p2pHostNames,
                        contextMap = null,
                    )
                } catch (e: Exception) {
                    null
                }
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
        certificate: ByteArrayInputStream
    ) {
        uploadCertificate(restClient, certificate, "p2p-tls", P2P_TLS_CERTIFICATE_ALIAS)
    }

    fun uploadCertificate(
        restClient: RestClient<CertificatesRestResource>,
        certificate: ByteArrayInputStream,
        usage: String,
        alias: String
    ) {
        restClient.use { client ->
            checkInvariant(
                errorMessage = "Failed upload certificate after $MAX_ATTEMPTS attempts."
            ) {
                try {
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
                } catch (e: Exception) {
                    null
                }
            }
        }
    }
}
