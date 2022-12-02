package net.corda.messaging.subscription

import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.messagebus.api.consumer.builder.CordaConsumerBuilder
import net.corda.messagebus.api.producer.builder.CordaProducerBuilder
import net.corda.messaging.api.processor.DurableProcessor
import net.corda.messaging.api.processor.EventLogProcessor
import net.corda.messaging.api.records.EventLogRecord
import net.corda.messaging.api.records.Record
import net.corda.messaging.api.subscription.Subscription
import net.corda.messaging.api.subscription.listener.PartitionAssignmentListener
import net.corda.messaging.config.ResolvedSubscriptionConfig

/**
 * Implementation of a DurableSubscription.
 *
 * Subscription will continuously try connect to Kafka based on the [config].
 * After connection is successful subscription will attempt to poll and process records until subscription is stopped.
 * Records are processed using the [processor]. Records outputted from the [processor] are sent back to kafka using a
 * producer built by [cordaProducerBuilder]. Records are consumed and produced atomically via transactions.
 *
 * Note: the semantics of a [DurableSubscriptionImpl] are the same with an [EventLogSubscriptionImpl] with the only difference
 * being the latter exposes a few more attributes for each record. As a result, the former is being implemented by delegating
 * any processing to the latter and filtering out the attributes that are not exposed.
 *
 * @property config configuration
 * @property cordaConsumerBuilder builder to generate a kafka consumer.
 * @property cordaProducerBuilder builder to generate a kafka producer.
 * @property processor processes records from kafka topic. Produces list of output records.
 * @property partitionAssignmentListener a callback listener that reacts to reassignments of partitions.
 *
 */

@Suppress("LongParameterList")
internal class DurableSubscriptionImpl<K : Any, V : Any>(
    private val config: ResolvedSubscriptionConfig,
    private val cordaConsumerBuilder: CordaConsumerBuilder,
    private val cordaProducerBuilder: CordaProducerBuilder,
    private val processor: DurableProcessor<K, V>,
    private val partitionAssignmentListener: PartitionAssignmentListener?,
    lifecycleCoordinatorFactory: LifecycleCoordinatorFactory
) : Subscription<K, V> {

    private val subscription = EventLogSubscriptionImpl(config, cordaConsumerBuilder, cordaProducerBuilder,
        ForwardingEventLogProcessor(processor), partitionAssignmentListener, lifecycleCoordinatorFactory)

    override val subscriptionName: LifecycleCoordinatorName
        get() = subscription.subscriptionName

    override fun start() {
        subscription.start()
    }

    override val isRunning: Boolean
        get() = subscription.isRunning

    override fun close() {
        subscription.close()
    }

    /**
     * A simple processor that forwards events to the underlying durable processor.
     */
    class ForwardingEventLogProcessor<K: Any, V: Any>(private val durableProcessor: DurableProcessor<K, V>): EventLogProcessor<K, V> {
        override fun onNext(events: List<EventLogRecord<K, V>>): List<Record<*, *>> {
            val records = events.map { Record(it.topic, it.key, it.value) }
            return durableProcessor.onNext(records)
        }

        override val keyClass: Class<K>
            get() = durableProcessor.keyClass
        override val valueClass: Class<V>
            get() = durableProcessor.valueClass
    }

}
