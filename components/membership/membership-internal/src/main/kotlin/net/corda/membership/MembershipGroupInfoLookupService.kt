package net.corda.membership

import net.corda.v5.application.identity.CordaX500Name

/**
 * Provides access to a member's group information service
 */
interface MembershipGroupInfoLookupService {
    /**
     * Retuurns a group information service providing group information for the
     * specified group as viewed by the specified member.
     *
     * @param groupId String containing the group identifier.
     * @param name CordaX500Name of the member requesting the group policy.
     */
    fun getGroupInfoService(groupId: String, name: CordaX500Name): MembershipGroupInfoService?
}

