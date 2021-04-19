package net.corda.messaging.kafka.subscription.subscriptions.pubsub

import net.corda.messaging.api.processor.PubSubProcessor
import net.corda.messaging.api.subscription.Subscription
import net.corda.messaging.api.subscription.factory.config.SubscriptionConfig
import net.corda.messaging.kafka.subscription.consumer.ConsumerBuilder
import net.corda.messaging.kafka.utils.commitSyncOffsets
import net.corda.messaging.kafka.utils.resetToLastCommittedPositions
import net.corda.messaging.kafka.utils.toRecord
import org.apache.kafka.clients.consumer.Consumer
import org.apache.kafka.clients.consumer.ConsumerRecords
import org.apache.kafka.clients.consumer.OffsetResetStrategy
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.lang.Exception
import java.lang.IllegalStateException
import java.time.Duration
import kotlin.concurrent.thread
import java.util.concurrent.ExecutorService

/**
 * Kafka implementation of a PubSubSubscription.
 * Subscription will continuously try connect to Kafka based on the [config] and [properties].
 * After connection is successful subscription will attempt to poll and process records until subscription is stopped.
 * Records are processed using the [executor] if it is not null. Otherwise they are processed on the same thread.
 * @property config Describes what topic to poll from and what the consumer group name should be.
 * @property properties properties used in building a kafka consumer.
 * @property consumerBuilder builder to generate a kafka consumer.
 * @property processor processes records from kafka topic. Does not produce any outputs.
 * @property executor if not null, processor is executed using the executor synchronously.
 *                    If executor is null processor executed on the same thread as the consumer.
 */
class KafkaPubSubSubscription<K, V>(
    private val config: SubscriptionConfig,
    private val properties: Map<String, String>,
    private val consumerBuilder: ConsumerBuilder<K, V>,
    private val processor: PubSubProcessor<K, V>,
    private val executor: ExecutorService?
) : Subscription<K, V> {

    companion object {
        val log: Logger = LoggerFactory.getLogger(this::class.java)
        //TODO - this needs to be set to a value long enough to allow the processor to complete between pol
        //otherwise there is risk of records being processed twice
        val CONSUMER_POLL: Duration = Duration.ofMillis(1000L)
    }

    @Volatile
    var cancelled = false
    lateinit var consumeLoopThread: Thread

    /**
     * Begin consuming events from the configured topic and process them
     * with the given [processor].
     */
    override fun start() {
        consumeLoopThread =  thread(
            start = true,
            isDaemon = false,
            contextClassLoader = null,
            name = "consumer",
            priority = -1,
            ::runConsumeLoop
        )
    }

    /**
     * Stop the subscription.
     */
    override fun stop() {
        cancelled = true
        executor?.shutdown()
    }

    /**
     * Create a Consumer for the given [config] and [properties] and subscribe to the topic.
     * Attempt to create this connection until it is successful while subscription is active.
     * After connection is made begin to process records indefinitely. Mark each record and committed after processing.
     * If an error occurs while processing reset the consumers position on the topic to the last committed position.
     * Execute the processor using the given [executor] if it is not null, otherwise execute on the current thread.
     */
    private fun runConsumeLoop() {
        val topic = config.eventTopic
        val groupName = config.groupName

        //keep trying to establish connection
        while (!cancelled) {
            try {
                val consumer = consumerBuilder.createConsumer(config, properties)
                consumer.subscribe(listOf(topic))

                //keep trying to process records
                try {
                    while (!cancelled) {
                        val records = consumer.poll(CONSUMER_POLL)
                        processRecords(records, executor, consumer)
                    }
                } catch (ex: Exception) {
                    // The consumer fetch position needs to be restored to the last committed offset
                    // before the transaction started.
                    // If there is no offset for this consumer then reset to the latest position on the topic
                    log.error("PubSubConsumer from group $groupName failed to read and process records from topic $topic." +
                            "Resetting to last committed offset.")
                    consumer.resetToLastCommittedPositions(OffsetResetStrategy.LATEST)
                }
            } catch(ex: IllegalStateException) {
                log.error("PubSubConsumer failed to subscribe a consumer from group $groupName to topic $topic. " +
                        "Consumer is already subscribed to this topic.", ex)
            } catch (ex: IllegalArgumentException) {
                log.error("PubSubConsumer failed to subscribe a consumer from group $groupName to topic $topic. " +
                        "Illegal args provided.", ex)
            } catch (ex: Exception) {
                log.error("PubSubConsumer failed to create a consumer for group $groupName on topic $topic.", ex)
            }
        }
    }

    /**
     * Sorts polled [record]s by their timestamp. Process them using an [executor] if it not null or on the same
     * thread otherwise. Commit the offset for each record back to the topic after processing them synchronously.
     */
    private fun processRecords(records: ConsumerRecords<K, V>, executor: ExecutorService?, consumer: Consumer<K, V>) {
        val sortedRecords = records.sortedBy { it.timestamp() }
        for (kafkaRecord in sortedRecords) {
            val event = kafkaRecord.toRecord()
            if (executor != null) {
                executor.submit { processor.onNext(event) }.get()
            } else {
                processor.onNext(event)
            }
            consumer.commitSyncOffsets(kafkaRecord)
        }
    }
}
