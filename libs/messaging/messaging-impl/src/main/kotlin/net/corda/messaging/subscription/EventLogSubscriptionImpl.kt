package net.corda.messaging.subscription

import java.util.UUID
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleStatus
import net.corda.messagebus.api.configuration.ConsumerConfig
import net.corda.messagebus.api.configuration.ProducerConfig
import net.corda.messagebus.api.constants.ConsumerRoles
import net.corda.messagebus.api.constants.ProducerRoles
import net.corda.messagebus.api.consumer.CordaConsumer
import net.corda.messagebus.api.consumer.CordaConsumerRecord
import net.corda.messagebus.api.consumer.CordaOffsetResetStrategy
import net.corda.messagebus.api.consumer.builder.CordaConsumerBuilder
import net.corda.messagebus.api.producer.CordaProducer
import net.corda.messagebus.api.producer.CordaProducerRecord
import net.corda.messagebus.api.producer.builder.CordaProducerBuilder
import net.corda.messaging.api.exception.CordaMessageAPIFatalException
import net.corda.messaging.api.exception.CordaMessageAPIIntermittentException
import net.corda.messaging.api.processor.EventLogProcessor
import net.corda.messaging.api.subscription.Subscription
import net.corda.messaging.api.subscription.listener.PartitionAssignmentListener
import net.corda.messaging.config.ResolvedSubscriptionConfig
import net.corda.messaging.subscription.consumer.listener.ForwardingRebalanceListener
import net.corda.messaging.utils.toCordaProducerRecords
import net.corda.messaging.utils.toEventLogRecord
import net.corda.metrics.CordaMetrics
import net.corda.schema.Schemas.Companion.getStateAndEventDLQTopic
import net.corda.v5.base.util.debug
import org.slf4j.LoggerFactory

/**
 * Implementation of an EventLogSubscription.
 *
 * Subscription will continuously try connect to Kafka based on the [config].
 * After connection is successful subscription will attempt to poll and process records until subscription is stopped.
 * Records are processed using the [processor]. Records outputted from the [processor] are sent back to kafka using a
 * producer built by [cordaProducerBuilder]. Records are consumed and produced atomically via transactions.
 *
 * @property config configuration
 * @property cordaConsumerBuilder builder to generate a kafka consumer.
 * @property cordaProducerBuilder builder to generate a kafka producer.
 * @property processor processes records from kafka topic. Produces list of output records.
 * @property partitionAssignmentListener a callback listener that reacts to reassignments of partitions.
 *
 */

@Suppress("LongParameterList")
internal class EventLogSubscriptionImpl<K : Any, V : Any>(
    private val config: ResolvedSubscriptionConfig,
    private val cordaConsumerBuilder: CordaConsumerBuilder,
    private val cordaProducerBuilder: CordaProducerBuilder,
    private val processor: EventLogProcessor<K, V>,
    private val partitionAssignmentListener: PartitionAssignmentListener?,
    lifecycleCoordinatorFactory: LifecycleCoordinatorFactory
) : Subscription<K, V> {

    private val log = LoggerFactory.getLogger(config.loggerName)

    private var threadLooper =
        ThreadLooper(log, config, lifecycleCoordinatorFactory, "durable processing thread", ::runConsumeLoop)

    private lateinit var deadLetterRecords: MutableList<ByteArray>

    private val errorMsg = "Failed to read and process records from topic ${config.topic}, group ${config.group}, producerClientId " +
            "${config.clientId}."

    private val processorMeter = CordaMetrics.Metric.MessageProcessorTime.builder()
        .withTag(CordaMetrics.Tag.MessagePatternType, "Durable")
        .withTag(CordaMetrics.Tag.MessagePatternClientId, config.clientId)
        .withTag(CordaMetrics.Tag.OperationName, "onNext")
        .build()

    override val isRunning: Boolean
        get() = threadLooper.isRunning

    override val subscriptionName: LifecycleCoordinatorName
        get() = threadLooper.lifecycleCoordinatorName

    /**
     * Begin consuming events from the configured topic, process them
     * with the given [processor] and send outputs to a topic.
     * @throws CordaMessageAPIFatalException if unrecoverable error occurs
     */
    override fun start() {
        log.debug { "Starting subscription with config:\n${config}" }
        threadLooper.start()
    }

    override fun close() = threadLooper.close()

    @Suppress("NestedBlockDepth")
    fun runConsumeLoop() {
        var attempts = 0
        var consumer: CordaConsumer<K, V>?
        var producer: CordaProducer?
        while (!threadLooper.loopStopped) {
            attempts++
            try {
                log.debug { "Attempt: $attempts" }
                deadLetterRecords = mutableListOf()
                val rebalanceListener = partitionAssignmentListener?.let {
                    ForwardingRebalanceListener(config.topic, config.group, config.clientId, it)
                }
                val consumerConfig = ConsumerConfig(config.group, config.clientId, ConsumerRoles.EVENT_LOG)
                consumer = cordaConsumerBuilder.createConsumer(
                    consumerConfig,
                    config.messageBusConfig,
                    processor.keyClass,
                    processor.valueClass,
                    { data ->
                        log.error("Failed to deserialize record from ${config.topic}")
                        deadLetterRecords.add(data)
                    },
                    rebalanceListener
                )
                val producerConfig = ProducerConfig(config.clientId, config.instanceId, true, ProducerRoles.EVENT_LOG)
                producer = cordaProducerBuilder.createProducer(producerConfig, config.messageBusConfig)
                consumer.use { cordaConsumer ->
                    cordaConsumer.subscribe(config.topic)
                    producer.use { cordaProducer ->
                        threadLooper.updateLifecycleStatus(LifecycleStatus.UP)
                        pollAndProcessRecords(cordaConsumer, cordaProducer)
                    }
                }
                attempts = 0
            } catch (ex: Exception) {
                when (ex) {
                    is CordaMessageAPIIntermittentException -> {
                        log.warn(
                            "$errorMsg Attempts: $attempts. Recreating consumer/producer and Retrying.", ex
                        )
                    }
                    else -> {
                        log.error(
                            "$errorMsg Attempts: $attempts. Closing subscription.", ex
                        )
                        threadLooper.updateLifecycleStatus(LifecycleStatus.ERROR, errorMsg)
                        threadLooper.stopLoop()
                    }
                }
            }
        }
    }

    /**
     * Poll records with the [consumer], process them with the [processor] and send outputs back to kafka atomically with the [producer].
     * If an exception is thrown while polling and processing then reset the fetch position to the last committed position.
     * If no offset is present on the topic reset poll position to the start of the topic.
     * If this continues to fail throw a [CordaMessageAPIIntermittentException] to break out of the loop.
     * This will recreate the consumer and producer and try again.
     * @throws CordaMessageAPIIntermittentException if the records cannot be polled or processed at the current position and max
     * retries have been exceeded.
     * @throws CordaMessageAPIFatalException Fatal unrecoverable error occurred. e.g misconfiguration
     */
    private fun pollAndProcessRecords(consumer: CordaConsumer<K, V>, producer: CordaProducer) {
        var attempts = 0
        while (!threadLooper.loopStopped) {
            try {
                processDurableRecords(consumer.poll(config.pollTimeout), producer, consumer)
                attempts = 0
            } catch (ex: Exception) {
                when (ex) {
                    is CordaMessageAPIFatalException -> {
                        throw ex
                    }
                    is CordaMessageAPIIntermittentException -> {
                        attempts++
                        handlePollAndProcessIntermittentError(attempts, consumer, ex)
                    }
                    else -> {
                        throw CordaMessageAPIFatalException(
                            "Failed to process records from topic ${config.topic}, " +
                                    "group ${config.group}, producerClientId ${config.clientId}. " +
                                    "Unexpected error occurred in this transaction. Closing producer.", ex
                        )
                    }
                }
            }
        }
    }

    /**
     * Handle whether to log a warning or to throw a [CordaMessageAPIIntermittentException]
     * Throw if max amount of [attempts] have been reached. Otherwisde log warning.
     */
    private fun handlePollAndProcessIntermittentError(
        attempts: Int,
        consumer: CordaConsumer<K, V>,
        ex: Exception
    ) {
        if (attempts <= config.processorRetries) {
            log.warn(
                "Failed to read and process records from topic ${config.topic}, group ${config.group}, " +
                        "producerClientId ${config.clientId}. " +
                        "Retrying poll and process. Attempts: $attempts."
            )
            consumer.resetToLastCommittedPositions(CordaOffsetResetStrategy.EARLIEST)
        } else {
            val message = "Failed to read and process records from topic ${config.topic}, group ${config.group}, " +
                    "producerClientId ${config.clientId}. " +
                    "Attempts: $attempts. Max reties for poll and process exceeded."
            log.warn(message, ex)
            throw CordaMessageAPIIntermittentException(message, ex)
        }
    }

    /**
     * Process Kafka [cordaConsumerRecords]. Commit the [consumer] offset for each record back to the topic after
     * processing them synchronously and writing output records back to kafka in a transaction.
     * If a record fails to deserialize skip this record and log the error.
     * @throws CordaMessageAPIIntermittentException error occurred that can be retried.
     * @throws CordaMessageAPIFatalException Fatal unrecoverable error occurred. e.g misconfiguration
     */
    private fun processDurableRecords(
        cordaConsumerRecords: List<CordaConsumerRecord<K, V>>,
        producer: CordaProducer,
        consumer: CordaConsumer<K, V>
    ) {
        if (cordaConsumerRecords.isEmpty()) {
            return
        }

        try {
            producer.beginTransaction()
            val outputs = processorMeter.recordCallable { processor.onNext(cordaConsumerRecords.map { it.toEventLogRecord() })
                .toCordaProducerRecords() }!!
            producer.sendRecords(outputs)
            if(deadLetterRecords.isNotEmpty()) {
                producer.sendRecords(deadLetterRecords.map {
                    CordaProducerRecord(
                        getStateAndEventDLQTopic(config.topic),
                        UUID.randomUUID().toString(),
                        it
                    )
                })
            }
            producer.sendAllOffsetsToTransaction(consumer)
            producer.commitTransaction()
        } catch (ex: Exception) {
            when (ex) {
                is CordaMessageAPIFatalException,
                is CordaMessageAPIIntermittentException -> {
                    throw ex
                }
                else -> {
                    throw CordaMessageAPIFatalException(
                        "Failed to process records from topic ${config.topic}, " +
                                "group ${config.group}, producerClientId ${config.clientId}. " +
                                "Unexpected error occurred in this transaction. Closing producer.", ex
                    )
                }
            }
        }
    }
}
