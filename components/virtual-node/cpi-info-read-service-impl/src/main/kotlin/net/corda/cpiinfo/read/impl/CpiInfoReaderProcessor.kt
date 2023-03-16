package net.corda.cpiinfo.read.impl

import net.corda.libs.packaging.core.CpiIdentifier
import net.corda.libs.packaging.core.CpiMetadata
import net.corda.messaging.api.processor.CompactedProcessor
import net.corda.messaging.api.records.Record
import net.corda.utilities.trace
import org.slf4j.LoggerFactory
import net.corda.data.packaging.CpiIdentifier as CpiIdAvro
import net.corda.data.packaging.CpiMetadata as CpiMetadataAvro

/**
 * CPI Info message processor.
 *
 * Maintains a compacted queue of [CPIIdentifier] to [CPIMetadata] Avro objects
 */
class CpiInfoReaderProcessor(private val onStatusUpCallback: () -> Unit, private val onErrorCallback: () -> Unit) :
    CompactedProcessor<CpiIdAvro, CpiMetadataAvro> {

    companion object {
        private val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    /**
     * Holds all the data objects we receive off the wire.
     */
    private val cpiInfoMap = CpiInfoMap()

    @Volatile
    private var snapshotReceived = false

    override val keyClass: Class<CpiIdAvro>
        get() = CpiIdAvro::class.java

    override val valueClass: Class<CpiMetadataAvro>
        get() = CpiMetadataAvro::class.java

    fun clear() {
        snapshotReceived = false
        cpiInfoMap.clear()
    }

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
    }

    fun getAll(): Collection<CpiMetadata> = cpiInfoMap.getAll().values

    fun get(identifier: CpiIdentifier): CpiMetadata? = cpiInfoMap.get(identifier)
}
