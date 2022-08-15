package net.corda.membership.httprpc.v1

import net.corda.httprpc.RpcOps
import net.corda.httprpc.annotations.HttpRpcGET
import net.corda.httprpc.annotations.HttpRpcPOST
import net.corda.httprpc.annotations.HttpRpcPathParameter
import net.corda.httprpc.annotations.HttpRpcQueryParameter
import net.corda.httprpc.annotations.HttpRpcResource
import net.corda.membership.httprpc.v1.types.response.KeyMetaData

@HttpRpcResource(
    name = "Keys Management API",
    description = "Endpoints for public/private keys management.",
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
        description = "Get list of schemes for the cluster.",
        responseDescription = "The list of schemes codes which are supported by the associated HSM integration."
    )
    fun listSchemes(
        @HttpRpcPathParameter(description = "'p2p', 'rpc-api', or holding identity ID.")
        tenantId: String,
        @HttpRpcPathParameter(description = "The HSM Category")
        hsmCategory: String,
    ): Collection<String>

    /**
     * GET endpoint which returns the list of a tenant's keys.
     *
     * @param tenantId The tenant ID.
     * @param skip How many keys to skip.
     * @param take The maximal number of keys to take.
     * @param orderBy How to order the results.
     * @param category The keys categories.
     * @param schemeCodeName The keys' schema code name.
     * @param alias The keys alias.
     * @param masterKeyAlias The keys master key alias.
     * @param createdAfter Return only keys that had been created after...
     * @param createdBefore Return only keys that had been created before...
     * @param ids The list of key IDs (will ignore other parameters)
     *
     * @return A map from a tenant key ID to its metadata.
     */
    @HttpRpcGET(
        path = "{tenantId}",
        description = "Get list of keys for members.",
        responseDescription = "A map from a tenant key ID to its metadata."
    )
    @Suppress("LongParameterList")
    fun listKeys(
        @HttpRpcPathParameter(description = "'p2p', 'rpc-api', or holding identity ID.")
        tenantId: String,
        @HttpRpcQueryParameter(
            description = "Index of the first key",
            default = "0",
            required = false,
        )
        skip: Int,
        @HttpRpcQueryParameter(
            description = "Page size",
            default = "20",
            required = false,
        )
        take: Int,
        @HttpRpcQueryParameter(
            description = "How to order the results (one of: none, timestamp, category, scheme_code_name, alias, " +
                "master_key_alias, external_id, id, " +
                "timestamp_desc, category_desc, scheme_code_name_desc, " +
                "alias_desc, master_key_alias_desc, external_id_desc or id_desc)",
            default = "none",
            required = false,
        )
        orderBy: String,
        @HttpRpcQueryParameter(
            description = "The key category",
            required = false,
        )
        category: String?,
        @HttpRpcQueryParameter(
            description = "The key schema code name",
            required = false,
        )
        schemeCodeName: String?,
        @HttpRpcQueryParameter(
            description = "The key alias",
            required = false,
        )
        alias: String?,
        @HttpRpcQueryParameter(
            description = "The key master key alias",
            required = false,
        )
        masterKeyAlias: String?,
        @HttpRpcQueryParameter(
            description = "Only keys that had been created after (for example: 2007-12-03T10:15:30.00Z)",
            required = false,
        )
        createdAfter: String?,
        @HttpRpcQueryParameter(
            description = "Only keys that had been created before (for example: 2007-12-03T10:15:30.00Z)",
            required = false,
        )
        createdBefore: String?,
        @HttpRpcQueryParameter(
            description = "ID's of the keys (Will ignore any other parameter)",
            required = false,
            name = "id",
        )
        ids: List<String>?,
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
        path = "{tenantId}/alias/{alias}/category/{hsmCategory}/scheme/{scheme}",
        description = "Generate key pair.",
        responseDescription = "The ID of the newly generated key pair."
    )
    fun generateKeyPair(
        @HttpRpcPathParameter(description = "'p2p', 'rpc-api', or holding identity ID.")
        tenantId: String,
        @HttpRpcPathParameter(
            description = "The key alias"
        )
        alias: String,
        @HttpRpcPathParameter(
            description = "The HSM Category"
        )
        hsmCategory: String,
        @HttpRpcPathParameter(
            description = "The scheme"
        )
        scheme: String
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
        description = "GET key in PEM format.",
        responseDescription = "The public key in PEM format."
    )
    fun generateKeyPem(
        @HttpRpcPathParameter(description = "'p2p', 'rpc-api', or holding identity ID.")
        tenantId: String,
        @HttpRpcPathParameter(description = "The Key ID. Or an error code if not found.")
        keyId: String,
    ): String
}
