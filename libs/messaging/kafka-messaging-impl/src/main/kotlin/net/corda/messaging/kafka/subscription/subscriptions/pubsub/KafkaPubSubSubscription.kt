package net.corda.messaging.kafka.subscription.subscriptions.pubsub

import com.typesafe.config.Config
import net.corda.messaging.api.exception.CordaMessageAPIFatalException
import net.corda.messaging.api.processor.PubSubProcessor
import net.corda.messaging.api.subscription.Subscription
import net.corda.messaging.api.subscription.factory.config.SubscriptionConfig
import net.corda.messaging.kafka.properties.KafkaProperties.Companion.CONSUMER_PROCESSOR_RETRIES
import net.corda.messaging.kafka.properties.KafkaProperties.Companion.CONSUMER_THREAD_STOP_TIMEOUT
import net.corda.messaging.kafka.subscription.consumer.builder.ConsumerBuilder
import net.corda.messaging.kafka.subscription.consumer.wrapper.CordaKafkaConsumer
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.consumer.OffsetResetStrategy
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.lang.Exception
import kotlin.concurrent.thread
import java.util.concurrent.ExecutorService
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Kafka implementation of a PubSubSubscription.
 * Subscription will continuously try connect to Kafka based on the [subscriptionConfig] and [kafkaConfig].
 * After connection is successful subscription will attempt to poll and process records until subscription is stopped.
 * Records are processed using the [executor] if it is not null. Otherwise they are processed on the same thread.
 * [executor] will be shutdown when the subscription is stopped.
 * @property subscriptionConfig Describes what topic to poll from and what the consumer group name should be.
 * @property kafkaConfig kafka configuration
 * @property consumerBuilder builder to generate a kafka consumer.
 * @property processor processes records from kafka topic. Does not produce any outputs.
 * @property executor if not null, processor is executed using the executor synchronously.
 *                    If executor is null processor executed on the same thread as the consumer.
 *
 */
class KafkaPubSubSubscription<K, V>(
    private val subscriptionConfig: SubscriptionConfig,
    private val kafkaConfig: Config,
    private val consumerBuilder: ConsumerBuilder<K, V>,
    private val processor: PubSubProcessor<K, V>,
    private val executor: ExecutorService?
) : Subscription<K, V> {

    companion object {
        private val log: Logger = LoggerFactory.getLogger(this::class.java)
    }
    private val consumerThreadStopTimeout = kafkaConfig.getLong(CONSUMER_THREAD_STOP_TIMEOUT)
    private val consumerProcessorRetries = kafkaConfig.getLong(CONSUMER_PROCESSOR_RETRIES)

    @Volatile
    private var cancelled = false
    private val lock = ReentrantLock()
    private var consumeLoopThread: Thread? = null
    private var cordaKafkaConsumer: CordaKafkaConsumer<K, V>? = null
    private val topic = subscriptionConfig.eventTopic
    private val groupName = subscriptionConfig.groupName

    /**
     * Begin consuming events from the configured topic and process them
     * with the given [processor].
     * @throws CordaMessageAPIFatalException fatal exception thrown during the consume, process or produce stage of a subscription.
     */
    override fun start() {
        lock.withLock {
            if (consumeLoopThread == null) {
                cancelled = false
                consumeLoopThread = thread(
                    true,
                    true,
                    null,
                    "pubsub processing thread ${subscriptionConfig.groupName}-${subscriptionConfig.eventTopic}",
                    -1,
                    ::runConsumeLoop
                )
            }
        }
    }

    /**
     * Stop the subscription.
     */
    override fun stop() {
        if (!cancelled) {
            val thread = lock.withLock {
                cancelled = true
                val threadTmp = consumeLoopThread
                consumeLoopThread = null
                threadTmp
            }
            thread?.join(consumerThreadStopTimeout)
            executor?.shutdown()
        }
    }

    /**
     * Create a Consumer for the given [subscriptionConfig] and [consumerProperties] and subscribe to the topic.
     * Attempt to create this connection until it is successful while subscription is active.
     * After connection is made begin to process records indefinitely. Mark each record and committed after processing.
     * If an error occurs while processing reset the consumers position on the topic to the last committed position.
     * Execute the processor using the given [executor] if it is not null, otherwise execute on the current thread.
     * If subscription is stopped close the consumer.
     */
    @Suppress("TooGenericExceptionCaught")
    private fun runConsumeLoop() {
        var attempts = 0
        while (!cancelled) {
            attempts++
            try {
                cordaKafkaConsumer = consumerBuilder.createConsumerAndSubscribe(subscriptionConfig)
                pollAndProcessRecords(cordaKafkaConsumer!!)
                attempts = 0
            } catch (ex: Exception) {
                log.warn("PubSubConsumer failed to create consumer for group $groupName, topic $topic, attempts: $attempts. Retrying.", ex)
            }
        }

        cordaKafkaConsumer?.close()
    }

    /**
     * Poll records with the [consumer] and process them with the [processor].
     * If an exception is thrown while polling and processing then reset the fetch position and try to poll and process again.
     * If this continues to fail break out of the loop. This will recreate the consumer to fetch at the latest position and poll again.
     */
    @Suppress("TooGenericExceptionCaught")
    private fun pollAndProcessRecords(consumer: CordaKafkaConsumer<K, V>) {
        var attempts = 0
        while (!cancelled) {
            try {
                val consumerRecords = consumer.poll()
                processPubSubRecords(consumerRecords, consumer)
                attempts = 0
            } catch (ex: Exception) {
                attempts++
                if (attempts < consumerProcessorRetries) {
                    log.warn("PubSubConsumer from group $groupName failed to read and process records from topic $topic." +
                                "Resetting to last committed offset and retrying. Attempts: $attempts.")
                    consumer.resetToLastCommittedPositions(OffsetResetStrategy.LATEST)
                } else {
                    log.warn("PubSubConsumer from group $groupName failed to read and process records from topic $topic." +
                            "Max reties for poll and process exceeded. Recreating consumer and polling from latest position.", ex)
                    break
                }
            }
        }
    }

    /**
     * Process Kafka [consumerRecords]. Process them using an [executor] if it not null or on the same
     * thread otherwise. Commit the offset for each record back to the topic after processing them synchronously.
     */
    private fun processPubSubRecords(consumerRecords: List<ConsumerRecord<K, V>>, consumer: CordaKafkaConsumer<K, V>) {
        for (consumerRecord in consumerRecords) {
            val eventRecord = consumer.getRecord(consumerRecord)
            if (executor != null) {
                executor.submit { processor.onNext(eventRecord) }.get()
            } else {
                processor.onNext(eventRecord)
            }
            consumer.commitSyncOffsets(consumerRecord)
        }
    }
}
