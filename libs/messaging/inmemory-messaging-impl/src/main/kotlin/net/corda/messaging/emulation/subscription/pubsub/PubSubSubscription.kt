package net.corda.messaging.emulation.subscription.pubsub

import com.typesafe.config.Config
import net.corda.lifecycle.Lifecycle
import net.corda.messaging.api.processor.PubSubProcessor
import net.corda.messaging.api.subscription.Subscription
import net.corda.messaging.emulation.subscription.factory.InMemSubscriptionFactory.Companion.EVENT_TOPIC
import net.corda.messaging.emulation.subscription.factory.InMemSubscriptionFactory.Companion.GROUP_NAME
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
        private val log: Logger = contextLogger()
    }

    internal val topic = config.getString(EVENT_TOPIC)
    internal val groupName = config.getString(GROUP_NAME)

    private var currentConsumer: Lifecycle? = null
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
        log.debug { "Starting subscription with config: $config" }
        lock.withLock {
            if (currentConsumer == null) {
                val consumer = PubSubConsumer(this)
                currentConsumer = topicService.subscribe(consumer)
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
