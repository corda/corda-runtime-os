package net.corda.interop.core

import net.corda.data.interop.PersistentInteropIdentity


/**
 * This class must have a valid equals() method.
 */
data class InteropIdentity(
    val x500Name: String,
    val groupId: String,
    val holdingIdentityShortHash: String
) {
    val shortHash = Utils.computeShortHash(x500Name, groupId)

    companion object {
        fun of(holdingIdentityShortHash: String, interopIdentity: PersistentInteropIdentity): InteropIdentity {
            return InteropIdentity(
                interopIdentity.x500Name,
                interopIdentity.groupId,
                holdingIdentityShortHash
            )
        }
    }
}
