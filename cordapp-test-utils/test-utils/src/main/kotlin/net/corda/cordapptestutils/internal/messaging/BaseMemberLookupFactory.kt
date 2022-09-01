package net.corda.cordapptestutils.internal.messaging

import net.corda.cordapptestutils.exceptions.NoSuchMemberException
import net.corda.v5.application.membership.MemberLookup
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.membership.MemberInfo
import java.security.PublicKey

class BaseMemberLookupFactory : MemberLookupFactory {

    override fun createMemberLookup(member: MemberX500Name, memberRegistry: HasMemberInfos): MemberLookup {
        return object : MemberLookup {
            override fun lookup(): List<MemberInfo> = memberRegistry.members.values.toList()

            override fun lookup(key: PublicKey): MemberInfo? = TODO()

            override fun lookup(name: MemberX500Name): MemberInfo = memberRegistry.members[name]
                ?: throw NoSuchMemberException(name)

            override fun myInfo(): MemberInfo {
                return lookup(member)
            }

        }
    }

}
