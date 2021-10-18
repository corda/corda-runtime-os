package net.corda.membership.impl

import net.corda.membership.MembershipGroupStorageService
import net.corda.membership.MembershipGroupStorageServiceFactory
import net.corda.v5.membership.identity.MemberX500Name

class MembershipGroupStorageServiceFactoryImpl : MembershipGroupStorageServiceFactory {

    override fun getGroupStorageService(groupId: String, name: MemberX500Name): MembershipGroupStorageService? {
        TODO("Not yet implemented")
    }
}
