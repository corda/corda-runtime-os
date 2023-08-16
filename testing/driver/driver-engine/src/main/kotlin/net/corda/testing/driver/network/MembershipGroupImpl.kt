package net.corda.testing.driver.network

import java.util.Collections.unmodifiableSet
import net.corda.data.identity.HoldingIdentity as AvroHoldingIdentity
import net.corda.data.p2p.app.MembershipStatusFilter.ACTIVE_OR_SUSPENDED_IF_PRESENT_OR_PENDING
import net.corda.membership.lib.MemberInfoExtension.Companion.STATUS
import net.corda.membership.lib.MemberInfoExtension.Companion.status
import net.corda.membership.lib.toSortedMap
import net.corda.membership.lib.toWire
import net.corda.testing.driver.node.Member
import net.corda.testing.driver.function.ThrowingConsumer
import net.corda.testing.driver.node.MembershipGroup
import net.corda.testing.driver.node.MemberStatus
import net.corda.testing.driver.sandbox.DRIVER_SERVICE_FILTER
import net.corda.testing.driver.sandbox.MembershipGroupController
import net.corda.testing.driver.sandbox.MembershipGroupControllerProvider
import net.corda.v5.membership.MemberInfo
import net.corda.v5.base.types.MemberX500Name
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.VirtualNodeInfo
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import net.corda.virtualnode.toCorda
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Suppress("unused")
@Component
class MembershipGroupImpl @Activate constructor(
    @Reference
    private val membershipGroupControllerProvider: MembershipGroupControllerProvider,
    @Reference(target = DRIVER_SERVICE_FILTER)
    private val virtualNodeInfoReadService: VirtualNodeInfoReadService
): MembershipGroup {
    private fun getVirtualNodeInfo(holdingIdentity: HoldingIdentity): VirtualNodeInfo {
        return virtualNodeInfoReadService.get(holdingIdentity)
            ?: throw AssertionError("Missing VirtualNodeInfo for $holdingIdentity")
    }

    override fun getName(holdingIdentity: AvroHoldingIdentity): String {
        return getVirtualNodeInfo(holdingIdentity.toCorda()).cpiIdentifier.name
    }

    override fun getMembers(holdingIdentity: AvroHoldingIdentity): Set<MemberX500Name> {
        return unmodifiableSet(membershipGroupControllerProvider.getGroupReader(holdingIdentity.toCorda()).membership
            .mapTo(linkedSetOf(), MemberInfo::getName))
    }

    override fun virtualNode(holdingIdentity: AvroHoldingIdentity, action: ThrowingConsumer<Member>) {
        val id = holdingIdentity.toCorda()
        action.acceptThrowing(MemberImpl(id, membershipGroupControllerProvider.getGroupReader(id)))
    }

    private inner class MemberImpl(
        private val id: HoldingIdentity,
        private val groupController: MembershipGroupController
    ) : Member {
        private val memberInfo: MemberInfo
            get() = groupController.lookup(id.x500Name, ACTIVE_OR_SUSPENDED_IF_PRESENT_OR_PENDING)
                ?: throw AssertionError("Member $id not found")

        override fun getName(): MemberX500Name {
            return id.x500Name
        }

        override fun getStatus(): MemberStatus {
            return MemberStatus.fromString(memberInfo.status)
        }

        override fun setStatus(newStatus: MemberStatus) {
            memberInfo.also {
                if (it.status != newStatus.toString()) {
                    val mgmContext = it.mgmProvidedContext.toWire().toSortedMap().also { map ->
                        map.replace(STATUS, newStatus.toString())
                    }
                    groupController.updateMembership(it, mgmContext)
                }
            }
        }
    }
}
