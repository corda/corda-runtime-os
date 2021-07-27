package net.corda.messaging.kafka.subscription.net.corda.messaging.kafka.utils

import net.corda.messaging.kafka.subscription.consumer.wrapper.ConsumerRecordAndMeta
import net.corda.messaging.kafka.subscription.generateMockConsumerRecords
import net.corda.messaging.kafka.utils.getEventsByBatch
import net.corda.messaging.kafka.utils.getRecordListOffsets

import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.common.TopicPartition
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class BatchUtilsTest {

    @Test
    fun `test events by batch, small batch ordered`() {
        val records = mutableListOf<ConsumerRecordAndMeta<String, String>>()
        var offset = 0
        for (i in 0 until 3) {
            for (j in 0 until 10) {
                records.add(ConsumerRecordAndMeta("", ConsumerRecord("", 1, offset.toLong(), "key$i", "value$j")))
                offset++
            }
        }

        val eventsByBatch  = getEventsByBatch(records)
        assertThat(eventsByBatch.size).isEqualTo(28)
    }

    @Test
    fun `test events by batch, large batches ordered`() {
        val records = mutableListOf<ConsumerRecordAndMeta<String, String>>()
        var offset = 0
        for (j in 0 until 3) {
            for (i in 0 until 10) {
                records.add(ConsumerRecordAndMeta("", ConsumerRecord("", 1, offset.toLong(), "key$i", "value$j")))
                offset++
            }
        }

        val eventsByBatch  = getEventsByBatch(records)
        assertThat(eventsByBatch.size).isEqualTo(3)
    }


    @Test
    fun `test events by batch, large batches unordered`() {
        val records = mutableListOf<ConsumerRecordAndMeta<String, String>>()
        for ((offset, i) in listOf(1, 2, 3, 1, 1, 2, 3, 3, 2).withIndex()) {
            records.add(ConsumerRecordAndMeta("", ConsumerRecord("", 1, offset.toLong(), "key$i", "$offset")))
        }

        val eventsByBatch  = getEventsByBatch(records)
        assertThat(eventsByBatch.size).isEqualTo(4)

        val batchOne = eventsByBatch[0]
        assertThat(batchOne.size).isEqualTo(3)
        assertThat(batchOne[0].record.value()).isEqualTo("0")
        assertThat(batchOne[1].record.value()).isEqualTo("1")
        assertThat(batchOne[2].record.value()).isEqualTo("2")

        val batchTwo = eventsByBatch[1]
        assertThat(batchTwo.size).isEqualTo(1)
        assertThat(batchTwo[0].record.value()).isEqualTo("3")

        val batchThree = eventsByBatch[2]
        assertThat(batchThree.size).isEqualTo(3)
        assertThat(batchThree[0].record.value()).isEqualTo("4")
        assertThat(batchThree[1].record.value()).isEqualTo("5")
        assertThat(batchThree[2].record.value()).isEqualTo("6")

        val batchFour = eventsByBatch[3]
        assertThat(batchFour.size).isEqualTo(2)
        assertThat(batchFour[0].record.value()).isEqualTo("7")
        assertThat(batchFour[1].record.value()).isEqualTo("8")
    }

    @Test
    fun `test events by batch, no records`() {
        val records = mutableListOf<ConsumerRecordAndMeta<String, String>>()
        val eventsByBatch  = getEventsByBatch(records)
        assertThat(eventsByBatch.size).isEqualTo(0)
    }

    @Test
    fun `test generated offsets for list of records`() {
        val records = generateMockConsumerRecords(3, "TOPIC1", 0) +
                generateMockConsumerRecords(4, "TOPIC1", 1) +
                generateMockConsumerRecords(3, "TOPIC2", 0)

        val recordOffsets  = getRecordListOffsets(records)
        assertThat(recordOffsets.size).isEqualTo(3)
        assertThat(recordOffsets[TopicPartition("TOPIC1", 0)]!!.offset()).isEqualTo(3)
        assertThat(recordOffsets[TopicPartition("TOPIC1", 1)]!!.offset()).isEqualTo(4)
        assertThat(recordOffsets[TopicPartition("TOPIC2", 0)]!!.offset()).isEqualTo(3)
    }
}
