package net.corda.testing.driver.sandbox

import net.corda.membership.read.MembershipGroupReaderProvider
import net.corda.v5.base.types.MemberX500Name
import net.corda.virtualnode.HoldingIdentity

interface MembershipGroupControllerProvider : MembershipGroupReaderProvider {
    override fun getGroupReader(holdingIdentity: HoldingIdentity): MembershipGroupController

    fun getMemberNameFor(tenantId: String): MemberX500Name?

    fun register(holdingIdentity: HoldingIdentity)
    fun unregister(holdingIdentity: HoldingIdentity)
}
