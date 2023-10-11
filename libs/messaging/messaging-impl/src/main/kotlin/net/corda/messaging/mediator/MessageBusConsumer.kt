package net.corda.messaging.mediator

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import net.corda.messagebus.api.CordaTopicPartition
import net.corda.messagebus.api.consumer.CordaConsumer
import net.corda.messagebus.api.consumer.CordaConsumerRecord
import net.corda.messagebus.api.consumer.CordaOffsetResetStrategy
import net.corda.messaging.api.mediator.MediatorConsumer
import org.slf4j.LoggerFactory
import java.time.Duration

/**
 * Message bus consumer that reads messages from configured topic.
 */
class MessageBusConsumer<K: Any, V: Any>(
    private val topic: String,
    private val consumer: CordaConsumer<K, V>,
): MediatorConsumer<K, V> {
    companion object {
        private val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    override fun subscribe() =
        consumer.subscribe(topic)

    override fun poll(timeout: Duration): Deferred<List<CordaConsumerRecord<K, V>>> =
        CompletableDeferred<List<CordaConsumerRecord<K, V>>>().apply {
            try {
                log.info("CordaConsumer.poll([$timeout]) started")
                val result = consumer.poll(timeout)
                log.info("CordaConsumer.poll([$timeout]) finished")
                complete(result)
            } catch (throwable: Throwable) {
                log.info("CordaConsumer.poll([$timeout]) error", throwable)
                completeExceptionally(throwable)
            }
        }

    override fun asyncCommitOffsets(): Deferred<Map<CordaTopicPartition, Long>> =
        CompletableDeferred<Map<CordaTopicPartition, Long>>().apply {
            try {
                log.info("CordaConsumer.asyncCommitOffsets begin")
                consumer.asyncCommitOffsets { offsets, exception ->
                    log.info("CordaConsumer.asyncCommitOffsets callback begin")
                    if (exception != null) {
                        log.info("CordaConsumer.asyncCommitOffsets error", exception)
                        completeExceptionally(exception)
                    } else {
                        log.info("CordaConsumer.asyncCommitOffsets completing")
                        complete(offsets)
                    }
                    log.info("CordaConsumer.asyncCommitOffsets callback end")
                }
            } catch (throwable: Throwable) {
                log.info("CordaConsumer.asyncCommitOffsets error", throwable)
                completeExceptionally(throwable)
            }
        }

    override fun resetEventOffsetPosition() =
        consumer.resetToLastCommittedPositions(CordaOffsetResetStrategy.EARLIEST)

    override fun close() =
        consumer.close()
}