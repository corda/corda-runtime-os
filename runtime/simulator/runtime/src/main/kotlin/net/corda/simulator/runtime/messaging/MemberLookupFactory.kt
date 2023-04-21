package net.corda.simulator.runtime.messaging

import net.corda.v5.application.membership.MemberLookup
import net.corda.v5.base.types.MemberX500Name

/**
 * Creates a [MemberLookup] for the given member.
 */
interface MemberLookupFactory {

    /**
     * @param member The name of the member to create the lookup for.
     * @param memberRegistry The registry of [net.corda.v5.membership.MemberInfo]s to use in the [MemberLookup].
     *
     * @return An implementation of [MemberLookup] for the provided member.
     */
    fun createMemberLookup(member: MemberX500Name, memberRegistry: HasMemberInfos): MemberLookup

}
