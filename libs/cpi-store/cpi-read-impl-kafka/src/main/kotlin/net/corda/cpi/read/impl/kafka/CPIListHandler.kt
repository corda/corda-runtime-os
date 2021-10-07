package net.corda.cpi.read.impl.kafka

import com.typesafe.config.Config
import net.corda.cpi.read.CPIListener
import net.corda.cpi.read.impl.kafka.CPIReadImplKafka.Companion.CPILIST_KEYNAME
import net.corda.data.packaging.CPIIdentifier
import net.corda.data.packaging.CPIMetadata
import net.corda.messaging.api.processor.CompactedProcessor
import net.corda.messaging.api.records.Record
import net.corda.messaging.api.subscription.CompactedSubscription
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.messaging.api.subscription.factory.config.SubscriptionConfig
import net.corda.packaging.Cpb
import net.corda.packaging.internal.CpbBuilder
import net.corda.v5.crypto.SecureHash
import java.lang.IllegalStateException
import java.util.*
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class CPIListHandler(private val subscriptionFactory: SubscriptionFactory,
                     private val nodeConfig: Config
) : CompactedProcessor<CPIIdentifier, CPIMetadata> {

    private val cpiIdentities: MutableMap<Cpb.Identifier, Cpb.MetaData> = Collections.synchronizedMap(mutableMapOf())
    private val listeners: MutableList<CPIListener> = Collections.synchronizedList(mutableListOf())
    private var subscription: CompactedSubscription<CPIIdentifier, CPIMetadata>? = null
    @Volatile
    private var snapshotReceived = false
    @Volatile
    private var stopped = true

    private val lock = ReentrantLock()

    override val keyClass: Class<CPIIdentifier>
        get() = CPIIdentifier::class.java
    override val valueClass: Class<CPIMetadata>
        get() = CPIMetadata::class.java

    override fun onSnapshot(currentData: Map<CPIIdentifier, CPIMetadata>) {
        // TODO: Do the correct conversion from CPIIdentifier to CPI.Identifier
        synchronized(listeners) {
            val convertedData = fromAvroTypedMap(currentData)
            listeners.forEach { it.onUpdate(convertedData.keys, convertedData) }
            cpiIdentities.putAll(convertedData)
            snapshotReceived = true
        }
    }

    private fun fromAvroTypedMap(currentData: Map<CPIIdentifier, CPIMetadata>): Map<Cpb.Identifier, Cpb.MetaData> {
        return currentData.map { fromCPIIdentifier(it.key) to fromCPIMetadata(it.value) }.toMap()
    }

    private fun fromCPIIdentifier(cpiIdentifier: CPIIdentifier): Cpb.Identifier {
        return cpiIdentifier.let { Cpb.Identifier(SecureHash.create(cpiIdentifier.signersHash))}
    }

    private fun fromCPIMetadata(cpiMetadata: CPIMetadata): Cpb.MetaData {
        return cpiMetadata.let { Cpb.MetaData(it.metadataMap.toMap()) }
    }

    override fun onNext(
        newRecord: Record<CPIIdentifier, CPIMetadata>,
        oldValue: CPIMetadata?,
        currentData: Map<CPIIdentifier, CPIMetadata>
    ) {
        val convertedData = fromAvroTypedMap(currentData)
        val changedKey = fromCPIIdentifier(newRecord.key)
        val changedValue: Cpb.MetaData? = newRecord.value?.let {fromCPIMetadata(it)}

        synchronized(listeners) {
            listeners.forEach { it.onUpdate(setOf(changedKey), convertedData) }
            changedValue?.let { cpiIdentities.put(changedKey, it) } ?: cpiIdentities.remove(changedKey)
        }
    }

    fun registerCPIListCallback(cpiListener: CPIListener): AutoCloseable {
        listeners.add(cpiListener)
        if (snapshotReceived) {
            cpiListener.onUpdate(cpiIdentities.keys, cpiIdentities)
        }
        return CPIListenerRegistration(this, cpiListener)
    }

    private fun unregisterCPIListCallback(cpiListener: CPIListener) {
        listeners.remove(cpiListener)
    }

    fun start()
    {
        lock.withLock {
            if (subscription != null) {
                subscription =
                    subscriptionFactory.createCompactedSubscription(
                        SubscriptionConfig(
                            CPIReadImplKafka.CPILIST_READ,
                            CPIReadImplKafka.CPILIST_TOPICNAME,
                        ),
                        this,
                        nodeConfig
                    )
                subscription!!.start()
                stopped = false
            }
        }
    }

    fun stop() {
        lock.withLock {
            if (!stopped) {
                subscription?.stop()
                subscription = null
                stopped = true
                snapshotReceived = false
            }
        }
    }

    class CPIListenerRegistration(private val cpiListHandler: CPIListHandler, private val cpiListener: CPIListener): AutoCloseable {
        override fun close() {
            cpiListHandler.unregisterCPIListCallback(cpiListener)
        }
    }
}
