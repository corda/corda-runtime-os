@file:Suppress("DEPRECATION")
// Needed for the v1.CertificatesRestResource import statement

package net.corda.sdk.network

import net.corda.crypto.core.ShortHash
import net.corda.membership.rest.v1.CertificatesRestResource
import net.corda.membership.rest.v1.MGMRestResource
import net.corda.membership.rest.v1.types.response.KeyPairIdentifier
import net.corda.rest.HttpFileUpload
import net.corda.rest.client.RestClient
import net.corda.sdk.network.Keys.Companion.P2P_TLS_CERTIFICATE_ALIAS
import net.corda.sdk.rest.RestClientUtils.executeWithRetry
import net.corda.v5.base.types.MemberX500Name
import org.bouncycastle.openssl.PEMParser
import org.bouncycastle.pkcs.PKCS10CertificationRequest
import java.io.InputStream
import java.security.InvalidKeyException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class ClientCertificates {

    /**
     * Update an MGM to with a client certificate to be used in mutual TLS connections
     * @param restClient of type RestClient<MGMRestResource>
     * @param holdingIdentityShortHash The holding identity of the MGM
     * @param subjects collection of certificate subjects to be used
     * @param wait Duration before timing out, default 10 seconds
     */
    fun allowMutualTlsForSubjects(
        restClient: RestClient<MGMRestResource>,
        holdingIdentityShortHash: ShortHash,
        subjects: Collection<MemberX500Name>,
        wait: Duration = 10.seconds
    ) {
        restClient.use { client ->
            executeWithRetry(
                waitDuration = wait,
                operationName = "Allow mutual TLS certificate"
            ) {
                val resource = client.start().proxy
                subjects.forEach { subject ->
                    resource.mutualTlsAllowClientCertificate(holdingIdentityShortHash.value, subject.toString())
                }
            }
        }
    }

    /**
     * list the allowed client certificates subjects to be used in mutual TLS connections
     * @param restClient of type RestClient<MGMRestResource>
     * @param holdingIdentityShortHash the holding identity of the MGM
     * @param wait Duration before timing out, default 10 seconds
     * @return a Collection of Strings with the list of the allowed client certificate subjects.
     */
    fun listMutualTlsClientCertificates(
        restClient: RestClient<MGMRestResource>,
        holdingIdentityShortHash: ShortHash,
        wait: Duration = 10.seconds
    ): Collection<String> {
        return restClient.use { client ->
            executeWithRetry(
                waitDuration = wait,
                operationName = "List mutual TLS certificates"
            ) {
                val resource = client.start().proxy
                resource.mutualTlsListClientCertificate(holdingIdentityShortHash.value)
            }
        }
    }

    /**
     * generate a certificate signing request (CSR) for a tenant
     * @param restClient of type RestClient<CertificatesRestResource>
     * @param tlsKey value of the TLS key ID
     * @param subjectX500Name the X.500 name that will be the subject associated with the request
     * @param p2pHostNames used to specify additional subject names
     * @param wait Duration before timing out, default 10 seconds
     * @return a PKCS10CertificationRequest if the CSR is valid
     */
    @Suppress("DEPRECATION")
    fun generateP2pCsr(
        restClient: RestClient<CertificatesRestResource>,
        tlsKey: KeyPairIdentifier,
        subjectX500Name: MemberX500Name,
        p2pHostNames: Collection<MemberX500Name>,
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
                    keyId = tlsKey.id,
                    x500Name = subjectX500Name.toString(),
                    subjectAlternativeNames = p2pHostNames.map { it.toString() },
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

    /**
     * Upload a given certificate for TLS
     * @param restClient of type RestClient<CertificatesRestResource>
     * @param certificate value of the given certificate
     * @param alias the unique alias under which the certificate chain will be stored
     * @param wait Duration before timing out, default 10 seconds
     */
    @Suppress("DEPRECATION")
    fun uploadTlsCertificate(
        restClient: RestClient<CertificatesRestResource>,
        certificate: InputStream,
        alias: String = P2P_TLS_CERTIFICATE_ALIAS,
        wait: Duration = 10.seconds
    ) {
        uploadCertificate(restClient, certificate, "p2p-tls", alias, wait)
    }

    /**
     * Upload a given certificate
     * @param restClient of type RestClient<CertificatesRestResource>
     * @param certificate value of the given certificate
     * @param usage the certificate usage such as p2p-tls or code-signer etc.
     * @param alias the unique alias under which the certificate chain will be stored
     * @param wait Duration before timing out, default 10 seconds
     */
    @Suppress("DEPRECATION")
    fun uploadCertificate(
        restClient: RestClient<CertificatesRestResource>,
        certificate: InputStream,
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
