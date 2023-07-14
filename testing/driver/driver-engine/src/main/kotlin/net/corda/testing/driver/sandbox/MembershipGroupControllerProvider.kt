package net.corda.testing.driver.sandbox

import net.corda.membership.read.MembershipGroupReaderProvider
import net.corda.virtualnode.HoldingIdentity

interface MembershipGroupControllerProvider : MembershipGroupReaderProvider {
    override fun getGroupReader(holdingIdentity: HoldingIdentity): MembershipGroupController
}
