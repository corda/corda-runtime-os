package net.corda.membership.httprpc.v1

import net.corda.httprpc.HttpFileUpload
import net.corda.httprpc.RpcOps
import net.corda.httprpc.annotations.HttpRpcPOST
import net.corda.httprpc.annotations.HttpRpcPUT
import net.corda.httprpc.annotations.HttpRpcPathParameter
import net.corda.httprpc.annotations.HttpRpcRequestBodyParameter
import net.corda.httprpc.annotations.HttpRpcResource

/**
 * The Certificates API consists of endpoints used to work with certificates and related operations. The API allows you
 * to import a certificate chain, and generate a certificate signing request (CSR) to be submitted to a certificate
 * authority (CA).
 */
@HttpRpcResource(
    name = "Certificates API",
    description = "The Certificates API consists of endpoints used to work with certificates and related operations.",
    path = "certificates"
)
interface CertificatesRpcOps : RpcOps {
    companion object {
        const val SIGNATURE_SPEC = "signatureSpec"
    }

    /**
     * The [importCertificateChain] method enables you to import a certificate chain for a tenant. A certificate chain
     * can be obtained from a certificate authority by submitting a certificate signing request (see [generateCsr]
     * method). This method does not return anything if the import is successful.
     *
     * Example usage:
     * ```
     * certificatesOps.importCertificateChain("58B6030FABDD", "cert58B6030FABDD", certificateChain)
     * ```
     *
     * @param tenantId Can either be a holding identity ID, the value 'p2p' for a cluster-level tenant of the P2P
     * services, or the value 'rpc-api' for a cluster-level tenant of the HTTP RPC API.
     * @param alias Unique alias under which the certificate chain will be stored.
     * @param certificates Valid certificate chain in PEM format obtained from a certificate authority.
     */
    @HttpRpcPUT(
        path = "{tenantId}",
        description = "Enables you to import a certificate chain for a tenant."
    )
    fun importCertificateChain(
        @HttpRpcPathParameter(description = "Can either be a holding identity ID, the value 'p2p' for a cluster-level" +
                " tenant of the P2P services, or the value 'rpc-api' for a cluster-level tenant of the HTTP RPC API.")
        tenantId: String,
        @HttpRpcRequestBodyParameter(
            description = "Unique alias under which the certificate chain will be stored.",
            required = true,
        )
        alias: String,
        @HttpRpcRequestBodyParameter(
            description = "Valid certificate chain in PEM format obtained from a certificate authority.",
            required = true,
            name = "certificate"
        )
        certificates: List<HttpFileUpload>,
    )

    /**
     * The [generateCsr] method enables you to generate a certificate signing request (CSR) for a tenant. The resulting
     * CSR is typically submitted to a certificate authority to acquire a signed certificate. If successful, this method
     * returns the generated CSR in PEM format.
     *
     * Example usage:
     * ```
     * certificatesOps.generateCsr("58B6030FABDD", "3B9A266F96E2", "C=GB, L=London, O=MGM", "TLS", ["localhost"],
     * {"signatureSpec": "SHA256withECDSA"})
     * ```
     *
     * @param tenantId Can either be a holding identity ID, the value 'p2p' for a cluster-level tenant of the P2P
     * services, or the value 'rpc-api' for a cluster-level tenant of the HTTP RPC API.
     * @param keyId Identifier of the public key that will be included in the certificate.
     * @param x500Name X.500 name that will be the subject associated with the request.
     * @param certificateRole Can be the value 'ACCOUNTS', 'CI', 'LEDGER', 'NOTARY', 'SESSION_INIT', 'TLS', or 'JWT_KEY'.
     * @param subjectAlternativeNames Optional. Lets you specify additional subject names.
     * @param contextMap Optional. Lets you add additional attributes to the CSR e.g. signature spec.
     *
     * @return The CSR in PEM format.
     */
    @Suppress("LongParameterList")
    @HttpRpcPOST(
        path = "{tenantId}/{keyId}",
        description = "Enables you to generate a certificate signing request (CSR) for a tenant."
    )
    fun generateCsr(
        @HttpRpcPathParameter(description = "Can either be a holding identity ID, the value 'p2p' for a cluster-level" +
                " tenant of the P2P services, or the value 'rpc-api' for a cluster-level tenant of the HTTP RPC API.")
        tenantId: String,
        @HttpRpcPathParameter(description = "Identifier of the public key that will be included in the certificate.")
        keyId: String,
        @HttpRpcRequestBodyParameter(
            description = "X.500 name that will be the subject associated with the request.",
            required = true,
        )
        x500Name: String,
        @HttpRpcRequestBodyParameter(
            description = "Can be the value 'ACCOUNTS', 'CI', 'LEDGER', 'NOTARY', 'SESSION_INIT', 'TLS', or 'JWT_KEY'.",
            required = true,
        )
        certificateRole: String,
        @HttpRpcRequestBodyParameter(
            description = "Lets you specify additional subject names.",
            required = false,
        )
        subjectAlternativeNames: List<String>?,
        @HttpRpcRequestBodyParameter(
            description = "Lets you add additional attributes to the CSR e.g. signature spec.",
            required = false,
        )
        contextMap: Map<String, String?>?,
    ): String
}
