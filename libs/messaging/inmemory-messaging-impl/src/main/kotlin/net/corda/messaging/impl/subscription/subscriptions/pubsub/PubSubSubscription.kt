package net.corda.messaging.impl.subscription.subscriptions.pubsub

import com.typesafe.config.Config
import net.corda.messaging.api.exception.CordaMessageAPIFatalException
import net.corda.messaging.api.processor.PubSubProcessor
import net.corda.messaging.api.records.Record
import net.corda.messaging.api.subscription.Subscription
import net.corda.messaging.api.subscription.factory.config.SubscriptionConfig
import net.corda.messaging.impl.properties.InMemProperties.Companion.TOPICS_POLL_SIZE
import net.corda.messaging.impl.subscription.subscriptions.pubsub.service.TopicService
import net.corda.messaging.impl.subscription.subscriptions.pubsub.service.model.OffsetStrategy
import net.corda.v5.base.internal.uncheckedCast
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.lang.Exception
import java.util.concurrent.ExecutorService
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.thread
import kotlin.concurrent.withLock

class PubSubSubscription<K : Any, V : Any>(
    private val subscriptionConfig: SubscriptionConfig,
    private val config: Config,
    private val processor: PubSubProcessor<K, V>,
    private val executor: ExecutorService?,
    private val topicService: TopicService,
) : Subscription<K, V> {

    companion object {
        private val log: Logger = LoggerFactory.getLogger(this::class.java)
    }

    private var consumeLoopThread: Thread? = null
    private val pollSize = config.getInt(TOPICS_POLL_SIZE)
    @Volatile
    private var stopped = false
    private val lock = ReentrantLock()
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
     * Begin consuming events from the configured topic and process them
     * with the given [processor].
     * @throws CordaMessageAPIFatalException if unrecoverable error occurs
     */
    override fun start() {
        lock.withLock {
            if (consumeLoopThread == null) {
                stopped = false
                topicService.subscribe(topic, groupName, OffsetStrategy.LATEST)
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
        if (!stopped) {
            lock.withLock {
                stopped = true
                topicService.unsubscribe(topic, groupName)
                executor?.shutdown()
            }
        }
    }

    private fun runConsumeLoop() {
        while (!stopped) {
            val records = topicService.getRecords(topic, groupName, pollSize)
            for (record in records) {
                processRecord(record)
            }
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private fun processRecord(record: Record<*, *>) {
        try {
            if (executor != null) {
                executor.submit { processor.onNext(uncheckedCast(record)) }.get()
            } else {
                processor.onNext(uncheckedCast(record))
            }
        } catch (ex: Exception) {
            val message =
                "PubSubConsumer from group $groupName failed to process record with key ${record.key} from topic $topic."
            log.warn(message, ex)
        }
    }
}
