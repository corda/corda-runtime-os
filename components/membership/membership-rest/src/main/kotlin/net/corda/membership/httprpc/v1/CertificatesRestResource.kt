package net.corda.membership.httprpc.v1

import net.corda.httprpc.HttpFileUpload
import net.corda.httprpc.RestResource
import net.corda.httprpc.annotations.HttpGET
import net.corda.httprpc.annotations.HttpPOST
import net.corda.httprpc.annotations.HttpPUT
import net.corda.httprpc.annotations.RestPathParameter
import net.corda.httprpc.annotations.RestRequestBodyParameter
import net.corda.httprpc.annotations.HttpRestResource

/**
 * The Certificates API consists of endpoints used to work with certificates and related operations. The API allows you
 * to import a certificate chain, and generate a certificate signing request (CSR) to be submitted to a certificate
 * authority (CA).
 */
@HttpRestResource(
    name = "Certificates API",
    description = "The Certificates API consists of endpoints used to work with certificates and related operations. " +
        "The API allows you to import a certificate chain, and generate a certificate signing request (CSR) to be" +
        " submitted to a certificate authority (CA).",
    path = "certificates"
)
interface CertificatesRestResource : RestResource {
    companion object {
        const val SIGNATURE_SPEC = "signatureSpec"
    }

    /**
     * The [importCertificateChain] method enables you to import a cluster level certificate chain. A certificate chain
     * can be obtained from a certificate authority by submitting a certificate signing request (see [generateCsr]
     * method). This method does not return anything if the import is successful.
     *
     * Example usage:
     * ```
     * certificatesOps.importCertificateChain(usage = "p2p-tls", alias = "cert58B6030FABDD",
     * certificates = "-----BEGIN CERTIFICATE-----\n{truncated for readability}\n-----END CERTIFICATE-----")
     * ```
     *
     * @param usage The certificate usage. Can be:
     *     * 'p2p-tls' for a TLS certificate to be used in P2P communication.
     *     * 'p2p-session' for a session certificate to be used in P2P communication.
     *     * 'rest-api-tls' for a TLS certificate to be used in REST API communication.
     *     * 'code-signer' for a certificate of the code signing service
     * @param alias The unique alias under which the certificate chain will be stored.
     * @param certificates A valid certificate chain in PEM format obtained from a certificate authority.
     */
    @HttpPUT(
        path = "cluster/{usage}",
        description = "This method imports a certificate chain for a cluster."
    )
    fun importCertificateChain(
        @RestPathParameter(
            description = "The certificate usage. Can be either 'p2p-tls' for a TLS certificate to be used in P2P communication, " +
                "'p2p-session' for a session certificate to be used in P2P communication, " +
                "'rest-api-tls' for a TLS certificate to be used in REST API communication, " +
                "or 'code-signer' for a certificate of the code signing service."
        )
        usage: String,
        @RestRequestBodyParameter(
            description = "The unique alias under which the certificate chain will be stored",
            required = true,
        )
        alias: String,
        @RestRequestBodyParameter(
            description = "A valid certificate chain in PEM format obtained from a certificate authority",
            required = true,
            name = "certificate"
        )
        certificates: List<HttpFileUpload>,
    ) = importCertificateChain(
        usage = usage,
        alias = alias,
        holdingIdentityId = null,
        certificates = certificates,
    )

    /**
     * The [importCertificateChain] method enables you to import a certificate chain for a virtual node. A certificate chain
     * can be obtained from a certificate authority by submitting a certificate signing request (see [generateCsr]
     * method). This method does not return anything if the import is successful.
     *
     * Example usage:
     * ```
     * certificatesOps.importCertificateChain(usage = "rest-api-tls", alias = "cert58B6030FABDD",
     * holdingIdentityId = "58B6030FABDD",
     * certificates = "-----BEGIN CERTIFICATE-----\n{truncated for readability}\n-----END CERTIFICATE-----")
     * ```
     *
     * @param usage The certificate usage. Can be:
     *     * 'p2p-tls' for a TLS certificate to be used in P2P communication.
     *     * 'p2p-session' for a session certificate to be used in P2P communication.
     *     * 'rest-api-tls' for a TLS certificate to be used in REST API communication.
     *     * 'code-signer' for a certificate of the code signing service
     * @param holdingIdentityId The holding identity of the virtual node that own the certificate.
     * @param alias The unique alias under which the certificate chain will be stored.
     * @param certificates A valid certificate chain in PEM format obtained from a certificate authority.
     */
    @HttpPUT(
        path = "vnode/{holdingIdentityId}/{usage}",
        description = "This method imports a certificate chain for a virtual node."
    )
    fun importCertificateChain(
        @RestPathParameter(
            description = "The certificate usage. Can be either 'p2p-tls' for a TLS certificate to be used in P2P communication, " +
                "'p2p-session' for a session certificate to be used in P2P communication, " +
                "'rest-api-tls' for a TLS certificate to be used in REST API communication, " +
                "or 'code-signer' for a certificate of the code signing service."
        )
        usage: String,
        @RestPathParameter(
            description = "The certificate holding identity ID",
        )
        holdingIdentityId: String?,
        @RestRequestBodyParameter(
            description = "The unique alias under which the certificate chain will be stored",
            required = true,
        )
        alias: String,
        @RestRequestBodyParameter(
            description = "A valid certificate chain in PEM format obtained from a certificate authority",
            required = true,
            name = "certificate"
        )
        certificates: List<HttpFileUpload>,
    )

    /**
     * The [getCertificateAliases] method enables you to get the aliases of all the cluster level certificate chains.
     *
     * @param usage The certificate usage. Can be:
     *     * 'p2p-tls' for a TLS certificate to be used in P2P communication.
     *     * 'p2p-session' for a session certificate to be used in P2P communication.
     *     * 'rest-api-tls' for a TLS certificate to be used in REST API communication.
     *     * 'code-signer' for a certificate of the code signing service
     * @return A list of the cluster level certificates aliases in the usage.
     */
    @HttpGET(
        path = "cluster/{usage}",
        description = "This method gets the certificate chain aliases for a cluster.",
        responseDescription = "The cluster level certificates aliases in the usage.",
    )
    fun getCertificateAliases(
        @RestPathParameter(
            description = "The certificate usage. Can be either 'p2p-tls' for a TLS certificate to be used in P2P communication, " +
                "'p2p-session' for a session certificate to be used in P2P communication, " +
                "'rest-api-tls' for a TLS certificate to be used in REST API communication, " +
                "or 'code-signer' for a certificate of the code signing service."
        )
        usage: String,
    ): List<String> = getCertificateAliases(
        usage = usage,
        holdingIdentityId = null,
    )

    /**
     * The [getCertificateAliases] method enables you to get the virtual node certificate aliases..
     *
     * @param usage The certificate usage. Can be:
     *     * 'p2p-tls' for a TLS certificate to be used in P2P communication.
     *     * 'p2p-session' for a session certificate to be used in P2P communication.
     *     * 'rest-api-tls' for a TLS certificate to be used in REST API communication.
     *     * 'code-signer' for a certificate of the code signing service
     * @param holdingIdentityId The holding identity of the virtual node that own the certificate.
     * @return A list of the virtual node certificates aliases in the usage.
     */
    @HttpGET(
        path = "vnode/{holdingIdentityId}/{usage}",
        description = "This method gets the certificate chain aliases for a virtual node.",
        responseDescription = "The virtual node certificates aliases in the usage.",
    )
    fun getCertificateAliases(
        @RestPathParameter(
            description = "The certificate usage. Can be either 'p2p-tls' for a TLS certificate to be used in P2P communication, " +
                "'p2p-session' for a session certificate to be used in P2P communication, " +
                "'rest-api-tls' for a TLS certificate to be used in REST API communication, " +
                "or 'code-signer' for a certificate of the code signing service."
        )
        usage: String,
        @RestPathParameter(
            description = "The certificate holding identity ID",
        )
        holdingIdentityId: String?,
    ): List<String>

    /**
     * The [getCertificateChain] method enables you to get a specific certificate chain by alias in PEM format.
     *
     * @param usage The certificate usage. Can be:
     *     * 'p2p-tls' for a TLS certificate to be used in P2P communication.
     *     * 'p2p-session' for a session certificate to be used in P2P communication.
     *     * 'rest-api-tls' for a TLS certificate to be used in REST API communication.
     *     * 'code-signer' for a certificate of the code signing service
     * @param alias The unique certificate chain alias
     * @return The certificate in PEM format.
     */
    @HttpGET(
        path = "cluster/{usage}/{alias}",
        description = "This method gets the certificate chain in PEM format for a cluster.",
        responseDescription = "The certificate in PEM format.",
    )
    fun getCertificateChain(
        @RestPathParameter(
            description = "The certificate usage. Can be either 'p2p-tls' for a TLS certificate to be used in P2P communication, " +
                "'p2p-session' for a session certificate to be used in P2P communication, " +
                "'rest-api-tls' for a TLS certificate to be used in REST API communication, " +
                "or 'code-signer' for a certificate of the code signing service."
        )
        usage: String,
        @RestPathParameter(
            description = "The certificate chain unique alias."
        )
        alias: String,
    ): String = getCertificateChain(
        usage = usage,
        alias = alias,
        holdingIdentityId = null,
    )

    /**
     * The [getCertificateAliases] method enables you to get the virtual node certificate chain in PEM format.
     *
     * @param usage The certificate usage. Can be:
     *     * 'p2p-tls' for a TLS certificate to be used in P2P communication.
     *     * 'p2p-session' for a session certificate to be used in P2P communication.
     *     * 'rest-api-tls' for a TLS certificate to be used in REST API communication.
     *     * 'code-signer' for a certificate of the code signing service
     * @param alias The unique certificate chain alias
     * @param holdingIdentityId The holding identity of the virtual node that own the certificate.
     * @return The certificate in PEM format.
     */
    @HttpGET(
        path = "vnode/{holdingIdentityId}/{usage}/{alias}",
        description = "This method gets the certificate chain in PEM format for a virtual node.",
        responseDescription = "The certificate in PEM format.",
    )
    fun getCertificateChain(
        @RestPathParameter(
            description = "The certificate usage. Can be either 'p2p-tls' for a TLS certificate to be used in P2P communication, " +
                "'p2p-session' for a session certificate to be used in P2P communication, " +
                "'rest-api-tls' for a TLS certificate to be used in REST API communication, " +
                "or 'code-signer' for a certificate of the code signing service."
        )
        usage: String,
        @RestPathParameter(
            description = "The certificate holding identity ID",
        )
        holdingIdentityId: String?,
        @RestPathParameter(
            description = "The certificate chain unique alias."
        )
        alias: String,
    ): String

    /**
     * The [generateCsr] method enables you to generate a certificate signing request (CSR) for a tenant. The resulting
     * CSR is typically submitted to a certificate authority to acquire a signed certificate. If successful, this method
     * returns the generated CSR in PEM format.
     *
     * Example usage:
     * ```
     * certificatesOps.generateCsr(tenantId = "58B6030FABDD", keyId = "3B9A266F96E2", x500Name = "C=GB, L=London, O=MGM",
     * subjectAlternativeNames = ["localhost"], contextMap = {"signatureSpec": "SHA256withECDSA"})
     *
     * certificatesOps.generateCsr(tenantId = "p2p", keyId = "3B9A266F96E2", x500Name = "C=GB, L=London, O=MGM",
     * subjectAlternativeNames = ["localhost"], contextMap = {"signatureSpec": "SHA256withECDSA"})
     * ```
     *
     * @param tenantId Can either be a holding identity ID, the value 'p2p' for a cluster-level tenant of the P2P
     * services, or the value 'rest-api' for a cluster-level tenant of the REST API.
     * @param keyId Identifier of the public key that will be included in the certificate.
     * @param x500Name The X.500 name that will be the subject associated with the request.
     * @param subjectAlternativeNames Optional. Used to specify additional subject names.
     * @param contextMap Optional. Used to add additional attributes to the CSR; for example, signature spec.
     *
     * @return The CSR in PEM format.
     */
    @Suppress("LongParameterList")
    @HttpPOST(
        path = "{tenantId}/{keyId}",
        description = "This method enables you to generate a certificate signing request (CSR) for a tenant.",
        responseDescription = "The CSR in PEM format.",
    )
    fun generateCsr(
        @RestPathParameter(
            description = "Can either be a holding identity ID, the value 'p2p' for a cluster-level" +
                " tenant of the P2P services, or the value 'rest-api' for a cluster-level tenant of the REST API"
        )
        tenantId: String,
        @RestPathParameter(description = "Identifier of the public key that will be included in the certificate")
        keyId: String,
        @RestRequestBodyParameter(
            description = "The X.500 name that will be the subject associated with the request",
            required = true,
        )
        x500Name: String,
        @RestRequestBodyParameter(
            description = "Used to specify additional subject names",
            required = false,
        )
        subjectAlternativeNames: List<String>?,
        @RestRequestBodyParameter(
            description = "Used to add additional attributes to the CSR; for example, signature spec",
            required = false,
        )
        contextMap: Map<String, String?>?,
    ): String
}
