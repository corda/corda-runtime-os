package net.corda.virtualnode.read.fake

import net.corda.lifecycle.impl.LifecycleCoordinatorFactoryImpl
import net.corda.lifecycle.impl.LifecycleCoordinatorSchedulerFactoryImpl
import net.corda.lifecycle.impl.registry.LifecycleRegistryImpl
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.VirtualNodeInfo
import net.corda.virtualnode.read.VirtualNodeInfoListener

internal fun createService(
    vararg virtualNodeInfos: VirtualNodeInfo,
    callbacks: List<VirtualNodeInfoListener> = emptyList(),
): VirtualNodeInfoReadServiceFake {
    val service = VirtualNodeInfoReadServiceFake(
        virtualNodeInfos.associateBy { it.holdingIdentity },
        callbacks,
        LifecycleCoordinatorFactoryImpl(LifecycleRegistryImpl(), LifecycleCoordinatorSchedulerFactoryImpl())
    )
    service.start()
    service.waitUntilRunning()
    return service
}

internal fun snapshot(vararg virtualNodeInfos: VirtualNodeInfo): Map<HoldingIdentity, VirtualNodeInfo> {
    return virtualNodeInfos.associateBy { it.holdingIdentity }
}

internal fun keys(vararg virtualNodeInfos: VirtualNodeInfo): Set<HoldingIdentity> {
    return virtualNodeInfos.map { it.holdingIdentity }.toSet()
}
