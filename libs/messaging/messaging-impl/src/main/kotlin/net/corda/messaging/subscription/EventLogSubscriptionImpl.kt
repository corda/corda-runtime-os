package net.corda.messaging.subscription

import com.typesafe.config.Config
import net.corda.libs.configuration.schema.messaging.INSTANCE_ID
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleStatus
import net.corda.messagebus.api.configuration.ConfigProperties.Companion.CONSUMER_GROUP_ID
import net.corda.messagebus.api.configuration.ConfigProperties.Companion.CONSUMER_POLL_AND_PROCESS_RETRIES
import net.corda.messagebus.api.configuration.ConfigProperties.Companion.CONSUMER_THREAD_STOP_TIMEOUT
import net.corda.messagebus.api.configuration.ConfigProperties.Companion.PRODUCER_CLIENT_ID
import net.corda.messagebus.api.configuration.ConfigProperties.Companion.PRODUCER_TRANSACTIONAL_ID
import net.corda.messagebus.api.consumer.CordaConsumer
import net.corda.messagebus.api.consumer.CordaConsumerRecord
import net.corda.messagebus.api.consumer.CordaOffsetResetStrategy
import net.corda.messagebus.api.producer.CordaProducer
import net.corda.messagebus.api.producer.CordaProducerRecord
import net.corda.messagebus.api.producer.builder.CordaProducerBuilder
import net.corda.messaging.api.exception.CordaMessageAPIFatalException
import net.corda.messaging.api.exception.CordaMessageAPIIntermittentException
import net.corda.messaging.api.processor.EventLogProcessor
import net.corda.messaging.api.subscription.Subscription
import net.corda.messaging.api.subscription.listener.PartitionAssignmentListener
import net.corda.messaging.properties.ConfigProperties.Companion.KAFKA_CONSUMER
import net.corda.messaging.properties.ConfigProperties.Companion.KAFKA_PRODUCER
import net.corda.messaging.properties.ConfigProperties.Companion.TOPIC_NAME
import net.corda.messaging.subscription.consumer.builder.CordaConsumerBuilder
import net.corda.messaging.subscription.consumer.listener.ForwardingRebalanceListener
import net.corda.messaging.utils.render
import net.corda.messaging.utils.toCordaProducerRecords
import net.corda.messaging.utils.toEventLogRecord
import net.corda.schema.Schemas.Companion.getStateAndEventDLQTopic
import net.corda.v5.base.util.debug
import org.slf4j.LoggerFactory
import java.util.*
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.thread
import kotlin.concurrent.withLock

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
class EventLogSubscriptionImpl<K : Any, V : Any>(
    private val config: Config,
    private val cordaConsumerBuilder: CordaConsumerBuilder,
    private val cordaProducerBuilder: CordaProducerBuilder,
    private val processor: EventLogProcessor<K, V>,
    private val partitionAssignmentListener: PartitionAssignmentListener?,
    private val lifecycleCoordinatorFactory: LifecycleCoordinatorFactory
) : Subscription<K, V> {

    private val log = LoggerFactory.getLogger(
        "${config.getString(CONSUMER_GROUP_ID)}.${config.getString(PRODUCER_TRANSACTIONAL_ID)}"
    )

    private val consumerThreadStopTimeout = config.getLong(CONSUMER_THREAD_STOP_TIMEOUT)
    private val consumerPollAndProcessRetries = config.getLong(CONSUMER_POLL_AND_PROCESS_RETRIES)

    @Volatile
    private var stopped = false
    private val lock = ReentrantLock()
    private var consumeLoopThread: Thread? = null
    private val topic = config.getString(TOPIC_NAME)
    private val groupName = config.getString(CONSUMER_GROUP_ID)
    private val producerClientId: String = config.getString(PRODUCER_CLIENT_ID)
    private lateinit var deadLetterRecords: MutableList<ByteArray>
    private val lifecycleCoordinator = lifecycleCoordinatorFactory.createCoordinator(
        LifecycleCoordinatorName(
            "$groupName-KafkaDurableSubscription-$topic",
            //we use instanceId here as transactionality is a concern in this subscription
            config.getString(INSTANCE_ID)
        )
    ) { _, _ -> }

    private val errorMsg = "Failed to read and process records from topic $topic, group $groupName, producerClientId " +
            "$producerClientId."


    /**
     * Is the subscription running.
     */
    override val isRunning: Boolean
        get() {
            return !stopped
        }

    override val subscriptionName: LifecycleCoordinatorName
        get() = lifecycleCoordinator.name

    /**
     * Begin consuming events from the configured topic, process them
     * with the given [processor] and send outputs to a topic.
     * @throws CordaMessageAPIFatalException if unrecoverable error occurs
     */
    override fun start() {
        log.debug { "Starting subscription with config:\n${config.render()}" }
        lock.withLock {
            if (consumeLoopThread == null) {
                stopped = false
                lifecycleCoordinator.start()
                consumeLoopThread = thread(
                    start = true,
                    isDaemon = true,
                    contextClassLoader = null,
                    name = "durable processing thread $groupName-$topic",
                    priority = -1,
                    block = ::runConsumeLoop
                )
            }
        }
    }

    /**
     * Stop the subscription.
     */
    override fun stop() {
        if (!stopped) {
            stopConsumeLoop()
            lifecycleCoordinator.stop()
        }
    }

    override fun close() {
        if (!stopped) {
            stopConsumeLoop()
            lifecycleCoordinator.close()
        }
    }

    private fun stopConsumeLoop() {
        val thread = lock.withLock {
            stopped = true
            val threadTmp = consumeLoopThread
            consumeLoopThread = null
            threadTmp
        }
        thread?.join(consumerThreadStopTimeout)
    }

    @Suppress("NestedBlockDepth")
    fun runConsumeLoop() {
        var attempts = 0
        var consumer: CordaConsumer<K, V>?
        var producer: CordaProducer?
        while (!stopped) {
            attempts++
            try {
                log.debug { "Attempt: $attempts" }
                deadLetterRecords = mutableListOf()
                consumer = if (partitionAssignmentListener != null) {
                    val consumerGroup = config.getString(CONSUMER_GROUP_ID)
                    val rebalanceListener =
                        ForwardingRebalanceListener(topic, consumerGroup, partitionAssignmentListener)
                    cordaConsumerBuilder.createDurableConsumer(
                        config.getConfig(KAFKA_CONSUMER),
                        processor.keyClass,
                        processor.valueClass,
                        { data ->
                            log.error("Failed to deserialize record from $topic")
                            deadLetterRecords.add(data)
                        },
                        cordaConsumerRebalanceListener = rebalanceListener
                    )
                } else {
                    cordaConsumerBuilder.createDurableConsumer(
                        config.getConfig(KAFKA_CONSUMER),
                        processor.keyClass,
                        processor.valueClass,
                        { data ->
                            log.error("Failed to deserialize record from $topic")
                            deadLetterRecords.add(data)
                        }
                    )
                }
                producer = cordaProducerBuilder.createProducer(config.getConfig(KAFKA_PRODUCER))
                consumer.use { cordaConsumer ->
                    cordaConsumer.subscribe(topic)
                    producer.use { cordaProducer ->
                        lifecycleCoordinator.updateStatus(LifecycleStatus.UP)
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
                        lifecycleCoordinator.updateStatus(LifecycleStatus.ERROR, errorMsg)
                        stop()
                    }
                }
            }
        }
        lifecycleCoordinator.updateStatus(LifecycleStatus.DOWN)
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
        while (!stopped) {
            try {
                processDurableRecords(consumer.poll(), producer, consumer)
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
                            "Failed to process records from topic $topic, " +
                                    "group $groupName, producerClientId $producerClientId. " +
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
        if (attempts <= consumerPollAndProcessRetries) {
            log.warn(
                "Failed to read and process records from topic $topic, group $groupName, " +
                        "producerClientId $producerClientId. " +
                        "Retrying poll and process. Attempts: $attempts."
            )
            consumer.resetToLastCommittedPositions(CordaOffsetResetStrategy.EARLIEST)
        } else {
            val message = "Failed to read and process records from topic $topic, group $groupName, " +
                    "producerClientId $producerClientId. " +
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
            producer.sendRecords(processor.onNext(cordaConsumerRecords.map { it.toEventLogRecord() }).toCordaProducerRecords())
            if(deadLetterRecords.isNotEmpty()) {
                producer.sendRecords(deadLetterRecords.map {
                    CordaProducerRecord(
                        getStateAndEventDLQTopic(topic),
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
                        "Failed to process records from topic $topic, " +
                                "group $groupName, producerClientId $producerClientId. " +
                                "Unexpected error occurred in this transaction. Closing producer.", ex
                    )
                }
            }
        }
    }

}
