package net.corda.membership.read

import net.corda.membership.CPIWhiteList
import net.corda.v5.crypto.PublicKeyHash
import net.corda.v5.membership.GroupParameters
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.membership.MemberInfo

/**
 * Provides group information for a specific group and member. The view of group information is for the member
 * represented by [owningMember] within the group represented by [groupId]
 */
interface MembershipGroupReader {
    /**
     * The ID of the group for which this service provides data on.
     */
    val groupId: String

    /**
     * The member X500 name for which this service provides data on.
     */
    val owningMember: MemberX500Name

    /**
     * The current group parameters for the group represented by [groupId].
     */
    val groupParameters: GroupParameters

    /**
     * The CPI whitelist for the group represented by [groupId].
     */
    val cpiWhiteList: CPIWhiteList

    /**
     * Returns a list of all visible [MemberInfo]s for the member represented by [owningMember]
     * having active membership status within the group represented by [groupId].
     */
    fun lookup(): Collection<MemberInfo>

    /**
     * Looks up a group member matching the public key SHA-256 hash as visible by the member represented
     * by [owningMember] having active membership status within the group represented by [groupId].
     *
     * If the member is not found then the null value is returned.
     *
     * @param publicKeyHash Public key hash for the member to lookup.
     */
    fun lookup(publicKeyHash: PublicKeyHash): MemberInfo?

    /**
     * Looks up a group member matching the [MemberX500Name] as visible by the member represented
     * by [owningMember] having active membership status within the group represented by [groupId].
     *
     * If the member is not found then the null value is returned.
     *
     * @param name MemberX500Name of the member to lookup.
     */
    fun lookup(name: MemberX500Name): MemberInfo?
}
