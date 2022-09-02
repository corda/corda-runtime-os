package net.corda.cordapptestutils.internal.messaging

import net.corda.v5.application.membership.MemberLookup
import net.corda.v5.base.types.MemberX500Name

interface MemberLookupFactory {
    fun createMemberLookup(member: MemberX500Name, memberRegistry: HasMemberInfos): MemberLookup

}
