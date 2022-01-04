package net.corda.cpiinfo.read.impl

import net.corda.cpiinfo.read.CpiInfoListener
import net.corda.cpiinfo.read.CpiInfoReader
import net.corda.data.packaging.CPIIdentifier
import net.corda.data.packaging.CPIMetadata
import net.corda.messaging.api.processor.CompactedProcessor
import net.corda.messaging.api.records.Record
import net.corda.packaging.CPI
import net.corda.packaging.converters.toAvro
import net.corda.packaging.converters.toCorda
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.trace
import org.slf4j.Logger
import java.util.Collections
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * CPI Info message processor.
 *
 * Maintains a compacted queue of [CPIIdentifier] to [CPIMetadata] Avro objects
 */
class CpiInfoReaderProcessor(private val onStatusUpCallback: () -> Unit, private val onErrorCallback: () -> Unit) :
    CpiInfoReader,
    AutoCloseable,
    CompactedProcessor<CPIIdentifier, CPIMetadata> {

    companion object {
        val log: Logger = contextLogger()
    }

    /**
     * Holds all the data objects we receive off the wire.
     */
    private val cpiInfoMap = CpiInfoMap()

    @Volatile
    private var snapshotReceived = false

    private val lock = ReentrantLock()

    private val listeners = Collections.synchronizedMap(mutableMapOf<ListenerSubscription, CpiInfoListener>())

    override val keyClass: Class<CPIIdentifier>
        get() = CPIIdentifier::class.java

    override val valueClass: Class<CPIMetadata>
        get() = CPIMetadata::class.java

    fun clear() {
        snapshotReceived = false
        val changeKeys = cpiInfoMap.getAllAsCordaObjects().keys
        cpiInfoMap.clear()
        val newSnapshot = cpiInfoMap.getAllAsCordaObjects() // is now empty
        listeners.forEach { it.value.onUpdate(changeKeys, newSnapshot) }
    }

    override fun close() = listeners.clear()

    override fun onSnapshot(currentData: Map<CPIIdentifier, CPIMetadata>) {
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
        newRecord: Record<CPIIdentifier, CPIMetadata>,
        oldValue: CPIMetadata?,
        currentData: Map<CPIIdentifier, CPIMetadata>
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
        listeners.forEach { it.value.onUpdate(setOf(newRecord.key.toCorda()), currentSnapshot) }
    }

    override fun get(identifier: CPI.Identifier): CPI.Metadata? {
        return cpiInfoMap.get(identifier.toAvro())?.toCorda()
    }

    override fun registerCallback(listener: CpiInfoListener): AutoCloseable {
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
