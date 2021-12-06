package net.corda.virtualnode.impl

import net.corda.data.identity.HoldingIdentity
import net.corda.data.virtualnode.VirtualNodeInfo
import net.corda.virtualnode.VirtualNodeInfoWriterService

class VirtualNodeInfoWriterServiceImpl /*(subscriptionFactory: SubscriptionFactory, config: SmartConfig)*/ :
    VirtualNodeInfoWriterService {
    override fun put(holdingIdentity: HoldingIdentity, virtualNodeInfo: VirtualNodeInfo) {
        TODO("Not yet implemented")
    }

    override fun remove(holdingIdentity: HoldingIdentity) {
        TODO("Not yet implemented")
    }
}
