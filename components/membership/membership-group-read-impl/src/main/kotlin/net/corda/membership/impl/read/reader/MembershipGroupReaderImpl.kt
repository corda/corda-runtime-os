package net.corda.membership.impl.read.reader

import net.corda.membership.CPIWhiteList
import net.corda.membership.GroupPolicy
import net.corda.membership.impl.MemberInfoExtension.Companion.identityKeyHashes
import net.corda.membership.impl.read.cache.MembershipGroupReadCache
import net.corda.membership.read.MembershipGroupReader
import net.corda.v5.crypto.PublicKeyHash
import net.corda.v5.membership.GroupParameters
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.membership.MemberInfo
import net.corda.virtualnode.HoldingIdentity

class MembershipGroupReaderImpl(
    private val holdingIdentity: HoldingIdentity,
    override val policy: GroupPolicy,
    private val membershipGroupReadCache: MembershipGroupReadCache
) : MembershipGroupReader {
    override val groupId: String = holdingIdentity.groupId
    override val owningMember: MemberX500Name = MemberX500Name.parse(holdingIdentity.x500Name)

    private val memberList: List<MemberInfo>
        get() = membershipGroupReadCache.memberListCache.get(holdingIdentity)!!

    override val groupParameters: GroupParameters
        get() = TODO("Not yet implemented")
    override val cpiWhiteList: CPIWhiteList
        get() = TODO("Not yet implemented")

    override fun lookup(publicKeyHash: PublicKeyHash): MemberInfo? =
        memberList.singleOrNull { publicKeyHash in it.identityKeyHashes }

    override fun lookup(name: MemberX500Name) = memberList.singleOrNull { it.name == name }
}