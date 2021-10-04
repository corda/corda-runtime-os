package net.corda.membership.impl

import net.corda.membership.MembershipGroupInfoLookupService
import net.corda.membership.MembershipGroupInfoService
import net.corda.v5.application.identity.CordaX500Name

class MembershipGroupInfoLookupServiceImpl : MembershipGroupInfoLookupService {

    override fun getGroupInfoService(groupId: String, name: CordaX500Name): MembershipGroupInfoService? {
        TODO("Not yet implemented")
    }
}
