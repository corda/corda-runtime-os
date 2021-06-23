package net.corda.messaging.kafka.subscription

import com.typesafe.config.Config
import net.corda.messaging.api.exception.CordaMessageAPIFatalException
import net.corda.messaging.api.exception.CordaMessageAPIIntermittentException
import net.corda.messaging.api.processor.DurableProcessor
import net.corda.messaging.api.subscription.Subscription
import net.corda.messaging.kafka.producer.builder.ProducerBuilder
import net.corda.messaging.kafka.producer.wrapper.CordaKafkaProducer
import net.corda.messaging.kafka.properties.KafkaProperties.Companion.CONSUMER_GROUP_ID
import net.corda.messaging.kafka.properties.KafkaProperties.Companion.CONSUMER_POLL_AND_PROCESS_RETRIES
import net.corda.messaging.kafka.properties.KafkaProperties.Companion.CONSUMER_THREAD_STOP_TIMEOUT
import net.corda.messaging.kafka.properties.KafkaProperties.Companion.KAFKA_CONSUMER
import net.corda.messaging.kafka.properties.KafkaProperties.Companion.KAFKA_PRODUCER
import net.corda.messaging.kafka.properties.KafkaProperties.Companion.PRODUCER_CLIENT_ID
import net.corda.messaging.kafka.properties.KafkaProperties.Companion.TOPIC_NAME
import net.corda.messaging.kafka.render
import net.corda.messaging.kafka.subscription.consumer.builder.ConsumerBuilder
import net.corda.messaging.kafka.subscription.consumer.wrapper.ConsumerRecordAndMeta
import net.corda.messaging.kafka.subscription.consumer.wrapper.CordaKafkaConsumer
import net.corda.messaging.kafka.subscription.consumer.wrapper.asRecord
import net.corda.v5.base.util.debug
import org.apache.kafka.clients.consumer.OffsetResetStrategy
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.thread
import kotlin.concurrent.withLock

/**
 * Kafka implementation of a DurableSubscription.
 * Subscription will continuously try connect to Kafka based on the [subscriptionConfig] and [config].
 * After connection is successful subscription will attempt to poll and process records until subscription is stopped.
 * Records are processed using the [processor]. Records outputted from the [processor] are sent back to kafka using a
 * producer built by [producerBuilder]. Records are consumed and produced atomically via transactions.
 * @property subscriptionConfig Describes what topic to poll from and what the consumer group name should be.
 * @property config configuration
 * @property consumerBuilder builder to generate a kafka consumer.
 * @property producerBuilder builder to generate a kafka producer.
 * @property processor processes records from kafka topic. Produces list of output records.
 *
 */
class KafkaDurableSubscriptionImpl<K : Any, V : Any>(
    private val config: Config,
    private val consumerBuilder: ConsumerBuilder<K, V>,
    private val producerBuilder: ProducerBuilder,
    private val processor: DurableProcessor<K, V>
) : Subscription<K, V> {

    private val log: Logger = LoggerFactory.getLogger(config.getString(PRODUCER_CLIENT_ID))

    private val consumerThreadStopTimeout = config.getLong(CONSUMER_THREAD_STOP_TIMEOUT)
    private val consumerPollAndProcessRetries = config.getLong(CONSUMER_POLL_AND_PROCESS_RETRIES)

    @Volatile
    private var stopped = false
    private val lock = ReentrantLock()
    private var consumeLoopThread: Thread? = null
    private val topic = config.getString(TOPIC_NAME)
    private val groupName = config.getString(CONSUMER_GROUP_ID)
    private val producerClientId: String = config.getString(PRODUCER_CLIENT_ID)

    /**
     * Is the subscription running.
     */
    override val isRunning: Boolean
        get() {
            return !stopped
        }

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
            val thread = lock.withLock {
                stopped = true
                val threadTmp = consumeLoopThread
                consumeLoopThread = null
                threadTmp
            }
            thread?.join(consumerThreadStopTimeout)
        }
    }

    /**
     * Create a Consumer for the given [subscriptionConfig] and [config] and subscribe to the topic.
     * Attempt to create this connection until it is successful while subscription is active.
     * After connection is made begin to process records indefinitely. Mark each record as committed after processing
     * is completed and outputs are written to their respective topics.
     * If an error occurs while processing, reset the consumers position on the topic to the last committed position.
     * If subscription is stopped close the consumer.
     */
    @Suppress("TooGenericExceptionCaught")
    fun runConsumeLoop() {
        var attempts = 0
        var consumer: CordaKafkaConsumer<K, V>?
        var producer: CordaKafkaProducer?
        while (!stopped) {
            attempts++
            try {
                log.debug { "Attempt: $attempts" }
                consumer = consumerBuilder.createDurableConsumer(config.getConfig(KAFKA_CONSUMER))
                producer = producerBuilder.createProducer(config.getConfig(KAFKA_PRODUCER))
                consumer.use { cordaConsumer ->
                    cordaConsumer.subscribeToTopic()
                    producer.use { cordaProducer ->
                        pollAndProcessRecords(cordaConsumer, cordaProducer)
                    }
                }
                attempts = 0
            } catch (ex: Exception) {
                when (ex) {
                    is CordaMessageAPIIntermittentException -> {
                        log.warn(
                            "Failed to read and process records from topic $topic, group $groupName, producerClientId $producerClientId. " +
                                    "Attempts: $attempts. Recreating consumer/producer and Retrying.", ex
                        )
                    }
                    else -> {
                        log.error(
                            "Failed to read and process records from topic $topic, group $groupName, producerClientId $producerClientId. " +
                                    "Attempts: $attempts. Closing subscription.", ex
                        )
                        stop()
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
    @Suppress("TooGenericExceptionCaught")
    private fun pollAndProcessRecords(consumer: CordaKafkaConsumer<K, V>, producer: CordaKafkaProducer) {
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
        consumer: CordaKafkaConsumer<K, V>,
        ex: Exception
    ) {
        if (attempts <= consumerPollAndProcessRetries) {
            log.warn(
                "Failed to read and process records from topic $topic, group $groupName, " +
                        "producerClientId $producerClientId. " +
                        "Retrying poll and process. Attempts: $attempts."
            )
            consumer.resetToLastCommittedPositions(OffsetResetStrategy.EARLIEST)
        } else {
            val message = "Failed to read and process records from topic $topic, group $groupName, " +
                    "producerClientId $producerClientId. " +
                    "Attempts: $attempts. Max reties for poll and process exceeded."
            log.warn(message, ex)
            throw CordaMessageAPIIntermittentException(message, ex)
        }
    }

    /**
     * Process Kafka [consumerRecords]. Commit the [consumer] offset for each record back to the topic after processing them synchronously
     * and writing output records back to kafka in a transaction.
     * If a record fails to deserialize skip this record and log the error.
     * @throws CordaMessageAPIIntermittentException error occurred that can be retried.
     * @throws CordaMessageAPIFatalException Fatal unrecoverable error occurred. e.g misconfiguration
     */
    @Suppress("TooGenericExceptionCaught")
    private fun processDurableRecords(
        consumerRecords: List<ConsumerRecordAndMeta<K, V>>,
        producer: CordaKafkaProducer,
        consumer: CordaKafkaConsumer<K, V>
    ) {
        if (consumerRecords.isEmpty()) {
            return
        }

        try {
            producer.beginTransaction()
            producer.sendRecords(processor.onNext(consumerRecords.map { it.asRecord() }))
            producer.sendOffsetsToTransaction(consumer)
            producer.tryCommitTransaction()
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
