package net.corda.virtualnode.async.operation.impl

import net.corda.data.virtualnode.VirtualNodeOperationStatus
import net.corda.messaging.api.records.Record
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.trace
import net.corda.virtualnode.async.operation.VirtualNodeOperationStatusMap
import net.corda.virtualnode.async.operation.VirtualNodeOperationStatusProcessor

class VirtualNodeOperationStatusProcessorImpl(
    private val cache: VirtualNodeOperationStatusMap,
    private val onSnapshotCallback: (() -> Unit)?,
    private val onNextCallback: (() -> Unit)?,
    private val onErrorCallback: (() -> Unit)?,
) : VirtualNodeOperationStatusProcessor {

    private companion object {
        val log = contextLogger()
    }

    override fun onSnapshot(currentData: Map<String, VirtualNodeOperationStatus>) {
        cache.putAll(currentData)
        onSnapshotCallback?.invoke()
    }

    override fun onNext(
        newRecord: Record<String, VirtualNodeOperationStatus>,
        oldValue: VirtualNodeOperationStatus?,
        currentData: Map<String, VirtualNodeOperationStatus>
    ) {
        log.trace { "Virtual Node Operation Status Processor received onNext" }
        val removal = newRecord.value == null
        if (removal) {
            cache.remove(newRecord.key)
            return
        }
        val status = newRecord.value!!
        val key = VirtualNodeOperationStatusMap.Key(status.virtualNodeShortHash, newRecord.key)
        try {
            cache.put(key, status)
        } catch (exception: IllegalArgumentException) {
            log.error(
                "VirtualNodeOperationStatusReadService could not handle onNext, key/value is invalid for $newRecord",
                exception
            )
            onErrorCallback?.invoke()
            return
        }
        onNextCallback?.invoke()
    }

    override val keyClass: Class<String>
        get() = String::class.java
    override val valueClass: Class<VirtualNodeOperationStatus>
        get() = VirtualNodeOperationStatus::class.java
}