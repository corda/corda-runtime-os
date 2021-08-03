package net.corda.messaging.emulation.subscription.eventlog

import net.corda.messaging.api.records.EventLogRecord
import net.corda.messaging.emulation.topic.model.OffsetStrategy
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.uncheckedCast
import org.slf4j.Logger
import java.util.concurrent.atomic.AtomicBoolean

class EventLogSubscriptionThread<K : Any, V : Any>(
    private val subscription: EventLogSubscription<K, V>,
    private val threadFactory: (Runnable) -> Thread = { Thread(it) }
) : Runnable {

    companion object {
        private val logger: Logger = contextLogger()

        @Suppress("ForbiddenComment")
        // TODO: Where do I get those?
        private const val POLL_SIZE = 5
    }

    private val keepRunning = AtomicBoolean(true)
    private val thread by lazy {
        val thread = threadFactory(this)
        thread.name = "events log processing thread ${subscription.group}-${subscription.topic}"
        thread.isDaemon = true
        thread.contextClassLoader = null
        thread.priority = -1
        thread
    }

    override fun run() {
        subscription.topicService.subscribe(
            topicName = subscription.topic,
            consumerGroup = subscription.group,
            OffsetStrategy.LATEST,
        )
        while (keepRunning.get()) {
            val records = subscription.topicService.getRecords(
                topicName = subscription.topic,
                consumerGroup = subscription.group,
                numberOfRecords = POLL_SIZE
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
                @Suppress("TooGenericExceptionCaught")
                try {
                    subscription.processor.onNext(uncheckedCast(records))
                } catch (ex: Exception) {
                    logger.warn(
                        "Error processing processing records for consumer ${subscription.group}, topic ${subscription.topic}. " +
                            "Skipping to offset ${records.last().offset}",
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
