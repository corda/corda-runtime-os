package net.corda.p2p.linkmanager.integration.test.components

import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.reconciliation.VersionedRecord
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.ShortHash
import net.corda.virtualnode.VirtualNodeInfo
import net.corda.virtualnode.read.VirtualNodeInfoListener
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import java.util.stream.Stream

internal class TestVirtualNodeInfoReadService(
    coordinatorFactory: LifecycleCoordinatorFactory,
): VirtualNodeInfoReadService,
    TestLifeCycle(coordinatorFactory,
        VirtualNodeInfoReadService::class,
    ) {
    override fun getAll(): List<VirtualNodeInfo> {
        throw UnsupportedOperationException()
    }

    override fun get(holdingIdentity: HoldingIdentity): VirtualNodeInfo? {
        throw UnsupportedOperationException()
    }

    override fun getByHoldingIdentityShortHash(holdingIdentityShortHash: ShortHash): VirtualNodeInfo? {
        throw UnsupportedOperationException()
    }

    override fun registerCallback(listener: VirtualNodeInfoListener): AutoCloseable {
        throw UnsupportedOperationException()
    }

    override fun getAllVersionedRecords(): Stream<VersionedRecord<HoldingIdentity, VirtualNodeInfo>>? {
        throw UnsupportedOperationException()
    }

    override val lifecycleCoordinatorName = LifecycleCoordinatorName.forComponent<VirtualNodeInfoReadService>()
}