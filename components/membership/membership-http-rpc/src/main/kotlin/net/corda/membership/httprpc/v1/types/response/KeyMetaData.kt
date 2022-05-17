package net.corda.membership.httprpc.v1.types.response

/**
 * Data class that describe a key meta data
 */
data class KeyMetaData(
    /**
     * The key ID
     */
    val keyId: String,
    /**
     * The key alias
     */
    val alias: String,
    /**
     * The key HSM category
     */
    val hsmCategory: String,
)
