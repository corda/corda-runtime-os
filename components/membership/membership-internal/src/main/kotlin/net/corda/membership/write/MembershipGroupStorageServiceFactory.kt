package net.corda.membership.write

import net.corda.v5.membership.identity.MemberX500Name

/**
 * Factory for creating [MembershipGroupStorageService] for a holding identity (group ID & MemberX500Name).
 */
interface MembershipGroupStorageServiceFactory {
    /**
     * Returns a group storage service providing APIs to update group information for the specified group as viewed by
     * the specified member.
     *
     * @param groupId String containing the group identifier.
     * @param name MemberX500Name of the member requesting the group policy.
     */
    fun getGroupStorageService(groupId: String, name: MemberX500Name): MembershipGroupStorageService?
}
