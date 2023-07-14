package net.corda.testing.driver.impl

import java.time.Duration
import net.corda.testing.driver.MembershipGroupDSL
import net.corda.testing.driver.function.ThrowingConsumer
import net.corda.testing.driver.node.MembershipGroup
import net.corda.testing.driver.node.Member
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.base.types.MemberX500Name
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.toAvro

internal class MembershipGroupManager(private val dsl: DriverInternalDSL) {
    private fun doMembershipGroupService(timeout: Duration, operation: ThrowingConsumer<MembershipGroup>) {
        try {
            dsl.framework.getService(MembershipGroup::class.java, null, timeout).andAlso(operation)
        } catch (e: RuntimeException) {
            throw e
        } catch (e: Exception) {
            throw CordaRuntimeException(e::class.java.name, e.message, e)
        }
    }

    fun forVirtualNode(holdingIdentity: HoldingIdentity, timeout: Duration, action: ThrowingConsumer<Member>) {
        doMembershipGroupService(timeout) { group ->
            group.virtualNode(holdingIdentity.toAvro(), action)
        }
    }

    fun forMembershipGroup(holdingIdentity: HoldingIdentity, timeout: Duration, action: ThrowingConsumer<MembershipGroupDSL>) {
        doMembershipGroupService(timeout) { group ->
            action.acceptThrowing(MembershipGroupDSLImpl(group, holdingIdentity))
        }
    }

    private class MembershipGroupDSLImpl(private val group: MembershipGroup, private val id: HoldingIdentity) :
        MembershipGroupDSL {
        private fun getHoldingIdentity(name: MemberX500Name) = HoldingIdentity(name, id.groupId).toAvro()

        override fun members(): Set<MemberX500Name> {
            return group.getMembers(id.toAvro())
        }

        override fun member(name: MemberX500Name, action: ThrowingConsumer<Member>) {
            group.virtualNode(getHoldingIdentity(name), action)
        }
    }
}
