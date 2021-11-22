package net.corda.virtualnode.impl

import net.corda.messaging.api.processor.CompactedProcessor
import net.corda.messaging.api.records.Record
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.trace
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.VirtualNodeInfo
import net.corda.virtualnode.VirtualNodeInfoListener
import net.corda.virtualnode.VirtualNodeInfoService
import net.corda.virtualnode.toAvro
import net.corda.virtualnode.toCorda
import org.slf4j.Logger
import java.util.Collections
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Virtual node info processor.
 *
 * This class listens and maintains a compacted queue of [HoldingIdentity] to [VirtualNodeInfo]
 *
 * It implements the [VirtualNodeInfoService] interface that calls back with updates as well as allows
 * the caller to directly query for [VirtualNodeInfo] by [HoldingIdentity] or 'short hash' of the [HoldingIdentity]
 * e.g. `123456ABCDEF`
 *
 * We use callback to indicate that we've received a snapshot and are 'ready'.
 */
class VirtualNodeInfoProcessor(private val onSnapshotCallback: () -> Unit) : VirtualNodeInfoService, AutoCloseable,
    CompactedProcessor<net.corda.data.identity.HoldingIdentity, net.corda.data.virtualnode.VirtualNodeInfo> {

    companion object {
        val log: Logger = contextLogger()
    }

    /** Holds all the virtual node info we receive off the wire as Avro objects */
    private val virtualNodeInfoMap = VirtualNodeInfoMap()

    @Volatile
    private var snapshotReceived = false

    private val lock = ReentrantLock()

    /** Collection of callbacks to listeners of active changes to the [VirtualNodeInfo] collection. */
    private val listeners = Collections.synchronizedMap(mutableMapOf<ListenerSubscription, VirtualNodeInfoListener>())

    /** Clear out all [VirtualNodeInfo] objects that we hold, and notify any listeners */
    fun clear() {
        snapshotReceived = false
        val changeKeys = virtualNodeInfoMap.getAllAsCordaObjects().keys
        virtualNodeInfoMap.clear()
        val newSnapshot = virtualNodeInfoMap.getAllAsCordaObjects() // is now empty
        listeners.forEach { it.value.onUpdate(changeKeys, newSnapshot) }
    }

    override fun close() = listeners.clear()

    override val keyClass: Class<net.corda.data.identity.HoldingIdentity>
        get() = net.corda.data.identity.HoldingIdentity::class.java

    override val valueClass: Class<net.corda.data.virtualnode.VirtualNodeInfo>
        get() = net.corda.data.virtualnode.VirtualNodeInfo::class.java

    override fun onSnapshot(currentData: Map<net.corda.data.identity.HoldingIdentity, net.corda.data.virtualnode.VirtualNodeInfo>) {
        log.trace { "Virtual Node Info Processor received snapshot" }
        virtualNodeInfoMap.putAll(
            currentData.mapKeys { VirtualNodeInfoMap.Key(it.key, it.key.toCorda().id) }
        )

        snapshotReceived = true

        val currentSnapshot = virtualNodeInfoMap.getAllAsCordaObjects()
        listeners.forEach { it.value.onUpdate(currentSnapshot.keys, currentSnapshot) }

        // Callback to whoever called us, that we're 'up'
        onSnapshotCallback()
    }

    override fun onNext(
        newRecord: Record<net.corda.data.identity.HoldingIdentity, net.corda.data.virtualnode.VirtualNodeInfo>,
        oldValue: net.corda.data.virtualnode.VirtualNodeInfo?,
        currentData: Map<net.corda.data.identity.HoldingIdentity, net.corda.data.virtualnode.VirtualNodeInfo>
    ) {
        log.trace { "Virtual Node Info Processor received onNext" }
        val key = VirtualNodeInfoMap.Key(newRecord.key, newRecord.key.toCorda().id)
        if (newRecord.value != null) {
            virtualNodeInfoMap.put(key, newRecord.value!!)
        } else {
            virtualNodeInfoMap.remove(key)
        }

        val currentSnapshot = virtualNodeInfoMap.getAllAsCordaObjects()
        listeners.forEach { it.value.onUpdate(setOf(newRecord.key.toCorda()), currentSnapshot) }
    }

    override fun get(holdingIdentity: HoldingIdentity): VirtualNodeInfo? =
        virtualNodeInfoMap.get(holdingIdentity.toAvro())?.toCorda()

    override fun getById(id: String): VirtualNodeInfo? = virtualNodeInfoMap.getById(id)?.toCorda()

    override fun registerCallback(listener: VirtualNodeInfoListener): AutoCloseable {
        lock.withLock {
            val subscription = ListenerSubscription(this)
            listeners[subscription] = listener
            if (snapshotReceived) {
                val currentSnapshot = virtualNodeInfoMap.getAllAsCordaObjects()
                listener.onUpdate(currentSnapshot.keys, currentSnapshot)
            }
            return subscription
        }
    }

    /** Unregister a caller's subscription when they close it. */
    private fun unregisterCallback(subscription: ListenerSubscription) {
        listeners.remove(subscription)
    }

    /**
     * We return this handle to the subscription callback to the caller so that the can [close()]
     * it and unregister if they wish.
     */
    private class ListenerSubscription(private val service: VirtualNodeInfoProcessor) : AutoCloseable {
        override fun close() {
            service.unregisterCallback(this)
        }
    }
}
