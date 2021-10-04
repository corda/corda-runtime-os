package net.corda.membership

import net.corda.v5.application.identity.CordaX500Name
import net.corda.v5.application.node.MemberInfo

/**
 * Provides group information for a specific group and member. The view of group information may vary
 */
interface MembershipGroupInfoService {
    /**
     * The ID of the group for which this service provides functionality for.
     */
    val groupId: String

    /**
     * The member for which this group information is visible for.
     */
    val requestingMember: CordaX500Name

    /**
     * The group policy information for this group.
     */
    val policy: GroupPolicy

    /**
     * Looks up a group member of the specified group by the public key SHA-256 hash
     * belonging to the member.
     * If the member is not found then the null value is returned.
     *
     * @param lookupMember Public key hash as a ByteArray for the member to lookup.
     */
    fun lookupMember(lookupMember: ByteArray): MemberInfo?

    /**
     * Looks up a group member of the specified group by the public key SHA-256 hash
     * belonging to the member.
     * If the member is not found then the null value is returned.
     *
     * @param lookupMember CordaX500Name of the member to lookup.
     */
    fun lookupMember(lookupMember: CordaX500Name): MemberInfo?
}
