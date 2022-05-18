package net.corda.membership.httprpc.v1

import net.corda.httprpc.RpcOps
import net.corda.httprpc.annotations.HttpRpcGET
import net.corda.httprpc.annotations.HttpRpcPOST
import net.corda.httprpc.annotations.HttpRpcPathParameter
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
     * @return A list of scheme names.
     */
    @HttpRpcGET(
        path = "{holdingIdentityId}/schemes/{hsmCategory}",
        description = "Get list of schemes for the cluster."
    )
    fun listSchemes(
        @HttpRpcPathParameter(description = "The Holding Identity ID.")
        holdingIdentityId: String,
        @HttpRpcPathParameter(description = "The HSM Category")
        hsmCategory: String,
    ): Collection<String>

    /**
     * GET endpoint which returns the list of a holding identity keys.
     *
     * @param holdingIdentityId The ID of the holding identity.
     *
     * @return A map from a holding identity key ID to its metadata.
     */
    @HttpRpcGET(
        path = "{holdingIdentityId}",
        description = "Get list of keys for members."
    )
    fun listKeys(
        @HttpRpcPathParameter(description = "The Holding Identity ID.")
        holdingIdentityId: String,
    ): Map<String, KeyMetaData>

    /**
     * POST endpoint which Generate a key pair for a holding identity.
     *
     * @param holdingIdentityId The Holding identity IDs.
     * @param alias The key alias.
     * @param hsmCategory The HSM category.
     *
     * @return The ID of the newly generated key pair.
     */
    @HttpRpcPOST(
        path = "{holdingIdentityId}",
        description = "Generate key pair."
    )
    fun generateKeyPair(
        @HttpRpcPathParameter(description = "The Holding Identity ID.")
        holdingIdentityId: String,
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
            required = false,
        )
        scheme: String?,
    ): String

    /**
     * GET endpoint which returns a PEM string from a holding identity KEY.
     *
     * @param holdingIdentityId The ID of holding identity.
     * @param keyId The ID of the key.
     *
     * @return The public key in PEM format.
     */
    @HttpRpcGET(
        path = "{holdingIdentityId}/{keyId}",
        description = "GET key in PEM format."
    )
    fun generateKeyPem(
        @HttpRpcPathParameter(description = "The Holding Identity ID.")
        holdingIdentityId: String,
        @HttpRpcPathParameter(description = "The Key ID.")
        keyId: String,
    ): String
}
