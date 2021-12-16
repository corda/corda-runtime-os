package net.corda.messaging.kafka.subscription.net.corda.messaging.kafka.utils

import net.corda.messagebus.api.consumer.CordaConsumerRecord
import net.corda.messaging.kafka.utils.getEventsByBatch
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class BatchUtilsTest {

    @Test
    fun `test events by batch, small batch ordered`() {
        val records = mutableListOf<CordaConsumerRecord<String, String>>()
        var offset = 0
        for (i in 0 until 3) {
            for (j in 0 until 10) {
                records.add(CordaConsumerRecord("", 1, offset.toLong(), "key$i", "value$j", 0))
                offset++
            }
        }

        val eventsByBatch  = getEventsByBatch(records)
        assertThat(eventsByBatch.size).isEqualTo(28)
    }

    @Test
    fun `test events by batch, large batches ordered`() {
        val records = mutableListOf<CordaConsumerRecord<String, String>>()
        var offset = 0
        for (j in 0 until 3) {
            for (i in 0 until 10) {
                records.add(CordaConsumerRecord("", 1, offset.toLong(), "key$i", "value$j", 0))
                offset++
            }
        }

        val eventsByBatch  = getEventsByBatch(records)
        assertThat(eventsByBatch.size).isEqualTo(3)
    }


    @Test
    fun `test events by batch, large batches unordered`() {
        val records = mutableListOf<CordaConsumerRecord<String, String>>()
        for ((offset, i) in listOf(1, 2, 3, 1, 1, 2, 3, 3, 2).withIndex()) {
            records.add(CordaConsumerRecord("", 1, offset.toLong(), "key$i", "$offset", 0))
        }

        val eventsByBatch  = getEventsByBatch(records)
        assertThat(eventsByBatch.size).isEqualTo(4)

        val batchOne = eventsByBatch[0]
        assertThat(batchOne.size).isEqualTo(3)
        assertThat(batchOne[0].value).isEqualTo("0")
        assertThat(batchOne[1].value).isEqualTo("1")
        assertThat(batchOne[2].value).isEqualTo("2")

        val batchTwo = eventsByBatch[1]
        assertThat(batchTwo.size).isEqualTo(1)
        assertThat(batchTwo[0].value).isEqualTo("3")

        val batchThree = eventsByBatch[2]
        assertThat(batchThree.size).isEqualTo(3)
        assertThat(batchThree[0].value).isEqualTo("4")
        assertThat(batchThree[1].value).isEqualTo("5")
        assertThat(batchThree[2].value).isEqualTo("6")

        val batchFour = eventsByBatch[3]
        assertThat(batchFour.size).isEqualTo(2)
        assertThat(batchFour[0].value).isEqualTo("7")
        assertThat(batchFour[1].value).isEqualTo("8")
    }

    @Test
    fun `test events by batch, no records`() {
        val records = mutableListOf<CordaConsumerRecord<String, String>>()
        val eventsByBatch  = getEventsByBatch(records)
        assertThat(eventsByBatch.size).isEqualTo(0)
    }
}
