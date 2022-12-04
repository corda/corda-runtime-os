package net.corda.virtualnode.async.operation.impl

import net.corda.virtualnode.async.operation.VirtualNodeOperationStatusMap
import net.corda.virtualnode.async.operation.VirtualNodeOperationStatusProcessor
import net.corda.virtualnode.async.operation.VirtualNodeOperationStatusFactory
import org.osgi.service.component.annotations.Component

@Component(service = [VirtualNodeOperationStatusFactory::class])
class VirtualNodeOperationStatusFactoryImpl: VirtualNodeOperationStatusFactory {
    override fun createStatusMap(): VirtualNodeOperationStatusMap {
        return VirtualNodeOperationStatusMapImpl()
    }
    override fun createStatusProcessor(
        cache: VirtualNodeOperationStatusMap,
        onSnapshotCallback: (() -> Unit)?,
        onNextCallback: (() -> Unit)?,
        onErrorCallback: (() -> Unit)?
    ): VirtualNodeOperationStatusProcessor {
        return VirtualNodeOperationStatusProcessorImpl(cache, onSnapshotCallback, onNextCallback, onErrorCallback)
    }
}