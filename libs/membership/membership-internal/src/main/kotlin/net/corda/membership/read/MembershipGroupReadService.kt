package net.corda.membership.read

import net.corda.v5.membership.identity.MemberX500Name

/**
 * Provides read-only access to group membership information.
 */
interface MembershipGroupReadService {
    /**
     * Returns a singleton instance of [MembershipGroupReader] for a given group ID and MemberX500Name which gives
     * access to that holding identity's view on the group.
     *
     * @param groupId The group identifier on the group the caller is requesting a view on.
     * @param memberX500Name [MemberX500Name] of the member whose view on the group the caller is requesting.
     */
    fun getGroupReader(groupId: String, memberX500Name: MemberX500Name): MembershipGroupReader
}
