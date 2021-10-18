package net.corda.membership.impl

import net.corda.membership.GroupPolicy
import net.corda.membership.MembershipGroupService
import net.corda.v5.application.identity.CordaX500Name
import net.corda.v5.membership.identity.MemberInfo

class MembershipGroupServiceImpl(
    override val groupId: String,
    override val requestingMember: CordaX500Name,
    override val policy: GroupPolicy
) : MembershipGroupService {

    override fun lookupMember(lookupMember: ByteArray): MemberInfo? {
        TODO("Not yet implemented")
    }

    override fun lookupMember(lookupMember: CordaX500Name): MemberInfo? {
        TODO("Not yet implemented")
    }
}
