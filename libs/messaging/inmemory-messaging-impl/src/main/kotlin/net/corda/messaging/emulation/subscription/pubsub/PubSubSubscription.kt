package net.corda.messaging.emulation.subscription.pubsub

import com.typesafe.config.Config
import net.corda.messaging.api.processor.PubSubProcessor
import net.corda.messaging.api.records.Record
import net.corda.messaging.api.subscription.Subscription
import net.corda.messaging.emulation.properties.InMemProperties.Companion.CONSUMER_THREAD_STOP_TIMEOUT
import net.corda.messaging.emulation.properties.InMemProperties.Companion.TOPICS_POLL_SIZE
import net.corda.messaging.emulation.subscription.factory.InMemSubscriptionFactory.Companion.EVENT_TOPIC
import net.corda.messaging.emulation.subscription.factory.InMemSubscriptionFactory.Companion.GROUP_NAME
import net.corda.messaging.emulation.topic.model.OffsetStrategy
import net.corda.messaging.emulation.topic.service.TopicService
import net.corda.v5.base.internal.uncheckedCast
import net.corda.v5.base.util.debug
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.concurrent.ExecutorService
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.thread
import kotlin.concurrent.withLock

/**
 * In-memory pub sub subscription.
 * @property config config for subscription
 * @property processor processor to execute upon retrieved records from topic
 * @property executor processor is executed using this if it is not null
 * @property topicService retrieve records and commit offsets back to topics with this service
 */
class PubSubSubscription<K : Any, V : Any>(
    private val config: Config,
    private val processor: PubSubProcessor<K, V>,
    private val executor: ExecutorService?,
    private val topicService: TopicService,
) : Subscription<K, V> {

    companion object {
        private val log: Logger = LoggerFactory.getLogger(this::class.java)
    }

    private val pollSize = config.getInt(TOPICS_POLL_SIZE)
    private val consumerThreadStopTimeout = config.getLong(CONSUMER_THREAD_STOP_TIMEOUT)
    private val topic = config.getString(EVENT_TOPIC)
    private val groupName = config.getString(GROUP_NAME)

    @Volatile
    private var stopped = false
    private var consumeLoopThread: Thread? = null
    private val lock = ReentrantLock()

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
     */
    override fun start() {
        log.debug { "Starting subscription with config: $config" }
        lock.withLock {
            if (consumeLoopThread == null) {
                stopped = false
                topicService.subscribe(topic, groupName, OffsetStrategy.LATEST)
                consumeLoopThread = thread(
                    start = true,
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
     * Unsubscribe from the topic
     * Stop the executor.
     * Join the thread
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
     * Get and process records while not [stopped].
     * Sleep [pollInterval] between poll and process.
     * If polled records fail to process log a warning and skip them.
     */
    @Suppress("TooGenericExceptionCaught")
    private fun runConsumeLoop() {
        while (!stopped) {
            val records = topicService.getRecords(topic, groupName, pollSize)
            try {
                for (recordMetaData in records) {
                    processRecord(uncheckedCast(recordMetaData.record))
                }
            } catch (ex: Exception) {
                log.warn("Error processing processing records for consumer $groupName, topic $topic. " +
                        "Skipping to offset ${records.last().offset}")
            }
        }
    }

    /**
     * Attempt to process a [record] with the given [processor].
     * [processor] executed using the [executor] if it is not null.
     */
    private fun processRecord(record: Record<K, V>) {
        if (executor != null) {
            executor.submit { processor.onNext(record) }.get()
        } else {
            processor.onNext(record)
        }
    }
}
