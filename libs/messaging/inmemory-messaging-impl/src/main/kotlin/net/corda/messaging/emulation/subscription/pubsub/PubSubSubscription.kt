package net.corda.messaging.emulation.subscription.pubsub

import net.corda.messaging.api.processor.PubSubProcessor
import net.corda.messaging.api.subscription.Subscription
import net.corda.messaging.api.subscription.factory.config.SubscriptionConfig
import net.corda.messaging.emulation.topic.model.Consumption
import net.corda.messaging.emulation.topic.model.RecordMetadata
import net.corda.messaging.emulation.topic.service.TopicService
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.debug
import net.corda.v5.base.util.uncheckedCast
import org.slf4j.Logger
import java.util.concurrent.ExecutorService
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * In-memory pub sub subscription.
 * @property subscriptionConfig config for subscription
 * @property processor processor to execute upon retrieved records from topic
 * @property executor processor is executed using this if it is not null
 * @property topicService retrieve records and commit offsets back to topics with this service
 */
class PubSubSubscription<K : Any, V : Any>(
    internal val subscriptionConfig: SubscriptionConfig,
    private val processor: PubSubProcessor<K, V>,
    private val executor: ExecutorService?,
    private val topicService: TopicService,
) : Subscription<K, V> {

    companion object {
        private val log: Logger = contextLogger()
    }

    private var currentConsumer: Consumption? = null
    private val lock = ReentrantLock()
    /**
     * Is the subscription running.
     */
    override val isRunning: Boolean
        get() {
            return currentConsumer?.isRunning ?: false
        }

    /**
     * Begin consuming events from the configured topic and process them
     * with the given [processor].
     */
    override fun start() {
        log.debug { "Starting subscription with config: $subscriptionConfig" }
        lock.withLock {
            if (currentConsumer == null) {
                val consumer = PubSubConsumer(this)
                currentConsumer = topicService.createConsumption(consumer)
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
        lock.withLock {
            currentConsumer?.stop()
            currentConsumer = null
        }
    }

    /**
     * Attempt to process a collection of [records] with the given [processor].
     * [processor] executed using the [executor] if it is not null.
     */
    internal fun processRecords(records: Collection<RecordMetadata>) {
        records.mapNotNull {
            it.castToType(processor.keyClass, processor.valueClass)
        }.onEach { record ->
            if (executor != null) {
                executor.submit { processor.onNext(uncheckedCast(record)) }.get()
            } else {
                processor.onNext(uncheckedCast(record))
            }
        }
    }
}
