@file:Suppress("DEPRECATION")
// Needed for the v1.CertificatesRestResource import statement

package net.corda.sdk.network


import io.ktor.client.request.forms.InputProvider
import io.ktor.utils.io.streams.asInput
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import net.corda.data.certificates.CertificateUsage
import net.corda.membership.certificates.CertificateUsageUtils.publicName
import net.corda.membership.rest.v1.types.response.KeyPairIdentifier
import net.corda.restclient.models.GenerateCsrWrapperRequest
import net.corda.sdk.network.Keys.Companion.P2P_TLS_CERTIFICATE_ALIAS
import net.corda.sdk.rest.RestClientUtils.suspendableExecuteWithRetry
import net.corda.sdk.rest.restClient
import net.corda.sdk.rest.restClientCoroutineScope
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
    suspend fun allowMutualTlsForSubjects(
        coroutineScope: CoroutineScope = restClientCoroutineScope,
        holdingIdentityShortHash: String,
        subjects: Collection<String>,
        wait: Duration = 10.seconds
    )  {
        subjects.map {
            coroutineScope.async {
                suspendableExecuteWithRetry(
                    waitDuration = wait,
                    operationName = "Allow mutual TLS certificate"
                ) {
                    restClient.mgmClient()
                        .putMgmHoldingidentityshorthashMutualTlsAllowedClientCertificateSubjectsSubject(
                            holdingIdentityShortHash,
                            it
                        )
                }
            }
        }.awaitAll()
    }

    /**
     * list the allowed client certificates subjects to be used in mutual TLS connections
     * @param restClient of type RestClient<MGMRestResource>
     * @param holdingIdentityShortHash the holding identity of the MGM
     * @param wait Duration before timing out, default 10 seconds
     * @return a Collection of Strings with the list of the allowed client certificate subjects.
     */
    suspend fun listMutualTlsClientCertificates(
        coroutineScope: CoroutineScope = restClientCoroutineScope,
        holdingIdentityShortHash: String,
        wait: Duration = 10.seconds
    ): Collection<String> = withContext(coroutineScope.coroutineContext) {

        return@withContext suspendableExecuteWithRetry(
            waitDuration = wait,
            operationName = "List mutual TLS certificates"
        ) {
            restClient.mgmClient()
                .getMgmHoldingidentityshorthashMutualTlsAllowedClientCertificateSubjects(holdingIdentityShortHash)
        }.body()
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
    suspend fun generateP2pCsr(
        coroutineScope: CoroutineScope = restClientCoroutineScope,
        tlsKey: KeyPairIdentifier,
        subjectX500Name: String,
        p2pHostNames: Collection<String>,
        wait: Duration = 10.seconds
    ): PKCS10CertificationRequest = withContext(coroutineScope.coroutineContext) {
        val csr = suspendableExecuteWithRetry(
            waitDuration = wait,
            operationName = "Generate CSR"
        ) {
            restClient.certificatesClient().postCertificateTenantidKeyid(
                tenantid = "p2p",
                keyid = tlsKey.id,
                generateCsrWrapperRequest = GenerateCsrWrapperRequest(
                    x500Name = subjectX500Name,
                    subjectAlternativeNames = p2pHostNames.toList(),
                    contextMap = null,
                )
            ).body()
        }

        return@withContext csr.reader().use { reader ->
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
    suspend fun uploadTlsCertificate(
        coroutineScope: CoroutineScope = restClientCoroutineScope,
        certificate: InputStream,
        alias: String = P2P_TLS_CERTIFICATE_ALIAS,
        wait: Duration = 10.seconds
    ) = withContext(coroutineScope.coroutineContext) {
        uploadCertificate(coroutineScope, certificate, CertificateUsage.P2P_TLS, alias, wait)
    }

    /**
     * Upload a given certificate
     * @param restClient of type RestClient<CertificatesRestResource>
     * @param certificate value of the given certificate
     * @param usage the certificate usage such as p2p-tls or code-signer etc. Underscores are repleaced with hyphens
     * @param alias the unique alias under which the certificate chain will be stored
     * @param wait Duration before timing out, default 10 seconds
     */
    @Suppress("DEPRECATION")
    suspend fun uploadCertificate(
        coroutineScope: CoroutineScope = restClientCoroutineScope,
        certificate: InputStream,
        usage: CertificateUsage,
        alias: String,
        wait: Duration = 10.seconds
    ) = withContext(coroutineScope.coroutineContext) {
        suspendableExecuteWithRetry(
            waitDuration = wait,
            operationName = "Upload certificate $alias"
        ) {
            restClient.certificatesClient().putCertificateClusterUsage(
                usage = usage.publicName,
                alias = alias,
                certificate = InputProvider {
                    certificate.asInput()
                }
            )
        }
    }
}
