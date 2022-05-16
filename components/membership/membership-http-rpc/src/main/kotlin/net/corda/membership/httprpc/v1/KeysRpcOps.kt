package net.corda.membership.httprpc.v1

import net.corda.httprpc.RpcOps
import net.corda.httprpc.annotations.HttpRpcGET
import net.corda.httprpc.annotations.HttpRpcPOST
import net.corda.httprpc.annotations.HttpRpcPathParameter
import net.corda.httprpc.annotations.HttpRpcRequestBodyParameter
import net.corda.httprpc.annotations.HttpRpcResource

@HttpRpcResource(
    name = "KeysRpcOps",
    description = "Keys API",
    path = "keys"
)
interface KeysRpcOps : RpcOps {
    companion object {
        private const val CLUSTER_ID = "cluster"
    }
    /**
     * GET endpoint which returns the list of the cluster keys.
     *
     * @return The list of the cluster keys IDs.
     */
    @HttpRpcGET(
        path = "cluster",
        description = "Get list of keys for cluster."
    )
    fun listClusterKeys(): Collection<String> = listKeys(CLUSTER_ID)

    /**
     * GET endpoint which returns the list of a holding identity keys.
     *
     * @param holdingIdentityId The ID of the holding identity.
     *
     * @return The list of the holding identity keys IDs.
     */
    @HttpRpcGET(
        path = "{holdingIdentityId}",
        description = "Get list of keys for members."
    )
    fun listKeys(
        @HttpRpcPathParameter(description = "The Holding Identity ID.")
        holdingIdentityId: String,
    ): Collection<String>

    /**
     * POST endpoint which Generate a key pair for the cluster.
     *
     * @param alias The key alias.
     * @param hsmCategory The HSM category.
     *
     * @return The ID of the newly generated key pair.
     */
    @HttpRpcPOST(
        path = "cluster",
        description = "Generate key pair for cluster."
    )
    fun generateKeyPairForCluster(
        @HttpRpcRequestBodyParameter(
            name = "alias",
            description = "The key alias",
            required = true,
        )
        alias: String,
        @HttpRpcRequestBodyParameter(
            name = "hsmCategory",
            description = "The HSM Category",
            required = true,
        )
        hsmCategory: String,
    ): String = generateKeyPair(
        holdingIdentityId = CLUSTER_ID,
        alias = alias,
        hsmCategory = hsmCategory
    )

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
            name = "alias",
            description = "The key alias",
            required = true,
        )
        alias: String,
        @HttpRpcRequestBodyParameter(
            name = "hsmCategory",
            description = "The HSM Category",
            required = true,
        )
        hsmCategory: String,
    ): String

    /**
     * GET endpoint which returns a PEM string from a cluster KEY.
     *
     * @param keyId The ID of the key.
     *
     * @return The public key in PEM format.
     */
    @HttpRpcGET(
        path = "cluster/{keyId}",
        description = "GET key in PEM format."
    )
    fun generateClusterKeyPem(
        @HttpRpcPathParameter(description = "The Key ID.")
        keyId: String,
    ): String = generateKeyPem(CLUSTER_ID, keyId)

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
