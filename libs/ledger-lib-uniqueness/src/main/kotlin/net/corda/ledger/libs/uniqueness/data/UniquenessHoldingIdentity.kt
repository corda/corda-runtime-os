package net.corda.ledger.libs.uniqueness.data

import net.corda.crypto.core.ShortHash
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.crypto.SecureHash

/**
 * Alternative version of [net.corda.virtualnode.HoldingIdentity] to allow for separation from the `virtual-node-info` module without larger
 * scale refactoring of the rest of the codebase.
 */
data class UniquenessHoldingIdentity
    (
    val x500Name: MemberX500Name,
    val groupId: String,
    /**
     * Returns the holding identity as the first 12 characters of a SHA-256 hash of the x500 name and group id.
     *
     * To be used as a short look-up code that may be passed back to a customer as part of a URL etc.
     */
    val shortHash: ShortHash,
    /**
     * Returns the [SecureHash] of the holding identity: a SHA-256 hash of the x500 name and group.
     */
    val hash: SecureHash
) {

    override fun toString(): String {
        // Matches the [toString] of [net.corda.virtualnode.HoldingIdentity]
        return "HoldingIdentity(x500Name=$x500Name, groupId='$groupId')"
    }


}