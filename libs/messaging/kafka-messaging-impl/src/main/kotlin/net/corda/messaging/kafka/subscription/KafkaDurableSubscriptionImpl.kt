package net.corda.messaging.kafka.subscription

import com.typesafe.config.Config
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.messaging.api.processor.DurableProcessor
import net.corda.messaging.api.processor.EventLogProcessor
import net.corda.messaging.api.records.EventLogRecord
import net.corda.messaging.api.records.Record
import net.corda.messaging.api.subscription.PartitionAssignmentListener
import net.corda.messaging.api.subscription.Subscription
import net.corda.messaging.kafka.producer.builder.ProducerBuilder
import net.corda.messaging.kafka.properties.ConfigProperties.Companion.CONSUMER_GROUP_ID
import net.corda.messaging.kafka.properties.ConfigProperties.Companion.PRODUCER_TRANSACTIONAL_ID
import net.corda.messaging.kafka.subscription.consumer.builder.ConsumerBuilder
import org.slf4j.LoggerFactory

/**
 * Kafka implementation of a DurableSubscription.
 * Subscription will continuously try connect to Kafka based on the [config].
 * After connection is successful subscription will attempt to poll and process records until subscription is stopped.
 * Records are processed using the [processor]. Records outputted from the [processor] are sent back to kafka using a
 * producer built by [producerBuilder]. Records are consumed and produced atomically via transactions.
 *
 * Note: the semantics of a [KafkaDurableSubscriptionImpl] are the same with an [KafkaEventLogSubscriptionImpl] with the only difference
 * being the latter exposes a few more attributes for each record. As a result, the former is being implemented by delegating
 * any processing to the latter and filtering out the attributes that are not exposed.
 *
 * @property config configuration
 * @property consumerBuilder builder to generate a kafka consumer.
 * @property producerBuilder builder to generate a kafka producer.
 * @property processor processes records from kafka topic. Produces list of output records.
 * @property partitionAssignmentListener a callback listener that reacts to reassignments of partitions.
 *
 */

@Suppress("LongParameterList")
class KafkaDurableSubscriptionImpl<K : Any, V : Any>(
    private val config: Config,
    private val consumerBuilder: ConsumerBuilder<K, V>,
    private val producerBuilder: ProducerBuilder,
    private val processor: DurableProcessor<K, V>,
    private val partitionAssignmentListener: PartitionAssignmentListener?,
    private val lifecycleCoordinatorFactory: LifecycleCoordinatorFactory
) : Subscription<K, V> {

    private val log = LoggerFactory.getLogger(
        "${config.getString(CONSUMER_GROUP_ID)}.${config.getString(PRODUCER_TRANSACTIONAL_ID)}"
    )

    private val subscription = KafkaEventLogSubscriptionImpl(config, consumerBuilder, producerBuilder,
        ForwardingEventLogProcessor(processor), partitionAssignmentListener, lifecycleCoordinatorFactory)

    override val subscriptionName: LifecycleCoordinatorName
        get() = subscription.subscriptionName

    override fun start() {
        subscription.start()
    }

    override val isRunning: Boolean
        get() = subscription.isRunning

    override fun stop() {
        subscription.stop()
    }

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
