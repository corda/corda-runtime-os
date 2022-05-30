package net.corda.membership.httprpc.v1

import net.corda.httprpc.RpcOps
import net.corda.httprpc.annotations.HttpRpcPOST
import net.corda.httprpc.annotations.HttpRpcPathParameter
import net.corda.httprpc.annotations.HttpRpcRequestBodyParameter
import net.corda.httprpc.annotations.HttpRpcResource

@HttpRpcResource(
    name = "CertificatesRpcOps",
    description = "Certificates API",
    path = "certificates"
)
interface CertificatesRpcOps : RpcOps {
    companion object {
        const val SIGNATURE_SPEC = "signatureSpec"
    }
    /**
     * POST endpoint which Generate a certificate signing request (CSR) for a holding identity.
     *
     * @param tenantId The tenant ID.
     * @param keyId The Key ID.
     * @param x500name A valid X500 name.
     * @param certificateRole - The certificate role
     * @param subjectAlternativeNames - list of subject alternative DNS names
     * @param contextMap - Any additional attributes to add to the CSR.
     *
     * @return The CSR in PEM format.
     */
    @Suppress("LongParameterList")
    @HttpRpcPOST(
        path = "{tenantId}/{keyId}",
        description = "Generate certificate signing request (CSR)."
    )
    fun generateCsr(
        @HttpRpcPathParameter(description = "'p2p', 'rpc-api', or holding identity identity ID.")
        tenantId: String,
        @HttpRpcPathParameter(description = "The Key ID.")
        keyId: String,
        @HttpRpcRequestBodyParameter(
            description = "The X500 name",
            required = true,
        )
        x500name: String,
        @HttpRpcRequestBodyParameter(
            description = "Certificate role. For example: TLS, SESSION_INIT, ...",
            required = true,
        )
        certificateRole: String,
        @HttpRpcRequestBodyParameter(
            description = "Subject alternative names",
            required = false,
        )
        subjectAlternativeNames: List<String>?,
        @HttpRpcRequestBodyParameter(
            description = "Context Map. For example: `$SIGNATURE_SPEC` to signature spec (SHA512withECDSA, SHA384withRSA...)",
            required = false,
        )
        contextMap: Map<String, String?>?,
    ): String
}
