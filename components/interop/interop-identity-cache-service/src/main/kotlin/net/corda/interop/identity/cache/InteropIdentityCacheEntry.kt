package net.corda.interop.identity.cache

import net.corda.data.interop.InteropIdentity


/**
 * This class must have a valid equals() method.
 */
data class InteropIdentityCacheEntry(
    val x500Name: String,
    val groupId: String,
    val holdingIdentityShortHash: String
) {
    companion object {
        fun of(interopIdentity: InteropIdentity): InteropIdentityCacheEntry {
            return InteropIdentityCacheEntry(
                interopIdentity.x500Name,
                interopIdentity.groupId,
                interopIdentity.holdingIdentityShortHash
            )
        }
    }
}
