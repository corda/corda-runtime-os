package net.corda.membership.httprpc.v1.types.response

import java.time.Instant

/**
 * Data class representing a PreAuthToken
 */
data class PreAuthToken(
    val id: String,
    val ownerX500Name: String,
    val ttl: Instant,
    val status: PreAuthTokenStatus,
    val remarks: String?
)

enum class PreAuthTokenStatus {
    /** Valid for use by [PreAuthToken.ownerX500Name] */
    AVAILABLE,

    /** Revoked by the MGM operator */
    REVOKED,

    /** Consumed during registration by [PreAuthToken.ownerX500Name] */
    CONSUMED,

    /** Invalidated by the system due to a security incident */
    AUTO_INVALIDATED
}
