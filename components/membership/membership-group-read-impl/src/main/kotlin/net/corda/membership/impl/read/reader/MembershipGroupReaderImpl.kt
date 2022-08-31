package net.corda.membership.impl.read.reader

import net.corda.membership.impl.read.cache.MembershipGroupReadCache
import net.corda.membership.lib.CPIWhiteList
import net.corda.membership.lib.MemberInfoExtension.Companion.MEMBER_STATUS_PENDING
import net.corda.membership.lib.MemberInfoExtension.Companion.ledgerKeyHashes
import net.corda.membership.lib.MemberInfoExtension.Companion.sessionKeyHash
import net.corda.membership.lib.MemberInfoExtension.Companion.status
import net.corda.membership.read.MembershipGroupReader
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.crypto.PublicKeyHash
import net.corda.v5.membership.GroupParameters
import net.corda.v5.membership.MemberInfo
import net.corda.virtualnode.HoldingIdentity

class MembershipGroupReaderImpl(
    private val holdingIdentity: HoldingIdentity,
    private val membershipGroupReadCache: MembershipGroupReadCache
) : MembershipGroupReader {
    override val groupId: String = holdingIdentity.groupId
    override val owningMember: MemberX500Name = holdingIdentity.x500Name

    private val memberList: List<MemberInfo>
        get() = membershipGroupReadCache.memberListCache.get(holdingIdentity)
            ?: throw IllegalStateException(
                "Failed to find member list for ID='${holdingIdentity.shortHash}, Group ID='${holdingIdentity.groupId}'"
            )

    override val groupParameters: GroupParameters
        get() = TODO("Not yet implemented")
    override val cpiWhiteList: CPIWhiteList
        get() = TODO("Not yet implemented")

    override fun lookup(): Collection<MemberInfo> = memberList.filter { it.isActiveOrPending() }

    override fun lookupByLedgerKey(ledgerKeyHash: PublicKeyHash): MemberInfo? =
        memberList.singleOrNull { it.isActiveOrPending() && ledgerKeyHash in it.ledgerKeyHashes }

    override fun lookupBySessionKey(sessionKeyHash: PublicKeyHash): MemberInfo? {
        println("QQQ lookupBySessionKey -> $sessionKeyHash for ${holdingIdentity.x500Name}")
        memberList.forEach {
            println("QQQ for ${it.name} -> ${it.sessionKeyHash} (${it.sessionKeyHash == sessionKeyHash})")
        }
        return memberList.singleOrNull { it.isActiveOrPending() && sessionKeyHash == it.sessionKeyHash }
    }


    override fun lookup(name: MemberX500Name) = memberList.singleOrNull {
        it.isActiveOrPending() && it.name == name
    }


    private fun MemberInfo.isActiveOrPending(): Boolean {
        return isActive || status == MEMBER_STATUS_PENDING
    }
}
