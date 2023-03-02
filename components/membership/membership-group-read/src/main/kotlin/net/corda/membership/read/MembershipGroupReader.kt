package net.corda.membership.read

import net.corda.crypto.cipher.suite.PublicKeyHash
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.membership.GroupParameters
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
    val groupParameters: GroupParameters?

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
     * @param ledgerKeyHash Hash of the ledger key belonging to the member to be looked up.
     */
    fun lookupByLedgerKey(ledgerKeyHash: PublicKeyHash): MemberInfo?

    /**
     * Looks up a group member matching the [MemberX500Name] as visible by the member represented
     * by [owningMember] having active membership status within the group represented by [groupId].
     *
     * If the member is not found then the null value is returned.
     *
     * @param name MemberX500Name of the member to lookup.
     */
    fun lookup(name: MemberX500Name): MemberInfo?

    /**
     * Looks up a group member matching the public key SHA-256 hash as visible by the member represented
     * by [owningMember] having active membership status within the group represented by [groupId].
     *
     * If the member is not found then the null value is returned.
     *
     * @param sessionKeyHash Hash of the session key belonging to the member to be looked up.
     */
    fun lookupBySessionKey(sessionKeyHash: PublicKeyHash): MemberInfo?

    /**
     * A service to lookup of a notary virtual nodes in the group.
     */
    val notaryVirtualNodeLookup: NotaryVirtualNodeLookup
}
