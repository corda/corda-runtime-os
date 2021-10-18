package net.corda.membership

import net.corda.v5.membership.identity.MemberX500Name

/**
 * Factory for creating [MembershipGroupStorageService] for a holding identity (group ID & MemberX500Name).
 */
interface MembershipGroupStorageServiceFactory {
    /**
     * TODO
     *
     * @param groupId String containing the group identifier.
     * @param name MemberX500Name of the member requesting the group policy.
     */
    fun getGroupStorageService(groupId: String, name: MemberX500Name): MembershipGroupStorageService?
}
