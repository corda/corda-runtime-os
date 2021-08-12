package net.corda.messaging.emulation.topic.model

import net.corda.messaging.api.records.Record
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class PartitionTest {
    private val partition = Partition(4, 10, "topic")

    @Test
    fun `addRecord will add record to the end`() {
        val record = Record("topic", 1, 2)
        partition.addRecord(record)

        assertThat(partition.getRecordsFrom(0L, 100))
            .contains(RecordMetadata(1L, record, 4))
    }

    @Test
    fun `addRecord will increase the offset`() {
        (1..4).map {
            Record("topic", it, it + 1)
        }.forEach {
            partition.addRecord(it)
        }

        assertThat(partition.getRecordsFrom(0L, 100).map { it.offset })
            .contains(1L, 2L, 3L, 4L)
    }

    @Test
    fun `addRecord will remove records from the end`() {
        (1..20).map {
            Record("topic", it, it + 1)
        }.forEach {
            partition.addRecord(it)
        }

        assertThat(partition.getRecordsFrom(0L, 100).map { it.offset.toInt() })
            .containsAll((11..20))
    }

    @Test
    fun `getRecordsFrom will return the correct list of records`() {
        (1..20).map {
            Record("topic", it, it + 1)
        }.forEach {
            partition.addRecord(it)
        }

        assertThat(partition.getRecordsFrom(13L, 4).map { it.offset.toInt() })
            .containsAll((14..17))
    }

    @Test
    fun `latestOffset will return the correct list of records`() {
        (10..40).map {
            Record("topic", it, it + 1)
        }.forEach {
            partition.addRecord(it)
        }

        assertThat(partition.latestOffset()).isEqualTo(31L)
    }
}
