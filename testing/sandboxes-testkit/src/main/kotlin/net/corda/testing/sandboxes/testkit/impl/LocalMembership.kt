package net.corda.testing.sandboxes.testkit.impl

import net.corda.v5.base.types.MemberX500Name

interface LocalMembership {
    fun getLocalMembers(): Set<MemberX500Name>
    fun setLocalMembers(localMembers: Set<MemberX500Name>)
}
