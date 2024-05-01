package net.corda.messaging.mediator

import net.corda.messagebus.api.CordaTopicPartition
import net.corda.messagebus.api.consumer.CordaConsumer
import net.corda.messagebus.api.consumer.CordaConsumerRebalanceListener
import net.corda.messagebus.api.consumer.CordaConsumerRecord
import net.corda.messagebus.api.consumer.CordaOffsetResetStrategy
import net.corda.messaging.api.mediator.MediatorConsumer
import net.corda.messaging.mediator.processor.TopicOffsetManager
import org.slf4j.LoggerFactory
import java.time.Duration

/**
 * Message bus consumer that reads messages from configured topic.
 */
class MessageBusConsumer<K : Any, V : Any>(
    private val topic: String,
    private val consumer: CordaConsumer<K, V>,
) : MediatorConsumer<K, V>, CordaConsumerRebalanceListener {
    private companion object {
        private val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    private val offsetManager = TopicOffsetManager(topic)

    override fun subscribe() {
        offsetManager.assigned()
        consumer.subscribe(topic, CordaConsumerRebalanceListener.concat(this, consumer.getDefaultRebalanceListener()))
    }

    override fun poll(timeout: Duration): List<CordaConsumerRecord<K, V>> {
        val records = consumer.poll(timeout)
        records.forEach { offsetManager.recordPolledOffset(it.partition, it.offset) }
        return records
    }

    override fun tagRecords(records: List<CordaConsumerRecord<K, V>>, tag: String) {
        records.forEach { offsetManager.recordOffsetTag(it.partition, it.offset, tag) }
    }

    override fun alreadySyncedOffset(topic: String, partition: Int, offset: Long): Boolean? {
        if (topic != this.topic) return null
        return (offsetManager.getLowestUncommittedOffset(partition) ?: Long.MAX_VALUE) > offset
    }

    override fun syncCommitOffsets(records: List<CordaConsumerRecord<K, V>>) {
        if (records.isEmpty()) return
        records.forEach { offsetManager.recordOffsetPreCommit(it.partition, it.offset) }
        log.info("syncCommitOffsets before $offsetManager")
        val prototype = records.first()
        try {
            consumer.syncCommitOffsets(
                offsetManager.getCommittableOffsets()
                    .map { CordaConsumerRecord(topic, it.key, it.value, prototype.key, null, 0L) })
            offsetManager.commit()
        } catch (t: Throwable) {
            offsetManager.rollback()
            throw t
        } finally {
            log.info("syncCommitOffsets after $offsetManager")
        }
    }

    override fun resetEventOffsetPosition() =
        consumer.resetToLastCommittedPositions(CordaOffsetResetStrategy.EARLIEST)

    override fun close() = consumer.close()
    override fun onPartitionsRevoked(partitions: Collection<CordaTopicPartition>) {
        partitions.filter { it.topic == topic }.forEach { offsetManager.revokePartition(it.partition) }
    }

    override fun onPartitionsAssigned(partitions: Collection<CordaTopicPartition>) {
        partitions.filter { it.topic == topic }.forEach { offsetManager.assignPartition(it.partition) }
    }
}