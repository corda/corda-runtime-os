package net.corda.messaging.mediator

import net.corda.messagebus.api.consumer.CordaConsumer
import net.corda.messagebus.api.consumer.CordaConsumerRecord
import net.corda.messagebus.api.consumer.CordaOffsetResetStrategy
import net.corda.messaging.api.mediator.MediatorConsumer
import net.corda.messaging.mediator.processor.TopicOffsetManager
import java.time.Duration

/**
 * Message bus consumer that reads messages from configured topic.
 */
class MessageBusConsumer<K: Any, V: Any>(
    private val topic: String,
    private val consumer: CordaConsumer<K, V>,
): MediatorConsumer<K, V> {
    private val offsetManager = TopicOffsetManager()

    override fun subscribe() {
        offsetManager.assigned()
        consumer.subscribe(topic)
    }

    override fun poll(timeout: Duration): List<CordaConsumerRecord<K, V>> {
        val records = consumer.poll(timeout)
        records.forEach { offsetManager.recordPolledOffset(it.partition, it.offset) }
        return records
    }

    override fun syncCommitOffsets(records: List<CordaConsumerRecord<K, V>>) {
        if (records.isEmpty()) return
        records.forEach { offsetManager.recordOffsetPreCommit(it.partition, it.offset) }
        val prototype = records.first()
        try {
            consumer.syncCommitOffsets(
                offsetManager.getCommittableOffsets()
                    .map { CordaConsumerRecord(topic, it.key, it.value, prototype.key, null, 0L) })
            offsetManager.commit()
        } catch (t: Throwable) {
            offsetManager.rollback()
            throw t
        }
    }

    override fun resetEventOffsetPosition() =
        consumer.resetToLastCommittedPositions(CordaOffsetResetStrategy.EARLIEST)

    override fun close() = consumer.close()
}