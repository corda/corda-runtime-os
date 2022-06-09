package net.corda.membership.httprpc.v1.types.response

import java.time.Instant

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
    /**
     * The key scheme
     */
    val scheme: String,
    /**
     * The key master key alias
     */
    val masterKeyAlias: String?,
    /**
     * When was the key created
     */
    val created: Instant,
)
