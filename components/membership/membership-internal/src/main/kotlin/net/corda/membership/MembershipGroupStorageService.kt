package net.corda.membership

import net.corda.v5.membership.identity.MemberX500Name

/**
 * Provides write access for group information for a specific group and member.
 */
interface MembershipGroupStorageService {
    /**
     * The ID of the group for which this service provides functionality for.
     */
    val groupId: String

    /**
     * The member for which this group information is visible for.
     */
    val requestingMember: MemberX500Name

    /**
     * TO DO: Define write interface
     */
}
