package net.corda.membership.impl.read.reader

import net.corda.membership.lib.impl.MemberInfoExtension.Companion.ledgerKeyHashes
import net.corda.membership.lib.impl.MemberInfoExtension.Companion.sessionKeyHash
import net.corda.membership.lib.CPIWhiteList
import net.corda.membership.impl.read.cache.MembershipGroupReadCache
import net.corda.membership.read.MembershipGroupReader
import net.corda.v5.crypto.PublicKeyHash
import net.corda.v5.membership.GroupParameters
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.membership.MemberInfo
import net.corda.virtualnode.HoldingIdentity

class MembershipGroupReaderImpl(
    private val holdingIdentity: HoldingIdentity,
    private val membershipGroupReadCache: MembershipGroupReadCache
) : MembershipGroupReader {
    override val groupId: String = holdingIdentity.groupId
    override val owningMember: MemberX500Name = MemberX500Name.parse(holdingIdentity.x500Name)

    private val memberList: List<MemberInfo>
        get() = membershipGroupReadCache.memberListCache.get(holdingIdentity)
            ?: throw IllegalStateException(
                "Failed to find member list for ID='${holdingIdentity.id}, Group ID='${holdingIdentity.groupId}'")

    override val groupParameters: GroupParameters
        get() = TODO("Not yet implemented")
    override val cpiWhiteList: CPIWhiteList
        get() = TODO("Not yet implemented")

    override fun lookup(): Collection<MemberInfo> = memberList.filter { it.isActive }

    override fun lookup(ledgerKeyHash: PublicKeyHash): MemberInfo? =
        memberList.singleOrNull { it.isActive && ledgerKeyHash in it.ledgerKeyHashes }

    override fun lookupBySessionKey(sessionKeyHash: PublicKeyHash): MemberInfo? =
        memberList.singleOrNull { it.isActive && sessionKeyHash == it.sessionKeyHash }

    override fun lookup(name: MemberX500Name) = memberList.singleOrNull { it.isActive && it.name == name }
}