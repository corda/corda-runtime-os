package net.corda.membership.read

import net.corda.membership.GroupPolicy
import net.corda.v5.membership.identity.MemberInfo
import net.corda.v5.membership.identity.MemberX500Name

/**
 * Provides group information for a specific group and member. The view of group information may vary based on the
 * requesting member.
 */
interface MembershipGroupService {
    /**
     * The ID of the group for which this service provides functionality for.
     */
    val groupId: String

    /**
     * The member for which this group information is visible for.
     */
    val requestingMember: MemberX500Name

    /**
     * The group policy information for this group.
     */
    val policy: GroupPolicy

    /**
     * Looks up a group member of the specified group by the public key SHA-256 hash
     * belonging to the member.
     * Returns null if the member is not found.
     *
     * @param lookupMember Public key hash as a ByteArray for the member to lookup.
     */
    fun lookupMember(lookupMember: ByteArray): MemberInfo?

    /**
     * Looks up a group member of the specified group by the MemberX500Name
     * belonging to the member.
     * Returns null if the member is not found.
     *
     * @param name [MemberX500Name] of the member to look up.
     */
    fun lookupMember(name: MemberX500Name): MemberInfo?
}
