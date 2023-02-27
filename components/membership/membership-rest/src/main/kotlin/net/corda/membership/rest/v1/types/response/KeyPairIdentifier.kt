package net.corda.membership.rest.v1.types.response

/**
 * Data class that identifies a key pair.
 */
data class KeyPairIdentifier(
    /**
     * The ID of the newly generated key pair.
     */
    val id: String
)
