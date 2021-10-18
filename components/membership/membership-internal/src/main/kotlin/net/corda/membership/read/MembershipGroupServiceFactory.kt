package net.corda.membership.read

import net.corda.v5.membership.identity.MemberX500Name

/**
 * Factory for creating [MembershipGroupService] for a holding identity (group ID & MemberX500Name).
 */
interface MembershipGroupServiceFactory {
    /**
     * Returns a group information service providing group information for the
     * specified group as viewed by the specified member.
     *
     * @param groupId String containing the group identifier.
     * @param name MemberX500Name of the member requesting the group policy.
     */
    fun getGroupService(groupId: String, name: MemberX500Name): MembershipGroupService?
}
