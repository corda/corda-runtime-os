package net.corda.messaging.kafka.subscription.durable

import com.typesafe.config.Config
import net.corda.messaging.api.exception.CordaMessageAPIFatalException
import net.corda.messaging.api.exception.CordaMessageAPIIntermittentException
import net.corda.messaging.api.processor.DurableProcessor
import net.corda.messaging.api.subscription.Subscription
import net.corda.messaging.api.subscription.factory.config.SubscriptionConfig
import net.corda.messaging.kafka.properties.KafkaProperties.Companion.CONSUMER_POLL_AND_PROCESS_RETRIES
import net.corda.messaging.kafka.properties.KafkaProperties.Companion.CONSUMER_THREAD_STOP_TIMEOUT
import net.corda.messaging.kafka.subscription.consumer.builder.ConsumerBuilder
import net.corda.messaging.kafka.subscription.consumer.wrapper.CordaKafkaConsumer
import net.corda.messaging.kafka.subscription.producer.builder.SubscriptionProducerBuilder
import net.corda.messaging.kafka.subscription.producer.wrapper.CordaKafkaProducer
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.consumer.OffsetResetStrategy
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.lang.Exception
import java.nio.ByteBuffer
import kotlin.concurrent.thread
import java.util.concurrent.locks.ReentrantLock
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
class KafkaDurableSubscription<K : Any, V : Any>(
    private val subscriptionConfig: SubscriptionConfig,
    private val config: Config,
    private val consumerBuilder: ConsumerBuilder<K, V>,
    private val producerBuilder: SubscriptionProducerBuilder,
    private val processor: DurableProcessor<K, V>) : Subscription<K, V> {

    companion object {
        private val log: Logger = LoggerFactory.getLogger(this::class.java)
    }
    private val consumerThreadStopTimeout = config.getLong(CONSUMER_THREAD_STOP_TIMEOUT)
    private val consumerPollAndProcessRetries = config.getLong(CONSUMER_POLL_AND_PROCESS_RETRIES)

    @Volatile
    private var stopped = false
    private val lock = ReentrantLock()
    private var consumeLoopThread: Thread? = null
    private val topic = subscriptionConfig.eventTopic
    private val groupName = subscriptionConfig.groupName

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
        lock.withLock {
            if (consumeLoopThread == null) {
                stopped = false
                consumeLoopThread = thread(
                    start = true,
                    isDaemon = true,
                    contextClassLoader = null,
                    name = "pubsub processing thread ${subscriptionConfig.groupName}-${subscriptionConfig.eventTopic}",
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
     * After connection is made begin to process records indefinitely. Mark each record and committed after processing
     * is completed and outputs are written to their respective topics.
     * If an error occurs while processing reset the consumers position on the topic to the last committed position.
     * If subscription is stopped close the consumer.
     * @throws CordaMessageAPIFatalException if unrecoverable error occurs
     */
    @Suppress("TooGenericExceptionCaught")
    fun runConsumeLoop() {
        var attempts = 0
        var consumer: CordaKafkaConsumer<K, V>?
        var producer: CordaKafkaProducer?
        while (!stopped) {
            attempts++
            try {
                consumer = consumerBuilder.createDurableConsumer(subscriptionConfig)
                producer = producerBuilder.createProducer(consumer.consumer)
                consumer.use { cordaConsumer ->
                    cordaConsumer.subscribeToTopic()
                    producer.use { cordaProducer ->
                        pollAndProcessRecords(cordaConsumer, cordaProducer)
                    }
                }
                attempts = 0
            } catch (ex: CordaMessageAPIIntermittentException) {
                log.warn("Failed to read and process records from topic $topic, group $groupName, " +
                        "attempts: $attempts. Recreating consumer/producer and Retrying.", ex)
            } catch (ex: CordaMessageAPIFatalException) {
                log.error("PubSubConsumer failed to create and subscribe consumer for group $groupName, topic $topic. " +
                        "Fatal error occurred. Closing subscription.", ex)
                stop()
            }  catch (ex: Exception) {
                log.error("PubSubConsumer failed to create and subscribe consumer for group $groupName, topic $topic, " +
                        "attempts: $attempts. " +
                        "Unexpected error occurred. Closing subscription.", ex)
                stop()
            }
        }
    }

    /**
     * Poll records with the [consumer], process them with the [processor] and send outputs back to kafka atomically.
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
                processDurableRecords(consumer.poll(), consumer, producer)
                attempts = 0
            } catch (ex: Exception) {
                when (ex) {
                    is CordaMessageAPIFatalException -> { throw ex }
                    else -> {
                        attempts++
                        consumer.resetToLastCommittedPositions(OffsetResetStrategy.EARLIEST)
                        if (attempts <= consumerPollAndProcessRetries) {
                            log.warn("PubSubConsumer from group $groupName failed to read and process records from topic $topic. " +
                                    "retrying. Attempts: $attempts.")
                        } else {
                            val message = "PubSubConsumer from group $groupName failed to read and process records from topic $topic. " +
                                    "Max reties for poll and process exceeded."
                            log.warn(message, ex)
                            throw CordaMessageAPIIntermittentException(message, ex)
                        }
                    }
                }
            }
        }
    }

    /**
     * Process Kafka [consumerRecords].  Commit the offset for each record back to the topic after processing them synchronously.
     * If a record fails to deserialize skip this record and log the error.
     * @throws CordaMessageAPIIntermittentException error occurred that can be retried.
     * @throws CordaMessageAPIFatalException Fatal unrecoverable error occurred. e.g misconfiguration
     */
    @Suppress("TooGenericExceptionCaught")
    private fun processDurableRecords(
        consumerRecords: List<ConsumerRecord<K, ByteBuffer>>,
        consumer: CordaKafkaConsumer<K, V>,
        producer: CordaKafkaProducer) {
        for (consumerRecord in consumerRecords) {
            val eventRecord = try {
                consumer.getRecord(consumerRecord)
            } catch (ex: CordaMessageAPIFatalException) {
                log.error("PubSubConsumer from group $groupName failed to deserialize record with " +
                            "key ${consumerRecord.key()} from topic $topic. Skipping record.", ex)
                //TODO - this will throw a warning in kafka about mixing consumer/producer offsets
                // but will be removed when Ryans SerDe PR is merged
                consumer.commitSyncOffsets(consumerRecord)
                continue
            }

            try {
                producer.beginTransaction()
                producer.sendRecords(processor.onNext(eventRecord))
                producer.sendOffsetsToTransaction()
                producer.commitTransaction()
            } catch (ex: Exception) {
                when(ex) {
                    is CordaMessageAPIFatalException,
                    is CordaMessageAPIIntermittentException -> {
                        throw ex
                    }
                    else -> {
                        throw CordaMessageAPIFatalException("Unexpected error occurred in transaction. Closing producer.", ex)
                    }
                }
            }
        }
    }
}
