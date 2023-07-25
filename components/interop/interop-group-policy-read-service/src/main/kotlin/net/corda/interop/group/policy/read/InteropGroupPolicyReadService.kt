package net.corda.interop.group.policy.read

import net.corda.lifecycle.Lifecycle


interface InteropGroupPolicyReadService : Lifecycle {
    fun getGroupPolicy(groupId: String) : String?
}
