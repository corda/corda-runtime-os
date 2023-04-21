package net.corda.membership.read

import net.corda.lifecycle.Lifecycle
import net.corda.virtualnode.HoldingIdentity

/**
 * Provides read-only access to group membership information via a [MembershipGroupReader] instance.
 */
interface MembershipGroupReaderProvider: Lifecycle {
    /**
     * Returns a singleton instance of [MembershipGroupReader] for a given group ID and MemberX500Name which gives
     * access to that holding identity's view on the group.
     *
     * @param holdingIdentity The [HoldingIdentity] of the member whose view on data the caller is requesting
     */
    fun getGroupReader(holdingIdentity: HoldingIdentity): MembershipGroupReader
}
