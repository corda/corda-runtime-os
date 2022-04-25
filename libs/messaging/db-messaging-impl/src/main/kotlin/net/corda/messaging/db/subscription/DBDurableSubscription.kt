package net.corda.messaging.db.subscription

import net.corda.lifecycle.Lifecycle
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.messaging.api.processor.DurableProcessor
import net.corda.messaging.api.processor.EventLogProcessor
import net.corda.messaging.api.records.EventLogRecord
import net.corda.messaging.api.records.Record
import net.corda.messaging.api.subscription.Subscription
import net.corda.messaging.api.subscription.config.SubscriptionConfig
import net.corda.messaging.api.subscription.listener.PartitionAssignmentListener
import net.corda.messaging.db.partition.PartitionAllocator
import net.corda.messaging.db.partition.PartitionAssignor
import net.corda.messaging.db.persistence.DBAccessProvider
import net.corda.messaging.db.sync.OffsetTrackersManager
import net.corda.schema.registry.AvroSchemaRegistry
import net.corda.v5.base.util.seconds
import java.time.Duration

@Suppress("LongParameterList")
class DBDurableSubscription<K : Any, V : Any>(
    subscriptionConfig: SubscriptionConfig,
    instanceId: Int,
    durableProcessor: DurableProcessor<K, V>,
    partitionAssignmentListener: PartitionAssignmentListener?,
    avroSchemaRegistry: AvroSchemaRegistry,
    offsetTrackersManager: OffsetTrackersManager,
    partitionAllocator: PartitionAllocator,
    partitionAssignor: PartitionAssignor,
    dbAccessProvider: DBAccessProvider,
    lifecycleCoordinatorFactory: LifecycleCoordinatorFactory,
    pollingTimeout: Duration = 1.seconds,
    batchSize: Int = 100
) : Subscription<K, V>, Lifecycle {

    private val eventLogSubscription = DBEventLogSubscription(
        subscriptionConfig,
        instanceId,
        ForwardingEventLogProcessor(durableProcessor),
        partitionAssignmentListener,
        avroSchemaRegistry,
        offsetTrackersManager,
        partitionAllocator,
        partitionAssignor,
        dbAccessProvider,
        lifecycleCoordinatorFactory,
        pollingTimeout,
        batchSize
    )

    override fun start() {
        eventLogSubscription.start()
    }

    override fun stop() {
        eventLogSubscription.stop()
    }

    override val isRunning: Boolean
        get() = eventLogSubscription.isRunning

    /**
     * A simple processor that forwards events to the underlying durable processor.
     */
    class ForwardingEventLogProcessor<K : Any, V : Any>(private val durableProcessor: DurableProcessor<K, V>) :
        EventLogProcessor<K, V> {
        override fun onNext(events: List<EventLogRecord<K, V>>): List<Record<*, *>> {
            val records = events.map { Record(it.topic, it.key, it.value) }
            return durableProcessor.onNext(records)
        }

        override val keyClass: Class<K>
            get() = durableProcessor.keyClass
        override val valueClass: Class<V>
            get() = durableProcessor.valueClass

    }

    override val subscriptionName: LifecycleCoordinatorName
        get() = eventLogSubscription.subscriptionName
}
