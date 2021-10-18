package net.corda.membership.impl

import net.corda.membership.MembershipGroupService
import net.corda.membership.MembershipGroupServiceFactory
import net.corda.v5.membership.identity.MemberX500Name

class MembershipGroupServiceFactoryImpl : MembershipGroupServiceFactory {

    override fun getGroupService(groupId: String, name: MemberX500Name): MembershipGroupService? {
        TODO("Not yet implemented")
    }
}
