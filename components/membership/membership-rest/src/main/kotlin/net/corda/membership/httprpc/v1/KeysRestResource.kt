package net.corda.membership.httprpc.v1

import net.corda.httprpc.RestResource
import net.corda.httprpc.annotations.HttpGET
import net.corda.httprpc.annotations.HttpPOST
import net.corda.httprpc.annotations.RestPathParameter
import net.corda.httprpc.annotations.RestQueryParameter
import net.corda.httprpc.annotations.HttpRestResource
import net.corda.membership.httprpc.v1.types.response.KeyMetaData
import net.corda.membership.httprpc.v1.types.response.KeyPairIdentifier

/**
 * The Keys Management API consists of endpoints used to manage public and private key pairs. The API
 * allows you to list scheme codes which are supported by the associated HSM integration, retrieve information about
 * key pairs owned by a tenant, generate a key pair for a tenant, and retrieve a tenant's public key in PEM format.
 */
@HttpRestResource(
    name = "Keys Management API",
    description = "The Keys Management API consists of endpoints used to manage public and private key pairs. The API" +
            " allows you to list scheme codes which are supported by the associated HSM integration, retrieve" +
            " information about key pairs owned by a tenant, generate a key pair for a tenant, and retrieve a tenant's" +
            " public key in PEM format.",
    path = "keys"
)
interface KeysRestResource : RestResource {
    /**
     * The [listSchemes] method enables you to retrieve a list of supported key schemes for a specified tenant and HSM
     * category. Some examples of schemes are 'CORDA.RSA', 'CORDA.ECDSA.SECP256K1', 'CORDA.ECDSA.SECP256R1', 'CORDA.EDDSA.ED25519',
     * 'CORDA.SPHINCS-256'.
     *
     * Example usage:
     * ```
     * keysOps.listSchemes(tenantId = "58B6030FABDD", hsmCategory = "SESSION_INIT")
     *
     * keysOps.listSchemes(tenantId = "rpc-api", hsmCategory = "SESSION_INIT")
     * ```
     *
     * @param tenantId Can either be a holding identity ID, the value 'p2p' for a cluster-level tenant of the P2P
     * services, or the value 'rpc-api' for a cluster-level tenant of the HTTP RPC API.
     * @param hsmCategory Can be the value 'ACCOUNTS', 'CI', 'LEDGER', 'NOTARY', 'SESSION_INIT', 'TLS', or 'JWT_KEY'.
     *
     * @return The list of scheme codes which are supported by the associated HSM integration.
     */
    @HttpGET(
        path = "{tenantId}/schemes/{hsmCategory}",
        description = "This method retrieves a list of supported key schemes for a specified tenant and HSM category.",
        responseDescription = "The list of scheme codes which are supported by the associated HSM integration"
    )
    fun listSchemes(
        @RestPathParameter(description = "Can either be a holding identity ID, the value 'p2p' for a cluster-level" +
                " tenant of the P2P services, or the value 'rpc-api' for a cluster-level tenant of the HTTP RPC API")
        tenantId: String,
        @RestPathParameter(description = "The category of the HSM. Can be the value 'ACCOUNTS', 'CI', 'LEDGER', 'NOTARY'," +
                " 'SESSION_INIT', 'TLS', or 'JWT_KEY'")
        hsmCategory: String,
    ): Collection<String>

    /**
     * The [listKeys] method enables you to retrieve information about a list of key pairs belonging to a tenant.
     * The returned list may be filtered and/or ordered as required by passing a list of key IDs, or specifying one or
     * more of the optional parameters.
     *
     * Example usage:
     *
     * 1. Retrieve information about key pairs belonging to the tenant with holding identity ID '58B6030FABDD'.
     * Only return information about key pairs under the 'CI' HSM category, skip the first 4 key pair records and
     * return up to 400 key pair records, ordered according to their aliases.
     * ```
     * keysOps.listKeys(tenantId = "58B6030FABDD", skip = 4, take = 400, orderBy = "ALIAS", category = CI, alias = null,
     * masterKeyAlias = null, createdAfter = null, createdBefore = null, schemeCodeName = null, ids = emptyList())
     * ```
     * 2. Retrieve information about key pairs belonging to the 'p2p' tenant associated with the key IDs
     * '3B9A266F96E2' and '4A9A266F96E2'.
     * ```
     * keysOps.listKeys(tenantId = "p2p", skip = null, take = null, orderBy = null, category = null,
     * alias = null, masterKeyAlias = null, createdAfter = null, createdBefore = null, schemeCodeName = null,
     * ids = ["3B9A266F96E2", "4A9A266F96E2"])
     * ```
     *
     * @param tenantId Can either be a holding identity ID, the value 'p2p' for a cluster-level tenant of the P2P
     * services, or the value 'rpc-api' for a cluster-level tenant of the HTTP RPC API.
     * @param skip Optional. The response paging information, number of records to skip.
     * @param take Optional. The response paging information, that is, the number of records to return. The actual
     * number returned may be less than requested.
     * @param orderBy Optional. Specifies how to order the results. Can be one of 'NONE', 'TIMESTAMP', 'CATEGORY', 'SCHEME_CODE_NAME',
     * 'ALIAS', 'MASTER_KEY_ALIAS', 'EXTERNAL_ID', 'ID', 'TIMESTAMP_DESC', 'CATEGORY_DESC', 'SCHEME_CODE_NAME_DESC', 'ALIAS_DESC',
     * 'MASTER_KEY_ALIAS_DESC', 'EXTERNAL_ID_DESC', 'ID_DESC'.
     * @param category Optional. Category of the HSM which handles the key pairs. Can be one of 'ACCOUNTS', 'CI', 'LEDGER', 'NOTARY',
     * 'SESSION_INIT', 'TLS', 'JWT_KEY'.
     * @param schemeCodeName Optional. The key pairs' signature scheme name. For example, 'CORDA.RSA', 'CORDA.ECDSA.SECP256K1',
     * 'CORDA.ECDSA.SECP256R1', 'CORDA.EDDSA.ED25519', 'CORDA.SPHINCS-256'.
     * @param alias Optional. The alias under which the key pair is stored.
     * @param masterKeyAlias Optional. The alias of the wrapping key.
     * @param createdAfter Optional. Only include key pairs which were created on or after the specified time. Must be a
     * valid instant in UTC, such as 2022-12-03T10:15:30.00Z.
     * @param createdBefore Optional. Only include key pairs which were created on or before the specified time. Must be a
     * valid instant in UTC, such as 2022-12-03T10:15:30.00Z.
     * @param ids Optional. Only include key pairs associated with the specified list of key IDs. If specified, other filter
     * parameters will be ignored.
     *
     * @return A map of key IDs to the respective key pair information ([KeyMetaData]).
     */
    @HttpGET(
        path = "{tenantId}",
        description = "This method retrieves information about a list of key pairs belonging to a tenant.",
        responseDescription = "A map of key IDs to the respective key pair information"
    )
    @Suppress("LongParameterList")
    fun listKeys(
        @RestPathParameter(description = "Can either be a holding identity ID, the value 'p2p' for a cluster-level" +
                " tenant of the P2P services, or the value 'rpc-api' for a cluster-level tenant of the HTTP RPC API")
        tenantId: String,
        @RestQueryParameter(
            description = "The response paging information, number of records to skip",
            default = "0",
            required = false,
        )
        skip: Int,
        @RestQueryParameter(
            description = "The response paging information, that is, the number of records to return. The actual number" +
                    " returned may be less than requested.",
            default = "20",
            required = false,
        )
        take: Int,
        @RestQueryParameter(
            description = "Specifies how to order the results. Can be one of 'NONE', 'TIMESTAMP', 'CATEGORY'," +
                    " 'SCHEME_CODE_NAME', 'ALIAS', 'MASTER_KEY_ALIAS', 'EXTERNAL_ID', 'ID', 'TIMESTAMP_DESC'," +
                    " 'CATEGORY_DESC', 'SCHEME_CODE_NAME_DESC', 'ALIAS_DESC', 'MASTER_KEY_ALIAS_DESC', 'EXTERNAL_ID_DESC'," +
                    " 'ID_DESC'.",
            default = "none",
            required = false,
        )
        orderBy: String,
        @RestQueryParameter(
            description = "Category of the HSM which handles the key pairs. Can be one of 'ACCOUNTS', 'CI', 'LEDGER'," +
                    " 'NOTARY', 'SESSION_INIT', 'TLS', 'JWT_KEY'.",
            required = false,
        )
        category: String?,
        @RestQueryParameter(
            description = "The key pairs' signature scheme name. For example, 'CORDA.RSA', 'CORDA.ECDSA.SECP256K1'," +
                    " 'CORDA.ECDSA.SECP256R1', 'CORDA.EDDSA.ED25519', 'CORDA.SPHINCS-256'.",
            required = false,
        )
        schemeCodeName: String?,
        @RestQueryParameter(
            description = "The alias under which the key pair is stored",
            required = false,
        )
        alias: String?,
        @RestQueryParameter(
            description = "The alias of the wrapping key",
            required = false,
        )
        masterKeyAlias: String?,
        @RestQueryParameter(
            description = "Only include key pairs which were created on or after the specified time. Must be a valid instant" +
                    " in UTC, such as 2022-12-03T10:15:30.00Z.",
            required = false,
        )
        createdAfter: String?,
        @RestQueryParameter(
            description = "Only include key pairs which were created on or before the specified time. Must be a valid instant" +
                    " in UTC, such as 2022-12-03T10:15:30.00Z.",
            required = false,
        )
        createdBefore: String?,
        @RestQueryParameter(
            description = "Only include key pairs associated with the specified list of key IDs. If specified, other filter" +
                    " parameters will be ignored.",
            required = false,
            name = "id",
        )
        ids: List<String>?,
    ): Map<String, KeyMetaData>

    /**
     * The [generateKeyPair] method enables you to generate a new key pair for a tenant. The key pair is generated for
     * the specified HSM category under the given alias. The type of the new key pair is determined by the [scheme] value.
     *
     * Example usage:
     * ```
     * keysOps.generateKeyPair(tenantId = "58B6030FABDD", alias = "alias", hsmCategory = "TLS", scheme = "CORDA.RSA")
     *
     * keysOps.generateKeyPair(tenantId = "p2p", alias = "alias", hsmCategory = "TLS", scheme = "CORDA.RSA")
     * ```
     *
     * @param tenantId Can either be a holding identity ID, the value 'p2p' for a cluster-level tenant of the P2P
     * services, or the value 'rpc-api' for a cluster-level tenant of the HTTP RPC API.
     * @param alias The alias under which the new key pair will be stored.
     * @param hsmCategory Category of the HSM which handles the key pairs. Can be one of 'ACCOUNTS', 'CI', 'LEDGER', 'NOTARY',
     * 'SESSION_INIT', 'TLS', 'JWT_KEY'.
     * @param scheme The key's scheme describing which type of the key pair to generate. For example, 'CORDA.RSA',
     * 'CORDA.ECDSA.SECP256K1', 'CORDA.ECDSA.SECP256R1', 'CORDA.EDDSA.ED25519', 'CORDA.SPHINCS-256'.
     *
     * @return The ID of the newly generated key pair in the form of [KeyPairIdentifier].
     */
    @HttpPOST(
        path = "{tenantId}/alias/{alias}/category/{hsmCategory}/scheme/{scheme}",
        description = "This method generates a new key pair for a tenant.",
        responseDescription = "The ID of the newly generated key pair"
    )
    fun generateKeyPair(
        @RestPathParameter(description = "Can either be a holding identity ID, the value 'p2p' for a cluster-level" +
                " tenant of the P2P services, or the value 'rpc-api' for a cluster-level tenant of the HTTP RPC API")
        tenantId: String,
        @RestPathParameter(
            description = "The alias under which the new key pair will be stored"
        )
        alias: String,
        @RestPathParameter(
            description = "Category of the HSM which handles the key pairs. Can be one of 'ACCOUNTS', 'CI', 'LEDGER'," +
                    " 'NOTARY', 'SESSION_INIT', 'TLS', 'JWT_KEY'."
        )
        hsmCategory: String,
        @RestPathParameter(
            description = "The key's scheme describing which type of the key pair to generate. For example, 'CORDA.RSA'," +
                    " 'CORDA.ECDSA.SECP256K1', 'CORDA.ECDSA.SECP256R1', 'CORDA.EDDSA.ED25519', 'CORDA.SPHINCS-256'."
        )
        scheme: String
    ): KeyPairIdentifier

    /**
     * The [generateKeyPem] method enables you to retrieve a tenant's public key in PEM format. This method assumes that
     * a key pair associated with the specified [keyId] already exists.
     *
     * Example usage:
     * ```
     * keysOps.generateKeyPem(tenantId = "58B6030FABDD", keyId = "3B9A266F96E2")
     *
     * keysOps.generateKeyPem(tenantId = "rpc-api", keyId = "3B9A266F96E2")
     * ```
     *
     * @param tenantId Can either be a holding identity ID, the value 'p2p' for a cluster-level tenant of the P2P
     * services, or the value 'rpc-api' for a cluster-level tenant of the HTTP RPC API.
     * @param keyId Identifier of the public key to be retrieved.
     *
     * @return The public key in PEM format.
     */
    @HttpGET(
        path = "{tenantId}/{keyId}",
        description = "This method retrieves a tenant's public key in PEM format.",
        responseDescription = "The public key in PEM format"
    )
    fun generateKeyPem(
        @RestPathParameter(description = "Can either be a holding identity ID, the value 'p2p' for a cluster-level" +
                " tenant of the P2P services, or the value 'rpc-api' for a cluster-level tenant of the HTTP RPC API")
        tenantId: String,
        @RestPathParameter(description = "Identifier of the public key to be retrieved")
        keyId: String,
    ): String
}
