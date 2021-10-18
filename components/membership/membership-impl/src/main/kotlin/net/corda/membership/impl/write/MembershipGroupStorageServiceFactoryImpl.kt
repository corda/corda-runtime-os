package net.corda.membership.impl.write

import net.corda.membership.write.MembershipGroupStorageService
import net.corda.membership.write.MembershipGroupStorageServiceFactory
import net.corda.v5.membership.identity.MemberX500Name

class MembershipGroupStorageServiceFactoryImpl : MembershipGroupStorageServiceFactory {

    override fun getGroupStorageService(groupId: String, name: MemberX500Name): MembershipGroupStorageService? {
        TODO("Not yet implemented")
    }
}
