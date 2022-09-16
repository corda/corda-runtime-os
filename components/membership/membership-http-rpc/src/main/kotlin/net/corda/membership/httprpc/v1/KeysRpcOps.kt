package net.corda.membership.httprpc.v1

import net.corda.httprpc.RpcOps
import net.corda.httprpc.annotations.HttpRpcGET
import net.corda.httprpc.annotations.HttpRpcPOST
import net.corda.httprpc.annotations.HttpRpcPathParameter
import net.corda.httprpc.annotations.HttpRpcQueryParameter
import net.corda.httprpc.annotations.HttpRpcResource
import net.corda.membership.httprpc.v1.types.response.KeyMetaData
import net.corda.membership.httprpc.v1.types.response.KeyPairIdentifier

/**
 * The Keys Management API consists of endpoints used to work with cryptographic keys and related operations. The API
 * allows you to list scheme codes which are supported by the associated HSM integration, retrieve keys owned by a
 * tenant, generate a key pair for a tenant, and retrieve a tenant's key in PEM format.
 */
@HttpRpcResource(
    name = "Keys Management API",
    description = "The Keys Management API consists of endpoints used to work with cryptographic keys and related operations.",
    path = "keys"
)
interface KeysRpcOps : RpcOps {
    /**
     * The [listSchemes] method enables you to retrieve a list of supported key schemes for a specified tenant and HSM
     * category. For example, 'CORDA.RSA', 'CORDA.ECDSA.SECP256K1', 'CORDA.ECDSA_SECP256R1', 'CORDA.EDDSA.ED25519',
     * 'CORDA.SPHINCS-256'.
     *
     * Example usage:
     * ```
     * keysOps.listSchemes(tenantId = "58B6030FABDD", hsmCategory = "SESSION_INIT")
     * ```
     *
     * @param tenantId Can either be a holding identity ID, the value 'p2p' for a cluster-level tenant of the P2P
     * services, or the value 'rpc-api' for a cluster-level tenant of the HTTP RPC API.
     * @param hsmCategory Can be the value 'ACCOUNTS', 'CI', 'LEDGER', 'NOTARY', 'SESSION_INIT', 'TLS', or 'JWT_KEY'.
     *
     * @return List of scheme codes which are supported by the associated HSM integration.
     */
    @HttpRpcGET(
        path = "{tenantId}/schemes/{hsmCategory}",
        description = "Enables you to retrieve a list of supported key schemes for a specified tenant and HSM category.",
        responseDescription = "List of scheme codes which are supported by the associated HSM integration."
    )
    fun listSchemes(
        @HttpRpcPathParameter(description = "'p2p', 'rpc-api', or holding identity ID.")
        tenantId: String,
        @HttpRpcPathParameter(description = "Category of the HSM. Can be the value 'ACCOUNTS', 'CI', 'LEDGER', 'NOTARY', 'SESSION_INIT', 'TLS', or 'JWT_KEY'.")
        hsmCategory: String,
    ): Collection<String>

    /**
     * The [listKeys] method enables you to retrieve a list of keys belonging to a tenant. The returned list may be
     * filtered and/or ordered as required by passing a list of key IDs, or specifying one or more of the optional
     * parameters.
     *
     * Example usage:
     * ```
     * keysOps.listKeys(tenantId = "58B6030FABDD", skip = 4, take = 400, orderBy = "ALIAS", category = CI, alias = null, masterKeyAlias = null, createdAfter = null, createdBefore = null, schemeCodeName = null, ids = emptyList())
     *
     * keysOps.listKeys(tenantId = "58B6030FABDD", skip = null, take = null, orderBy = null, category = null, alias = null, masterKeyAlias = null, createdAfter = null, createdBefore = null, schemeCodeName = null, ids = ["3B9A266F96E2", "4A9A266F96E2"])
     * ```
     *
     * @param tenantId Can either be a holding identity ID, the value 'p2p' for a cluster-level tenant of the P2P
     * services, or the value 'rpc-api' for a cluster-level tenant of the HTTP RPC API.
     * @param skip Optional. The response paging information, number of records to skip.
     * @param take Optional. The response paging information, number of records to return, the actual number may be less than
     * requested.
     * @param orderBy Optional. Specifies how to order the results. Can be one of 'NONE', 'TIMESTAMP', 'CATEGORY', 'SCHEME_CODE_NAME',
     * 'ALIAS', 'MASTER_KEY_ALIAS', 'EXTERNAL_ID', 'ID', 'TIMESTAMP_DESC', 'CATEGORY_DESC', 'SCHEME_CODE_NAME_DESC', 'ALIAS_DESC',
     * 'MASTER_KEY_ALIAS_DESC', 'EXTERNAL_ID_DESC', 'ID_DESC'.
     * @param category Optional. Category of the HSM which handles the keys. Can be one of 'ACCOUNTS', 'CI', 'LEDGER', 'NOTARY',
     * 'SESSION_INIT', 'TLS', 'JWT_KEY'.
     * @param schemeCodeName Optional. The keys' signature scheme name. For example, 'CORDA.RSA', 'CORDA.ECDSA.SECP256K1',
     * 'CORDA.ECDSA_SECP256R1', 'CORDA.EDDSA.ED25519', 'CORDA.SPHINCS-256'.
     * @param alias Optional. The alias under which the key is stored.
     * @param masterKeyAlias Optional. The alias of the wrapping key.
     * @param createdAfter Optional. Only include keys which were created on or after the specified time. Must be a
     * valid instant in UTC, such as 2022-12-03T10:15:30.00Z.
     * @param createdBefore Optional. Only include keys which were created on or before the specified time. Must be a
     * valid instant in UTC, such as 2022-12-03T10:15:30.00Z.
     * @param ids Optional. Only retrieve keys associated with the specified list of key IDs. If specified, other filter
     * parameters will be ignored.
     *
     * @return A map of key IDs to the respective key information ([KeyMetaData]).
     */
    @HttpRpcGET(
        path = "{tenantId}",
        description = "Enables you to retrieve a list of keys belonging to a tenant.",
        responseDescription = "A map of key IDs to the respective key information."
    )
    @Suppress("LongParameterList")
    fun listKeys(
        @HttpRpcPathParameter(description = "'p2p', 'rpc-api', or holding identity ID.")
        tenantId: String,
        @HttpRpcQueryParameter(
            description = "The response paging information, number of records to skip.",
            default = "0",
            required = false,
        )
        skip: Int,
        @HttpRpcQueryParameter(
            description = "The response paging information, number of records to return, the actual number may be less than requested.",
            default = "20",
            required = false,
        )
        take: Int,
        @HttpRpcQueryParameter(
            description = "Specifies how to order the results. Can be one of 'NONE', 'TIMESTAMP', 'CATEGORY', 'SCHEME_CODE_NAME', 'ALIAS', 'MASTER_KEY_ALIAS', 'EXTERNAL_ID', 'ID', 'TIMESTAMP_DESC', 'CATEGORY_DESC', 'SCHEME_CODE_NAME_DESC', 'ALIAS_DESC', 'MASTER_KEY_ALIAS_DESC', 'EXTERNAL_ID_DESC', 'ID_DESC'.",
            default = "none",
            required = false,
        )
        orderBy: String,
        @HttpRpcQueryParameter(
            description = "Category of the HSM which handles the keys. Can be one of 'ACCOUNTS', 'CI', 'LEDGER', 'NOTARY', 'SESSION_INIT', 'TLS', 'JWT_KEY'.",
            required = false,
        )
        category: String?,
        @HttpRpcQueryParameter(
            description = "The keys' signature scheme name. For example, 'CORDA.RSA', 'CORDA.ECDSA.SECP256K1', 'CORDA.ECDSA_SECP256R1', 'CORDA.EDDSA.ED25519', 'CORDA.SPHINCS-256'.",
            required = false,
        )
        schemeCodeName: String?,
        @HttpRpcQueryParameter(
            description = "The alias under which the key is stored.",
            required = false,
        )
        alias: String?,
        @HttpRpcQueryParameter(
            description = "The alias of the wrapping key.",
            required = false,
        )
        masterKeyAlias: String?,
        @HttpRpcQueryParameter(
            description = "Only include keys which were created on or after the specified time. Must be a valid instant in UTC, such as 2022-12-03T10:15:30.00Z.",
            required = false,
        )
        createdAfter: String?,
        @HttpRpcQueryParameter(
            description = "Only include keys which were created on or before the specified time. Must be a valid instant in UTC, such as 2022-12-03T10:15:30.00Z.",
            required = false,
        )
        createdBefore: String?,
        @HttpRpcQueryParameter(
            description = "Only retrieve keys associated with the specified list of key IDs. If specified, other filter parameters will be ignored.",
            required = false,
            name = "id",
        )
        ids: List<String>?,
    ): Map<String, KeyMetaData>

    /**
     * The [generateKeyPair] method enables you to generate a new key pair for a tenant. The key pair is generated for
     * the specified HSM category under the given alias. The type of the new key is determined by the [scheme] value.
     *
     * Example usage:
     * ```
     * keysOps.generateKeyPair(tenantId = "58B6030FABDD", alias = "alias", hsmCategory = "TLS", scheme = "CORDA.RSA")
     * ```
     *
     * @param tenantId Can either be a holding identity ID, the value 'p2p' for a cluster-level tenant of the P2P
     * services, or the value 'rpc-api' for a cluster-level tenant of the HTTP RPC API.
     * @param alias The alias under which the new key will be stored.
     * @param hsmCategory Category of the HSM which handles the keys. Can be one of 'ACCOUNTS', 'CI', 'LEDGER', 'NOTARY',
     * 'SESSION_INIT', 'TLS', 'JWT_KEY'.
     * @param scheme The key's scheme describing which type of the key to generate. For example, 'CORDA.RSA', 'CORDA.ECDSA.SECP256K1',
     * 'CORDA.ECDSA_SECP256R1', 'CORDA.EDDSA.ED25519', 'CORDA.SPHINCS-256'.
     *
     * @return The ID of the newly generated key pair in the form of [KeyPairIdentifier].
     */
    @HttpRpcPOST(
        path = "{tenantId}/alias/{alias}/category/{hsmCategory}/scheme/{scheme}",
        description = "Enables you to generate a new key pair for a tenant.",
        responseDescription = "The ID of the newly generated key pair."
    )
    fun generateKeyPair(
        @HttpRpcPathParameter(description = "'p2p', 'rpc-api', or holding identity ID.")
        tenantId: String,
        @HttpRpcPathParameter(
            description = "The alias under which the new key will be stored."
        )
        alias: String,
        @HttpRpcPathParameter(
            description = "Category of the HSM which handles the keys. Can be one of 'ACCOUNTS', 'CI', 'LEDGER', 'NOTARY', 'SESSION_INIT', 'TLS', 'JWT_KEY'."
        )
        hsmCategory: String,
        @HttpRpcPathParameter(
            description = "The key's scheme describing which type of the key to generate. For example, 'CORDA.RSA', 'CORDA.ECDSA.SECP256K1', 'CORDA.ECDSA_SECP256R1', 'CORDA.EDDSA.ED25519', 'CORDA.SPHINCS-256'."
        )
        scheme: String
    ): KeyPairIdentifier

    /**
     * The [generateKeyPem] method enables you to retrieve a tenant's public key in PEM format. This method assumes that
     * a key associated with the specified [keyId] already exists.
     *
     * Example usage:
     * ```
     * keysOps.generateKeyPem(tenantId = "58B6030FABDD", keyId = "3B9A266F96E2")
     * ```
     *
     * @param tenantId Can either be a holding identity ID, the value 'p2p' for a cluster-level tenant of the P2P
     * services, or the value 'rpc-api' for a cluster-level tenant of the HTTP RPC API.
     * @param keyId Identifier of the key to be retrieved.
     *
     * @return The public key in PEM format.
     */
    @HttpRpcGET(
        path = "{tenantId}/{keyId}",
        description = "Enables you to retrieve a tenant's public key in PEM format.",
        responseDescription = "The public key in PEM format."
    )
    fun generateKeyPem(
        @HttpRpcPathParameter(description = "'p2p', 'rpc-api', or holding identity ID.")
        tenantId: String,
        @HttpRpcPathParameter(description = "Identifier of the key to be retrieved.")
        keyId: String,
    ): String
}
