package net.corda.cpiinfo.read.impl

import net.corda.cpiinfo.read.CpiInfoListener
import net.corda.libs.packaging.core.CpiIdentifier
import net.corda.libs.packaging.core.CpiMetadata
import net.corda.messaging.api.processor.CompactedProcessor
import net.corda.messaging.api.records.Record
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.trace
import java.util.*
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import net.corda.data.packaging.CpiIdentifier as CpiIdAvro
import net.corda.data.packaging.CpiMetadata as CpiMetadataAvro

/**
 * CPI Info message processor.
 *
 * Maintains a compacted queue of [CPIIdentifier] to [CPIMetadata] Avro objects
 */
class CpiInfoReaderProcessor(private val onStatusUpCallback: () -> Unit, private val onErrorCallback: () -> Unit) :
    AutoCloseable,
    CompactedProcessor<CpiIdAvro, CpiMetadataAvro> {

    companion object {
        private val log = contextLogger()
    }

    /**
     * Holds all the data objects we receive off the wire.
     */
    private val cpiInfoMap = CpiInfoMap()

    @Volatile
    private var snapshotReceived = false

    private val lock = ReentrantLock()

    private val listeners = Collections.synchronizedMap(mutableMapOf<ListenerSubscription, CpiInfoListener>())

    override val keyClass: Class<CpiIdAvro>
        get() = CpiIdAvro::class.java

    override val valueClass: Class<CpiMetadataAvro>
        get() = CpiMetadataAvro::class.java

    fun clear() {
        snapshotReceived = false
        val changeKeys = cpiInfoMap.getAllAsCordaObjects().keys
        cpiInfoMap.clear()
        val newSnapshot = cpiInfoMap.getAllAsCordaObjects() // is now empty
        listeners.forEach { it.value.onUpdate(changeKeys, newSnapshot) }
    }

    override fun close() = listeners.clear()

    override fun onSnapshot(currentData: Map<CpiIdAvro, CpiMetadataAvro>) {
        log.trace { "Cpi Info Processor received snapshot" }

        try {
            cpiInfoMap.putAll(currentData)
        } catch (exception: IllegalArgumentException) {
            // We only expect this code path if someone has posted to Kafka,
            // a CpiMetadata with a different CpiIdentifier to the key.
            log.error(
                "Cpi Info service could not handle onSnapshot, may be corrupt or invalid",
                exception
            )
            onErrorCallback()
            return
        }

        snapshotReceived = true

        val currentSnapshot = cpiInfoMap.getAllAsCordaObjects()
        listeners.forEach { it.value.onUpdate(currentSnapshot.keys, currentSnapshot) }

        onStatusUpCallback()
    }

    override fun onNext(
        newRecord: Record<CpiIdAvro, CpiMetadataAvro>,
        oldValue: CpiMetadataAvro?,
        currentData: Map<CpiIdAvro, CpiMetadataAvro>
    ) {
        if (newRecord.value != null) {
            try {
                cpiInfoMap.put(newRecord.key, newRecord.value!!)
            } catch (exception: IllegalArgumentException) {
                // We only expect this code path if someone has posted to Kafka,
                // a CpiMetadata with a different CpiIdentifier to the key.
                log.error(
                    "Cpi Info service could not handle onNext, key/value is invalid for $newRecord",
                    exception
                )
                onErrorCallback()
                return
            }
        } else {
            cpiInfoMap.remove(newRecord.key)
        }

        val currentSnapshot = cpiInfoMap.getAllAsCordaObjects()
        listeners.forEach { it.value.onUpdate(setOf(CpiIdentifier.fromAvro(newRecord.key)), currentSnapshot) }
    }

    fun getAll(): List<CpiMetadata> {
        return cpiInfoMap.getAll().map(CpiMetadata::fromAvro)
    }

    fun get(identifier: CpiIdentifier): CpiMetadata? {
        val avroMsg = cpiInfoMap.get(identifier.toAvro()) ?: return null
        return CpiMetadata.fromAvro(avroMsg)
    }

    fun registerCallback(listener: CpiInfoListener): AutoCloseable {
        lock.withLock {
            val subscription = ListenerSubscription(this)
            listeners[subscription] = listener
            if (snapshotReceived) {
                val currentSnapshot = cpiInfoMap.getAllAsCordaObjects()
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
    private class ListenerSubscription(private val service: CpiInfoReaderProcessor) : AutoCloseable {
        override fun close() {
            service.unregisterCallback(this)
        }
    }
}
