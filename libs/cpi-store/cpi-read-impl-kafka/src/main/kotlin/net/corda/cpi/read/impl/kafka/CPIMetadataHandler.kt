package net.corda.cpi.read.impl.kafka

import com.typesafe.config.Config
import net.corda.cpi.read.CPIMetadataListener
import net.corda.data.packaging.CPIMetadata
import net.corda.messaging.api.processor.CompactedProcessor
import net.corda.messaging.api.records.Record
import net.corda.messaging.api.subscription.CompactedSubscription
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.messaging.api.subscription.factory.config.SubscriptionConfig
import net.corda.packaging.Cpb
import java.util.*
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class CPIMetadataHandler(private val subscriptionFactory: SubscriptionFactory,
                         private val nodeConfig: Config
) : CompactedProcessor<String, CPIMetadata> {


    private val cpiMetadata: MutableMap<String, Cpb.MetaData> = Collections.synchronizedMap(mutableMapOf())
    private val listeners: MutableList<CPIMetadataListener> = Collections.synchronizedList(mutableListOf())
    private val lock = ReentrantLock()
    private var subscription: CompactedSubscription<String, CPIMetadata>? = null
    @Volatile
    private var stopped = true
    @Volatile
    private var snapshotReceived = false

    override val keyClass: Class<String>
        get() = String::class.java
    override val valueClass: Class<CPIMetadata>
        get() = CPIMetadata::class.java

    override fun onSnapshot(currentData: Map<String, CPIMetadata>) {
        synchronized(listeners) {
            val convertedData = convertToPackagingType(currentData)
            listeners.forEach { it.onUpdate(currentData.keys, convertedData) }
            cpiMetadata.putAll(convertedData)
            snapshotReceived = true
        }
    }

    private fun convertToPackagingType(currentData: Map<String, CPIMetadata>): Map<String, Cpb.MetaData> {
        return currentData.mapValues { fromCPIMetadata(it.value)}
    }

    private fun fromCPIMetadata(cpiMetadata: CPIMetadata): Cpb.MetaData {
        return Cpb.MetaData(cpiMetadata.metadataMap.toMap())
    }

    override fun onNext(
        newRecord: Record<String, CPIMetadata>,
        oldValue: CPIMetadata?,
        currentData: Map<String, CPIMetadata>
    ) {
        val convertedData = convertToPackagingType(currentData)
        val newCPIMetadata = newRecord.value?.let {fromCPIMetadata(newRecord.value!!)}
        synchronized(listeners) {
            listeners.forEach { it.onUpdate(setOf(newRecord.key), convertedData) }
            newCPIMetadata?.let { cpiMetadata.put(newRecord.key, it) } ?: cpiMetadata.remove(newRecord.key)
        }
    }

    fun registerCPIMetadataCallback(cpiMetadataListener: CPIMetadataListener): AutoCloseable {
            listeners.add(cpiMetadataListener)
            if (snapshotReceived) {
                cpiMetadataListener.onUpdate(cpiMetadata.keys, cpiMetadata)
            }
            return CPIMetadataRegistration(this, cpiMetadataListener)
    }

    private fun unregisterCPIMetadataCallback(cpiMetadataListener: CPIMetadataListener) {
        listeners.remove(cpiMetadataListener)
    }

    fun start() {
        lock.withLock {
            cpiMetadata.clear()
            subscription =
                subscriptionFactory.createCompactedSubscription(
                    SubscriptionConfig(
                        CPIReadImplKafka.CPIMETADATA_READ,
                        CPIReadImplKafka.CPIMETADATA_TOPICNAME
                    ),
                    this,
                    nodeConfig
                )
            subscription!!.start()
            stopped = false
        }
    }

    fun stop() {
        lock.withLock {
            if (!stopped) {
                subscription!!.stop()
                stopped = true
            }
        }
    }

    class CPIMetadataRegistration(private val cpiMetadataHandler: CPIMetadataHandler, private val cpiMetadataListener: CPIMetadataListener): AutoCloseable {
        override fun close() {
            cpiMetadataHandler.unregisterCPIMetadataCallback(cpiMetadataListener)
        }
    }
}
