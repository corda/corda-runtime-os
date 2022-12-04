package net.corda.virtualnode.async.operation

import net.corda.data.virtualnode.VirtualNodeOperationStatus
import net.corda.messaging.api.processor.CompactedProcessor

interface VirtualNodeOperationStatusMap {
    data class Key(val virtualNodeShortHash: String, val requestId: String)

    fun getByVirtualNodeShortHash(virtualNodeShortHash: String): List<VirtualNodeOperationStatus>
    fun get(requestId: String): VirtualNodeOperationStatus?
    fun putAll(incoming: Map<String, VirtualNodeOperationStatus>)
    fun put(key: Key, value: VirtualNodeOperationStatus?)
    fun remove(requestId: String)
}