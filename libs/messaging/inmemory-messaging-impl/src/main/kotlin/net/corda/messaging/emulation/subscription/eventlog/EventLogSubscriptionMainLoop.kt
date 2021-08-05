package net.corda.messaging.emulation.subscription.eventlog

import net.corda.messaging.api.records.EventLogRecord
import net.corda.messaging.emulation.topic.model.OffsetStrategy
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.uncheckedCast
import org.slf4j.Logger
import java.util.concurrent.atomic.AtomicBoolean

/**
 * A single lifecycle of the EventLogSubscription. This can not be rerun.
 *
 * @property subscription - the detail of the subscription.
 * @property threadFactory - A factory to create threads.
 */
class EventLogSubscriptionMainLoop<K : Any, V : Any>(
    private val subscription: EventLogSubscription<K, V>,
    private val threadFactory: (Runnable) -> Thread = { Thread(it) }
) : Runnable {

    companion object {
        private val logger: Logger = contextLogger()
    }

    private val keepRunning = AtomicBoolean(true)
    private val thread by lazy {
        val thread = threadFactory(this)
        thread.name = "events log processing thread ${subscription.group}-${subscription.topic}"
        thread.isDaemon = true
        thread.contextClassLoader = null
        thread
    }

    override fun run() {
        // Subscribe only once...
        if (!subscription.subscribedToTopic.getAndSet(true)) {
            subscription.topicService.subscribe(
                topicName = subscription.topic,
                consumerGroup = subscription.group,
                OffsetStrategy.EARLIEST,
            )
        }
        while (keepRunning.get()) {
            val records = subscription.topicService.getRecords(
                topicName = subscription.topic,
                consumerGroup = subscription.group,
                numberOfRecords = subscription.config.pollSize,
                autoCommitOffset = false,
            )
                .filter {
                    subscription.processor.valueClass.isInstance(it.record.value)
                }
                .filter {
                    subscription.processor.keyClass.isInstance(it.record.key)
                }
                .map {
                    EventLogRecord(
                        topic = it.record.topic,
                        key = it.record.key,
                        value = it.record.value,
                        partition = subscription.partitioner(it.record),
                        offset = it.offset
                    )
                }

            if (records.isNotEmpty()) {
                val maxOffset = records.maxOf { it.offset }
                val minOffset = records.minOf { it.offset }
                @Suppress("TooGenericExceptionCaught")
                try {
                    subscription.processor.onNext(uncheckedCast(records))
                    subscription.topicService.commitOffset(
                        topicName = subscription.topic,
                        consumerGroup = subscription.group,
                        offset = maxOffset + 1
                    )
                } catch (ex: Exception) {
                    logger.warn(
                        "Error processing records for consumer ${subscription.group}, topic ${subscription.topic}. " +
                            "Will try again ($minOffset - $maxOffset)",
                        ex
                    )
                }
            }
        }
    }

    fun start() {
        thread.start()
    }

    fun stop() {
        keepRunning.set(false)
        thread.join()
    }
}
