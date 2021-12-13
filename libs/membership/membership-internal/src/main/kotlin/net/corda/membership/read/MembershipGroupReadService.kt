package net.corda.membership.read

import net.corda.v5.membership.identity.MemberX500Name

/**
 * Provides read-only access to group membership information.
 */
interface MembershipGroupReadService {
    /**
     * Returns a singleton instance of [MembershipGroupReader] for a given group ID and MemberX500Name.
     *
     * @param groupId String containing the group identifier.
     * @param name MemberX500Name of the member requesting the group policy.
     */
    fun getGroupReader(groupId: String, name: MemberX500Name): MembershipGroupReader
}
