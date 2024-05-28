package net.corda.sdk.network

import net.corda.crypto.core.ShortHash
import net.corda.data.certificates.CertificateUsage
import net.corda.membership.certificates.CertificateUsageUtils.publicName
import net.corda.membership.rest.v1.types.response.KeyPairIdentifier
import net.corda.restclient.CordaRestClient
import net.corda.restclient.dto.GenerateCsrWrapperRequest
import net.corda.sdk.network.Keys.Companion.P2P_TLS_CERTIFICATE_ALIAS
import net.corda.sdk.rest.RestClientUtils.executeWithRetry
import net.corda.v5.base.types.MemberX500Name
import org.bouncycastle.openssl.PEMParser
import org.bouncycastle.pkcs.PKCS10CertificationRequest
import java.io.File
import java.security.InvalidKeyException
import kotlin.io.path.deleteIfExists
import kotlin.io.path.writeText
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class ClientCertificates(val restClient: CordaRestClient) {

    /**
     * Update an MGM to with a client certificate to be used in mutual TLS connections
     * @param holdingIdentityShortHash The holding identity of the MGM
     * @param subjects collection of certificate subjects to be used
     * @param wait Duration before timing out, default 10 seconds
     */
    fun allowMutualTlsForSubjects(
        holdingIdentityShortHash: ShortHash,
        subjects: Collection<MemberX500Name>,
        wait: Duration = 10.seconds
    ) {
        executeWithRetry(
            waitDuration = wait,
            operationName = "Allow mutual TLS certificate"
        ) {
            subjects.forEach { subject ->
                restClient
                    .mgmClient
                    .putMgmHoldingidentityshorthashMutualTlsAllowedClientCertificateSubjectsSubject(
                        holdingIdentityShortHash.value,
                        subject.toString()
                    )
            }
        }
    }

    /**
     * list the allowed client certificates subjects to be used in mutual TLS connections
     * @param holdingIdentityShortHash the holding identity of the MGM
     * @param wait Duration before timing out, default 10 seconds
     * @return a Collection of Strings with the list of the allowed client certificate subjects.
     */
    fun listMutualTlsClientCertificates(
        holdingIdentityShortHash: ShortHash,
        wait: Duration = 10.seconds
    ): Collection<String> {
        return executeWithRetry(
            waitDuration = wait,
            operationName = "List mutual TLS certificates"
        ) {
            restClient
                .mgmClient
                .getMgmHoldingidentityshorthashMutualTlsAllowedClientCertificateSubjects(
                    holdingIdentityShortHash.value
                )
        }
    }

    /**
     * generate a certificate signing request (CSR) for a tenant
     * @param tlsKey value of the TLS key ID
     * @param subjectX500Name the X.500 name that will be the subject associated with the request
     * @param p2pHostNames used to specify additional subject names
     * @return a PKCS10CertificationRequest if the CSR is valid
     */
    fun generateP2pCsr(
        tlsKey: KeyPairIdentifier,
        subjectX500Name: MemberX500Name,
        p2pHostNames: Collection<String>
    ): PKCS10CertificationRequest {
        val requestBody = GenerateCsrWrapperRequest(
            x500Name = subjectX500Name.toString(),
            contextMap = null,
            subjectAlternativeNames = p2pHostNames.map { it.toString() }
        )
        val csr = restClient.certificatesClient.postCertificateTenantidKeyid(
            tenantid = "p2p",
            keyid = tlsKey.id,
            netCordaRestclientDtoGenerateCsrWrapperRequest = requestBody
        )
        return csr.reader().use { reader ->
            PEMParser(reader).use { parser ->
                parser.readObject()
            }
        } as? PKCS10CertificationRequest ?: throw InvalidKeyException("CSR is not a valid CSR: $csr")
    }

    /**
     * Upload a given certificate for TLS
     * @param certificateFile value of the given certificate
     * @param alias the unique alias under which the certificate chain will be stored
     * @param wait Duration before timing out, default 10 seconds
     */
    fun uploadTlsCertificate(
        certificateFile: File,
        alias: String = P2P_TLS_CERTIFICATE_ALIAS,
        wait: Duration = 10.seconds
    ) {
        uploadClusterCertificate(certificateFile, CertificateUsage.P2P_TLS, alias, wait)
    }

    /**
     * Upload a given certificate
     * @param certificateFile value of the given certificate
     * @param usage the certificate usage such as p2p-tls or code-signer etc. Underscores are repleaced with hyphens
     * @param alias the unique alias under which the certificate chain will be stored
     * @param wait Duration before timing out, default 10 seconds
     */
    fun uploadClusterCertificate(
        certificateFile: File,
        usage: CertificateUsage,
        alias: String,
        wait: Duration = 10.seconds
    ) {
        executeWithRetry(
            waitDuration = wait,
            operationName = "Upload certificate $alias"
        ) {
            restClient.certificatesClient.putCertificateClusterUsage(
                usage.publicName,
                alias,
                listOf(certificateFile)
            )
        }
    }
}

/**
 * Write a given string to a temp file.
 * Typically used for uploading, so we don't really care about where the file lives
 */
fun String.writeToTempFile(fileName: String): File {
    val file = kotlin.io.path.createTempFile(fileName).also {
        it.deleteIfExists()
        it.toFile().deleteOnExit()
    }
    file.writeText(this)
    return file.toFile()
}
