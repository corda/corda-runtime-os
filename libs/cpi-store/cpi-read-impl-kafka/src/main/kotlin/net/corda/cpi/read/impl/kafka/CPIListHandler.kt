package net.corda.cpi.read.impl.kafka

import net.corda.cpi.read.CPIListener
import net.corda.cpi.utils.CPI_LIST_TOPIC_NAME
import net.corda.cpi.utils.CPI_SUBSCRIPTION_GROUP_NAME
import net.corda.cpi.utils.newInstance
import net.corda.data.packaging.CPIMetadata
import net.corda.libs.configuration.SmartConfig
import net.corda.messaging.api.processor.CompactedProcessor
import net.corda.messaging.api.records.Record
import net.corda.messaging.api.subscription.CompactedSubscription
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.messaging.api.subscription.factory.config.SubscriptionConfig
import net.corda.packaging.CPI
import net.corda.packaging.converters.toCorda
import java.util.*

class CPIListHandler(private val subscriptionFactory: SubscriptionFactory,
                     private val nodeConfig: SmartConfig
) : CompactedProcessor<String, CPIMetadata> {

    val cpiMetadata: MutableMap<CPI.Identifier, CPI.Metadata> = Collections.synchronizedMap(mutableMapOf())
    private val listeners: MutableList<CPIListener> = Collections.synchronizedList(mutableListOf())
    private var subscription: CompactedSubscription<String, CPIMetadata>? = null
    @Volatile
    private var snapshotReceived = false
    @Volatile
    private var stopped = true

    override val keyClass: Class<String>
        get() = String::class.java
    override val valueClass: Class<CPIMetadata>
        get() = CPIMetadata::class.java

    override fun onSnapshot(currentData: Map<String, CPIMetadata>) {
        synchronized(listeners) {
            val convertedData = fromAvroTypedMap(currentData)
            listeners.forEach { it.onUpdate(convertedData.keys, convertedData) }
            cpiMetadata.putAll(convertedData)
            snapshotReceived = true
        }
    }

    private fun fromAvroTypedMap(currentData: Map<String, CPIMetadata>): Map<CPI.Identifier, CPI.Metadata> {
        return currentData.map { CPI.Identifier.newInstance(it.key) to it.value.toCorda() }.toMap()
    }

    private fun fromCPIIdentifier(cpiIdentifier: String): CPI.Identifier {
        return cpiIdentifier.let { CPI.Identifier.newInstance(it) }
    }

    override fun onNext(
        newRecord: Record<String, CPIMetadata>,
        oldValue: CPIMetadata?,
        currentData: Map<String, CPIMetadata>
    ) {
        val convertedData = fromAvroTypedMap(currentData)
        val changedKey = fromCPIIdentifier(newRecord.key)
        val changedValue: CPI.Metadata? = newRecord.value?.let {it.toCorda()}

        synchronized(listeners) {
            listeners.forEach { it.onUpdate(setOf(changedKey), convertedData) }
            changedValue?.let { cpiMetadata.put(changedKey, it) } ?: cpiMetadata.remove(changedKey)
        }
    }

    fun registerCPIListCallback(cpiListener: CPIListener): AutoCloseable {
        listeners.add(cpiListener)
        if (snapshotReceived) {
            cpiListener.onUpdate(cpiMetadata.keys, cpiMetadata)
        }
        return CPIListenerRegistration(this, cpiListener)
    }

    private fun unregisterCPIListCallback(cpiListener: CPIListener) {
        listeners.remove(cpiListener)
    }

    fun start()
    {
        if (subscription == null) {
            subscription =
                subscriptionFactory.createCompactedSubscription(
                    SubscriptionConfig(
                        CPI_SUBSCRIPTION_GROUP_NAME,
                        CPI_LIST_TOPIC_NAME,
                    ),
                   this,
                    nodeConfig
                )
            subscription!!.start()
            stopped = false
        }
    }

    fun stop() {
        if (!stopped) {
            subscription?.stop()
            subscription = null
            stopped = true
            snapshotReceived = false
        }
    }

    class CPIListenerRegistration(private val cpiListHandler: CPIListHandler, private val cpiListener: CPIListener): AutoCloseable {
        override fun close() {
            cpiListHandler.unregisterCPIListCallback(cpiListener)
        }
    }
}
