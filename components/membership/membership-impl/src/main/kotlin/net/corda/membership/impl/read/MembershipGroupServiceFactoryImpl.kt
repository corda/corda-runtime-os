package net.corda.membership.impl.read

import net.corda.membership.read.MembershipGroupService
import net.corda.membership.read.MembershipGroupServiceFactory
import net.corda.v5.membership.identity.MemberX500Name

class MembershipGroupServiceFactoryImpl : MembershipGroupServiceFactory {

    override fun getGroupService(groupId: String, name: MemberX500Name): MembershipGroupService? {
        TODO("Not yet implemented")
    }
}
