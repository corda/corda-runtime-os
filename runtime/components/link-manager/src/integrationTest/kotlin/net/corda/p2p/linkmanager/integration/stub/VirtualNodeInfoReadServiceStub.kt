package net.corda.p2p.linkmanager.integration.stub

import net.corda.crypto.core.ShortHash
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.read.VirtualNodeInfoListener
import net.corda.virtualnode.read.VirtualNodeInfoReadService

class VirtualNodeInfoReadServiceStub : VirtualNodeInfoReadService {
    override fun getAll() = throw UnsupportedOperationException()

    override fun get(holdingIdentity: HoldingIdentity) = throw UnsupportedOperationException()

    override fun getByHoldingIdentityShortHash(
        holdingIdentityShortHash: ShortHash,
    ) = throw UnsupportedOperationException()

    override fun registerCallback(listener: VirtualNodeInfoListener) = throw UnsupportedOperationException()

    override fun getAllVersionedRecords() = throw UnsupportedOperationException()

    override val lifecycleCoordinatorName = LifecycleCoordinatorName.forComponent<VirtualNodeInfoReadService>()

    override val isRunning = true

    override fun start() = Unit

    override fun stop() = Unit
}
