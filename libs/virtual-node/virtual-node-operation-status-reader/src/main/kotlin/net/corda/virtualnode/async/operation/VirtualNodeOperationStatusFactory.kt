package net.corda.virtualnode.async.operation

interface VirtualNodeOperationStatusFactory {
    fun createStatusMap(): VirtualNodeOperationStatusMap
    fun createStatusProcessor(
        cache: VirtualNodeOperationStatusMap,
        onSnapshotCallback: (() -> Unit)?,
        onNextCallback: (() -> Unit)?,
        onErrorCallback: (() -> Unit)?
    ): VirtualNodeOperationStatusProcessor
}