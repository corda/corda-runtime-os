package net.corda.membership.impl.read

import net.corda.membership.GroupPolicy
import net.corda.membership.read.MembershipGroupService
import net.corda.v5.membership.identity.MemberInfo
import net.corda.v5.membership.identity.MemberX500Name

class MembershipGroupServiceImpl(
    override val groupId: String,
    override val requestingMember: MemberX500Name,
    override val policy: GroupPolicy
) : MembershipGroupService {

    override fun lookupMember(lookupMember: ByteArray): MemberInfo? {
        TODO("Not yet implemented")
    }

    override fun lookupMember(name: MemberX500Name): MemberInfo? {
        TODO("Not yet implemented")
    }
}
