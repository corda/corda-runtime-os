package net.corda.membership.impl.read.reader

import net.corda.crypto.cipher.suite.PublicKeyHash
import net.corda.data.p2p.app.MembershipStatusFilter
import net.corda.membership.impl.read.cache.MembershipGroupReadCache
import net.corda.membership.lib.MemberInfoExtension.Companion.MEMBER_STATUS_ACTIVE
import net.corda.membership.lib.MemberInfoExtension.Companion.MEMBER_STATUS_PENDING
import net.corda.membership.lib.MemberInfoExtension.Companion.MEMBER_STATUS_SUSPENDED
import net.corda.membership.lib.MemberInfoExtension.Companion.ledgerKeyHashes
import net.corda.membership.lib.MemberInfoExtension.Companion.sessionKeyHash
import net.corda.membership.lib.MemberInfoExtension.Companion.status
import net.corda.membership.read.GroupParametersReaderService
import net.corda.membership.read.MembershipGroupReader
import net.corda.membership.read.NotaryVirtualNodeLookup
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.membership.GroupParameters
import net.corda.v5.membership.MemberInfo
import net.corda.virtualnode.HoldingIdentity

class MembershipGroupReaderImpl(
    private val holdingIdentity: HoldingIdentity,
    private val membershipGroupReadCache: MembershipGroupReadCache,
    private val groupParametersReaderService: GroupParametersReaderService,
) : MembershipGroupReader {
    override val groupId: String = holdingIdentity.groupId
    override val owningMember: MemberX500Name = holdingIdentity.x500Name

    private val memberList: List<MemberInfo>
        get() = membershipGroupReadCache.memberListCache.get(holdingIdentity)
            ?: throw IllegalStateException(
                "Failed to find member list for ID='${holdingIdentity.shortHash}, Group ID='${holdingIdentity.groupId}'"
            )

    override val groupParameters: GroupParameters?
        get() = groupParametersReaderService.get(holdingIdentity)

    override fun lookup(filter: MembershipStatusFilter): Collection<MemberInfo> =
        memberList.filterBy(filter)

    override fun lookupByLedgerKey(ledgerKeyHash: PublicKeyHash, filter: MembershipStatusFilter): MemberInfo? =
        memberList.filterBy(filter).singleOrNull { ledgerKeyHash in it.ledgerKeyHashes }

    override fun lookupBySessionKey(sessionKeyHash: PublicKeyHash, filter: MembershipStatusFilter): MemberInfo? =
        memberList.filterBy(filter).singleOrNull { sessionKeyHash == it.sessionKeyHash }

    override val notaryVirtualNodeLookup: NotaryVirtualNodeLookup by lazy {
        NotaryVirtualNodeLookupImpl(this)
    }

    override fun lookup(name: MemberX500Name, filter: MembershipStatusFilter): MemberInfo? =
        memberList.filterBy(filter).singleOrNull { it.name == name }

    private fun List<MemberInfo>.filterBy(filter: MembershipStatusFilter): List<MemberInfo> {
        return when (filter) {
            MembershipStatusFilter.PENDING -> this.filter { it.status == MEMBER_STATUS_PENDING }
            MembershipStatusFilter.ACTIVE -> this.filter { it.status == MEMBER_STATUS_ACTIVE }
            MembershipStatusFilter.ACTIVE_IF_PRESENT_OR_PENDING ->
                this.groupBy { it.name }.flatMap { memberEntry ->
                    memberEntry.value.filterBy(MembershipStatusFilter.ACTIVE).ifEmpty {
                        memberEntry.value.filterBy(MembershipStatusFilter.PENDING)
                    }
                }
            else -> this.filter { it.status == MEMBER_STATUS_ACTIVE || it.status == MEMBER_STATUS_SUSPENDED }
        }
    }
}
