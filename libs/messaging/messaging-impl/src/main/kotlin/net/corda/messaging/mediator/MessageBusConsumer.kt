package net.corda.messaging.mediator

import net.corda.messagebus.api.consumer.CordaConsumer
import net.corda.messagebus.api.consumer.CordaConsumerRecord
import net.corda.messagebus.api.consumer.CordaOffsetResetStrategy
import net.corda.messaging.api.mediator.MediatorConsumer
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.Duration

/**
 * Message bus consumer that reads messages from configured topic.
 */
class MessageBusConsumer<K: Any, V: Any>(
    private val topic: String,
    private val consumer: CordaConsumer<K, V>,
): MediatorConsumer<K, V> {
    private companion object {
        private val log: Logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    override fun subscribe() = consumer.subscribe(topic)

    override fun poll(timeout: Duration): List<CordaConsumerRecord<K, V>> {
        log.info("Polling from topic [$topic]")
        val records = consumer.poll(timeout)
        log.info("Polled [${records.size}] records from topic [$topic]")
        return records
    }

    override fun syncCommitOffsets() = consumer.syncCommitOffsets()

    override fun resetEventOffsetPosition() =
        consumer.resetToLastCommittedPositions(CordaOffsetResetStrategy.EARLIEST)

    override fun close() = consumer.close()
}