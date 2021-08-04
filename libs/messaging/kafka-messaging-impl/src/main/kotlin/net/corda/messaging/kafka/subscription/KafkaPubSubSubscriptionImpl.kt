package net.corda.messaging.kafka.subscription

import com.typesafe.config.Config
import net.corda.messaging.api.exception.CordaMessageAPIFatalException
import net.corda.messaging.api.exception.CordaMessageAPIIntermittentException
import net.corda.messaging.api.processor.PubSubProcessor
import net.corda.messaging.api.subscription.Subscription
import net.corda.messaging.kafka.properties.KafkaProperties.Companion.CONSUMER_GROUP_ID
import net.corda.messaging.kafka.properties.KafkaProperties.Companion.CONSUMER_POLL_AND_PROCESS_RETRIES
import net.corda.messaging.kafka.properties.KafkaProperties.Companion.CONSUMER_THREAD_STOP_TIMEOUT
import net.corda.messaging.kafka.properties.KafkaProperties.Companion.KAFKA_CONSUMER
import net.corda.messaging.kafka.properties.KafkaProperties.Companion.TOPIC_NAME
import net.corda.messaging.kafka.subscription.consumer.builder.ConsumerBuilder
import net.corda.messaging.kafka.subscription.consumer.wrapper.ConsumerRecordAndMeta
import net.corda.messaging.kafka.subscription.consumer.wrapper.CordaKafkaConsumer
import net.corda.messaging.kafka.subscription.consumer.wrapper.asRecord
import net.corda.messaging.kafka.utils.render
import net.corda.v5.base.types.toHexString
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.debug
import org.apache.kafka.clients.consumer.OffsetResetStrategy
import org.slf4j.Logger
import java.util.concurrent.ExecutorService
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.thread
import kotlin.concurrent.withLock

/**
 * Kafka implementation of a PubSubSubscription.
 * Subscription will continuously try connect to Kafka based on the [subscriptionConfig] and [config].
 * After connection is successful subscription will attempt to poll and process records until subscription is stopped.
 * Records are processed using the [executor] if it is not null. Otherwise they are processed on the same thread.
 * [executor] will be shutdown when the subscription is stopped.
 * @property subscriptionConfig Describes what topic to poll from and what the consumer group name should be.
 * @property config kafka configuration
 * @property consumerBuilder builder to generate a kafka consumer.
 * @property processor processes records from kafka topic. Does not produce any outputs.
 * @property executor if not null, processor is executed using the executor synchronously.
 *                    If executor is null processor executed on the same thread as the consumer.
 *
 */
class KafkaPubSubSubscriptionImpl<K : Any, V : Any>(
    private val config: Config,
    private val consumerBuilder: ConsumerBuilder<K, V>,
    private val processor: PubSubProcessor<K, V>,
    private val executor: ExecutorService?
) : Subscription<K, V> {

    companion object {
        private val log: Logger = contextLogger()
    }

    private val consumerThreadStopTimeout = config.getLong(CONSUMER_THREAD_STOP_TIMEOUT)
    private val consumerPollAndProcessRetries = config.getLong(CONSUMER_POLL_AND_PROCESS_RETRIES)

    @Volatile
    private var stopped = false
    private val lock = ReentrantLock()
    private var consumeLoopThread: Thread? = null
    private val topic = config.getString(TOPIC_NAME)
    private val groupName = config.getString(CONSUMER_GROUP_ID)

    /**
     * Is the subscription running.
     */
    override val isRunning: Boolean
        get() {
            return !stopped
        }

    /**
     * Begin consuming events from the configured topic and process them
     * with the given [processor].
     * @throws CordaMessageAPIFatalException if unrecoverable error occurs
     */
    override fun start() {
        log.debug { "Starting subscription with config:\n${config.render()}" }
        lock.withLock {
            if (consumeLoopThread == null) {
                stopped = false
                consumeLoopThread = thread(
                    true,
                    isDaemon = true,
                    contextClassLoader = null,
                    name = "pubsub processing thread $groupName-$topic",
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
            executor?.shutdown()
            thread?.join(consumerThreadStopTimeout)
        }
    }

    /**
     * Create a Consumer for the given [subscriptionConfig] and [config] and subscribe to the topic.
     * Attempt to create this connection until it is successful while subscription is active.
     * After connection is made begin to process records indefinitely. Mark each record and committed after processing.
     * If an error occurs while processing reset the consumers position on the topic to the last committed position.
     * Execute the processor using the given [executor] if it is not null, otherwise execute on the current thread.
     * If subscription is stopped close the consumer.
     * @throws CordaMessageAPIFatalException if unrecoverable error occurs
     */
    @Suppress("TooGenericExceptionCaught")
    fun runConsumeLoop() {
        var attempts = 0
        while (!stopped) {
            attempts++
            try {
                consumerBuilder.createPubSubConsumer(
                    config.getConfig(KAFKA_CONSUMER), processor.keyClass, processor.valueClass,::logFailedDeserialize
                ).use {
                    it.subscribeToTopic()
                    pollAndProcessRecords(it)
                }
                attempts = 0
            } catch (ex: CordaMessageAPIIntermittentException) {
                log.warn(
                    "PubSubConsumer from group $groupName failed to read and process records from topic $topic, " +
                            "attempts: $attempts. Retrying.", ex
                )
            } catch (ex: CordaMessageAPIFatalException) {
                log.error(
                    "PubSubConsumer failed to create and subscribe consumer for group $groupName, topic $topic. " +
                            "Fatal error occurred. Closing subscription.", ex
                )
                stop()
            } catch (ex: Exception) {
                log.error(
                    "PubSubConsumer failed to create and subscribe consumer for group $groupName, topic $topic, " +
                            "attempts: $attempts. " +
                            "Unexpected error occurred. Closing subscription.", ex
                )
                stop()
            }
        }
    }

    /**
     * Poll records with the [consumer] and process them with the [processor].
     * If an exception is thrown while polling and processing then reset the fetch position and try to poll and process again.
     * If this continues to fail break out of the loop. This will recreate the consumer to fetch at the latest position and poll again.
     * @throws CordaMessageAPIIntermittentException if the records cannot be polled at the current position or cannot be processed and max
     * retries have been exceeded.
     */
    @Suppress("TooGenericExceptionCaught")
    private fun pollAndProcessRecords(consumer: CordaKafkaConsumer<K, V>) {
        var attempts = 0
        while (!stopped) {
            try {
                val consumerRecords = consumer.poll()
                processPubSubRecords(consumerRecords, consumer)
                attempts = 0
            } catch (ex: Exception) {
                attempts++
                if (attempts <= consumerPollAndProcessRetries) {
                    log.warn(
                        "PubSubConsumer from group $groupName failed to read and process records from topic $topic." +
                                "Resetting to last committed offset and retrying. Attempts: $attempts."
                    )
                    consumer.resetToLastCommittedPositions(OffsetResetStrategy.LATEST)
                } else {
                    val message =
                        "PubSubConsumer from group $groupName failed to read and process records from topic $topic." +
                                "Max reties for poll and process exceeded. Recreating consumer and polling from latest position."
                    log.warn(message, ex)
                    throw CordaMessageAPIIntermittentException(message, ex)
                }
            }
        }
    }

    /**
     * Process Kafka [consumerRecords]. Process them using an [executor] if it not null or on the same
     * thread otherwise. Commit the offset for each record back to the topic after processing them synchronously.
     * If a record fails to deserialize skip this record and log the error.
     */
    private fun processPubSubRecords(consumerRecords: List<ConsumerRecordAndMeta<K, V>>, consumer: CordaKafkaConsumer<K, V>) {
        consumerRecords.forEach {
            if (executor != null) {
                executor.submit { processor.onNext(it.asRecord()) }.get()
            } else {
                processor.onNext(it.asRecord())
            }
            consumer.commitSyncOffsets(it.record)
        }
    }

    private fun logFailedDeserialize(topic: String, data: ByteArray) {
        log.error("Failed to deserialize a record on $topic: (${data.toHexString()}")
    }
}
