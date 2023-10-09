package net.corda.messaging.emulation.topic.model

import net.corda.messaging.api.records.Record
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.concurrent.write

class PartitionTest {
    private val partition = Partition(4, 10, "topic")

    @Test
    fun `addRecord will add record to the end`() {
        val record = Record("topic", 1, 2)
        partition.lock.write {
            partition.addRecord(record)
        }

        assertThat(partition.getRecordsFrom(0L, 100))
            .contains(RecordMetadata(0L, record, 4))
    }

    @Test
    fun `addRecord will increase the offset`() {
        partition.lock.write {
            for (i in 1..4) {
                partition.addRecord(Record("topic", i, i + 1))
            }
        }

        assertThat(partition.getRecordsFrom(0L, 100).map { it.offset })
            .contains(0L, 1L, 2L, 3L)
    }

    @Test
    fun `addRecord will remove records from the end`() {
        partition.lock.write {
            for (i in 1..20) {
                partition.addRecord(Record("topic", i, i + 1))
            }
        }

        assertThat(partition.getRecordsFrom(0L, 100).map { it.offset.toInt() })
            .containsAll((10..19))
    }

    @Test
    fun `addRecord will throw an exception if partition is not locked`() {
        assertThrows<ConcurrentModificationException> {
            for (i in 1..3) {
                partition.addRecord(Record("topic", i, i + 1))
            }
        }
    }

    @Test
    fun `getRecordsFrom will return the correct list of records`() {
        partition.lock.write {
            for (i in 1..20) {
                partition.addRecord(Record("topic", i, i + 1))
            }
        }

        assertThat(partition.getRecordsFrom(13L, 4).map { it.offset.toInt() })
            .containsAll((13..16))
    }

    @Test
    fun `latestOffset will return the correct list of records`() {
        partition.lock.write {
            for (i in 10..40) {
                partition.addRecord(Record("topic", i, i + 1))
            }
        }

        assertThat(partition.latestOffset()).isEqualTo(31L)
    }
}
