package net.corda.membership.httprpc.v1

import net.corda.httprpc.RpcOps
import net.corda.httprpc.annotations.HttpRpcGET
import net.corda.httprpc.annotations.HttpRpcPOST
import net.corda.httprpc.annotations.HttpRpcPathParameter
import net.corda.httprpc.annotations.HttpRpcQueryParameter
import net.corda.httprpc.annotations.HttpRpcRequestBodyParameter
import net.corda.httprpc.annotations.HttpRpcResource
import net.corda.membership.httprpc.v1.types.response.KeyMetaData

@HttpRpcResource(
    name = "KeysRpcOps",
    description = "Keys API",
    path = "keys"
)
interface KeysRpcOps : RpcOps {
    /**
     * GET endpoint which returns the list of the schemes in the cluster.
     *
     * @param tenantId The tenant ID.
     * @param hsmCategory The HSM category.
     * @return A list of scheme names.
     */
    @HttpRpcGET(
        path = "{tenantId}/schemes/{hsmCategory}",
        description = "Get list of schemes for the cluster."
    )
    fun listSchemes(
        @HttpRpcPathParameter(description = "'p2p', 'rpc-api', or holding identity identity ID.")
        tenantId: String,
        @HttpRpcPathParameter(description = "The HSM Category")
        hsmCategory: String,
    ): Collection<String>

    /**
     * GET endpoint which returns the list of a tenant keys.
     *
     * @param tenantId The tenant ID.
     *
     * @return A map from a tenant key ID to its metadata.
     */
    @HttpRpcGET(
        path = "{tenantId}",
        description = "Get list of keys for members."
    )
    fun listKeys(
        @HttpRpcPathParameter(description = "'p2p', 'rpc-api', or holding identity identity ID.")
        tenantId: String,
    ): Map<String, KeyMetaData>

    /**
     * POST endpoint which Generate a key pair.
     *
     * @param tenantId The tenant ID.
     * @param alias The key alias.
     * @param hsmCategory The HSM category.
     * @param scheme The scheme.
     *
     * @return The ID of the newly generated key pair.
     */
    @HttpRpcPOST(
        path = "{tenantId}",
        description = "Generate key pair."
    )
    fun generateKeyPair(
        @HttpRpcPathParameter(description = "'p2p', 'rpc-api', or holding identity identity ID.")
        tenantId: String,
        @HttpRpcRequestBodyParameter(
            description = "The key alias",
            required = true,
        )
        alias: String,
        @HttpRpcRequestBodyParameter(
            description = "The HSM Category",
            required = true,
        )
        hsmCategory: String,
        @HttpRpcRequestBodyParameter(
            description = "The scheme",
            required = true,
        )
        scheme: String,
    ): String

    /**
     * GET endpoint which returns a PEM string from a key.
     *
     * @param tenantId The tenant ID.
     * @param keyId The ID of the key.
     *
     * @return The public key in PEM format.
     */
    @HttpRpcGET(
        path = "{tenantId}/{keyId}",
        description = "GET key in PEM format."
    )
    fun generateKeyPem(
        @HttpRpcPathParameter(description = "'p2p', 'rpc-api', or holding identity identity ID.")
        tenantId: String,
        @HttpRpcPathParameter(description = "The Key ID.")
        keyId: String,
    ): String

    /**
     * GET endpoint which Generate a certificate signing request (CSR) for a holding identity.
     *
     * @param tenantId The tenant ID.
     * @param keyId The Key ID.
     * @param x500name A valid X500 name.
     * @param emailAddress The email address.
     * @param keyUsageExtension - OID to a valid key usage extension (default to server auth).
     * @param subjectAlternativeNames - list of subject alternative DNS names
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
        @HttpRpcQueryParameter(
            description = "The X500 name",
            required = true,
        )
        x500name: String,
        @HttpRpcQueryParameter(
            description = "The email address",
            required = false,
        )
        emailAddress: String? = null,
        @HttpRpcQueryParameter(
            description = "The key usage extension",
            required = false,
        )
        keyUsageExtension: String? = null,
        @HttpRpcQueryParameter(
            description = "Subject alternative names",
            required = false,
        )
        subjectAlternativeNames: Collection<String>?,
    ): String
}
