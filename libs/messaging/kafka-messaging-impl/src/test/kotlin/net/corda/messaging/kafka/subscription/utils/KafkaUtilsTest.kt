package net.corda.messaging.kafka.subscription.utils

import net.corda.messaging.api.records.Record
import net.corda.messaging.kafka.subscription.createMockConsumerAndAddRecords
import net.corda.messaging.kafka.utils.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.consumer.OffsetAndMetadata
import org.apache.kafka.clients.consumer.OffsetResetStrategy
import org.apache.kafka.common.TopicPartition
import java.time.Duration

class KafkaUtilsTest {

    @Test
    fun testToConsumerRecord() {
        val consumerRecord = ConsumerRecord("topic",1, 1, "key", "value".toByteArray())
        val record = consumerRecord.toRecord()
        assertThat(record.topic).isEqualTo(consumerRecord.topic())
        assertThat(record.key).isEqualTo(consumerRecord.key())
        assertThat(record.value).isEqualTo(consumerRecord.value())
    }


    @Test
    fun testToRecordFromProducer() {
        val record = Record("topic", "key", "value".toByteArray())
        val producerRecord = record.toProducerRecord()
        assertThat(producerRecord.topic()).isEqualTo(record.topic)
        assertThat(producerRecord.key()).isEqualTo(record.key)
        assertThat(producerRecord.value()).isEqualTo(record.value)
    }


    @Test
    fun testCommitSyncOffsets() {
        val topic = "MyTopic"
        val (consumer, topicPartition) = createMockConsumerAndAddRecords(topic,  10L, OffsetResetStrategy.NONE)
        val record = ConsumerRecord<String, ByteArray>(topic, 1, 5L, null, "value".toByteArray())
        assertThat(consumer.committed(setOf(topicPartition))).isEmpty()

        consumer.commitSyncOffsets(record, "meta data")
        val committedPositionAfterCommit = consumer.committed(setOf(topicPartition))
        assertThat(committedPositionAfterCommit.values.first().offset()).isEqualTo(6)
    }

    @Test
    fun testResetToLastCommittedPositions() {
        val topic = "MyTopic"
        val numberOfRecords = 10L
        val (consumer, topicPartition) = createMockConsumerAndAddRecords(topic, numberOfRecords, OffsetResetStrategy.EARLIEST)

        val positionBeforePoll = consumer.position(topicPartition)
        assertThat(positionBeforePoll).isEqualTo(0)
        consumer.poll(Duration.ZERO)

        //get current position after poll for this partition/topic
        val positionAfterPoll = consumer.position(topicPartition)
        assertThat(positionAfterPoll).isEqualTo(numberOfRecords)

        //Commit offset for half the records = offset of 5.
        val currentOffsets = mutableMapOf<TopicPartition, OffsetAndMetadata>()
        val offsetCommit = 5L
        currentOffsets[topicPartition] = OffsetAndMetadata(offsetCommit, "metaData")
        consumer.commitSync(currentOffsets)
        val positionAfterCommit = consumer.position(topicPartition)
        assertThat(positionAfterCommit).isEqualTo(numberOfRecords)

        //Reset fetch position to the last committed record
        consumer.resetToLastCommittedPositions()
        val positionAfterReset = consumer.position(topicPartition)
        assertThat(positionAfterReset).isEqualTo(offsetCommit)
    }

}