package net.corda.membership.impl

import net.corda.membership.GroupPolicy
import net.corda.membership.MembershipGroupInfoService
import net.corda.v5.application.identity.CordaX500Name
import net.corda.v5.application.node.MemberInfo

class MembershipGroupInfoServiceImpl(
    override val groupId: String,
    override val requestingMember: CordaX500Name,
    override val policy: GroupPolicy
) : MembershipGroupInfoService {

    override fun lookupMember(lookupMember: ByteArray): MemberInfo? {
        TODO("Not yet implemented")
    }

    override fun lookupMember(lookupMember: CordaX500Name): MemberInfo? {
        TODO("Not yet implemented")
    }
}
