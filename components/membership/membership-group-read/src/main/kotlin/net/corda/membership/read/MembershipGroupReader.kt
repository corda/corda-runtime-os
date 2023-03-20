package net.corda.membership.read

import net.corda.crypto.cipher.suite.PublicKeyHash
import net.corda.data.p2p.app.MembershipStatusFilter
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
     * within the group represented by [groupId] filtered by [filter].
     * [filter] should be only used by the P2P and membership layers. Everywhere else we must use the default value.
     *
     * @param filter Indicate what statuses you are looking for. By default, it will return the latest
     * active version.
     */
    fun lookup(filter: MembershipStatusFilter = MembershipStatusFilter.ACTIVE): Collection<MemberInfo>

    /**
     * Looks up a group member matching the public key SHA-256 hash as visible by the member represented
     * by [owningMember] within the group represented by [groupId] filtered by [filter].
     * [filter] should be only used by the P2P and membership layers. Everywhere else we must use the default value.
     *
     * If the member is not found then the null value is returned.
     *
     * @param ledgerKeyHash Hash of the ledger key belonging to the member to be looked up.
     * @param filter Indicates what statuses you are looking for. By default, it will return the latest
     * active version.
     */
    fun lookupByLedgerKey(
        ledgerKeyHash: PublicKeyHash,
        filter: MembershipStatusFilter = MembershipStatusFilter.ACTIVE
    ): MemberInfo?

    /**
     * Looks up a group member matching the [MemberX500Name] as visible by the member represented
     * by [owningMember] within the group represented by [groupId] filtered by [filter].
     * [filter] should be only used by the P2P and membership layers. Everywhere else we must use the default value.
     *
     * If the member is not found then the null value is returned.
     *
     * @param name MemberX500Name of the member to lookup.
     * @param filter Indicates what statuses you are looking for. By default, it will return the latest
     * active version.
     */
    fun lookup(name: MemberX500Name, filter: MembershipStatusFilter = MembershipStatusFilter.ACTIVE): MemberInfo?

    /**
     * Looks up a group member matching the public key SHA-256 hash as visible by the member represented
     * by [owningMember] within the group represented by [groupId] filtered by [filter].
     * [filter] should be only used by the P2P and membership layers. Everywhere else we must use the default value.
     *
     * If the member is not found then the null value is returned.
     *
     * @param sessionKeyHash Hash of the session key belonging to the member to be looked up.
     * @param filter Indicates what statuses you are looking for. By default, it will return the latest
     * active version.
     */
    fun lookupBySessionKey(
        sessionKeyHash: PublicKeyHash,
        filter: MembershipStatusFilter = MembershipStatusFilter.ACTIVE
    ): MemberInfo?

    /**
     * A service to lookup of a notary virtual nodes in the group.
     */
    val notaryVirtualNodeLookup: NotaryVirtualNodeLookup
}
