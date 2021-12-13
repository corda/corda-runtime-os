package net.corda.membership.impl.read

import net.corda.membership.CPIWhiteList
import net.corda.membership.GroupPolicy
import net.corda.membership.impl.read.cache.MemberListCache
import net.corda.membership.read.MembershipGroupReader
import net.corda.v5.membership.GroupParameters
import net.corda.v5.membership.identity.MemberInfo
import net.corda.v5.membership.identity.MemberX500Name

class MembershipGroupReaderImpl(
    override val groupId: String,
    override val owningMember: MemberX500Name,
    override val policy: GroupPolicy,
    private val memberListCache: MemberListCache
) : MembershipGroupReader {

    private val memberList: List<MemberInfo>
        get() = memberListCache.get(groupId, owningMember)!!

    override val groupParameters: GroupParameters
        get() = TODO("Not yet implemented")
    override val cpiWhiteList: CPIWhiteList
        get() = TODO("Not yet implemented")

    override fun lookup(publicKeyHash: ByteArray): MemberInfo? {
        TODO("Not yet implemented")
    }

    override fun lookup(name: MemberX500Name): MemberInfo? =
        memberList.singleOrNull {
            it.name == name
        }
}