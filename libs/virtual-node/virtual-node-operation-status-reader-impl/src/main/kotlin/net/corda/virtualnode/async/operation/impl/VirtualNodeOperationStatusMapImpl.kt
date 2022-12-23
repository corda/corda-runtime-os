package net.corda.virtualnode.async.operation.impl

import com.google.common.collect.Multimap
import com.google.common.collect.Multimaps
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReadWriteLock
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.withLock
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.debug
import net.corda.data.virtualnode.VirtualNodeOperationStatus as AvroVirtualNodeOperationStatus
import net.corda.virtualnode.async.operation.VirtualNodeOperationStatusMap

class VirtualNodeOperationStatusMapImpl : VirtualNodeOperationStatusMap {
    private companion object {
        val logger = contextLogger()
    }

    private val statusByRequestId: MutableMap<String, AvroVirtualNodeOperationStatus> = Collections.synchronizedMap(mutableMapOf())
    private val statusesByVirtualNodeShortHash: Multimap<String, AvroVirtualNodeOperationStatus> =
        Multimaps.newSetMultimap(ConcurrentHashMap()) { ConcurrentHashMap.newKeySet() }

    private val lock: ReadWriteLock = ReentrantReadWriteLock()

    /*fun clear() {
        statusByRequestId.clear()
        lock.writeLock().withLock {
            statusesByVirtualNodeShortHash.clear()
        }
    }*/

    override fun getByVirtualNodeShortHash(virtualNodeShortHash: String): List<AvroVirtualNodeOperationStatus> {
        return lock.readLock().withLock {
            ArrayList(statusesByVirtualNodeShortHash.get(virtualNodeShortHash).toList())
        }
    }

    override fun get(requestId: String): AvroVirtualNodeOperationStatus? {
        return statusByRequestId[requestId]
    }

    override fun putAll(incoming: Map<String, AvroVirtualNodeOperationStatus>) {
        incoming.forEach { put(VirtualNodeOperationStatusMap.Key(it.value.virtualNodeShortHash, it.key), it.value) }
    }

    private fun putValue(key: VirtualNodeOperationStatusMap.Key, value: AvroVirtualNodeOperationStatus) {
        statusByRequestId[key.requestId] = value
        lock.writeLock().withLock {
            statusesByVirtualNodeShortHash.put(key.virtualNodeShortHash, value)
        }
    }

    override fun put(key: VirtualNodeOperationStatusMap.Key, value: AvroVirtualNodeOperationStatus?) {
        if (value == null) {
            remove(key)
            return
        }

        putValue(key, value)
    }

    override fun remove(requestId: String) {
        val removeByVnodeShortHash = statusByRequestId[requestId]

        if (removeByVnodeShortHash == null) {
            logger.debug { "Attempted to remove virtual node operation status with requestId $requestId but it was not found." }
            return
        }

        remove(VirtualNodeOperationStatusMap.Key(removeByVnodeShortHash.virtualNodeShortHash, requestId))
    }

    private fun remove(key: VirtualNodeOperationStatusMap.Key) {
        statusByRequestId.remove(key.requestId)
        lock.writeLock().withLock {
            statusesByVirtualNodeShortHash.removeAll(key.virtualNodeShortHash)
        }
    }
}