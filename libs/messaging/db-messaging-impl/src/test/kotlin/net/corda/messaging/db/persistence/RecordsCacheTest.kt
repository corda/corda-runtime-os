package net.corda.messaging.db.persistence

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.lang.IllegalArgumentException

class RecordsCacheTest {

    private val topic = "test.topic"
    private val topicPartitions = mapOf(
        topic to 5
    )
    private val cacheEntriesPerPartition = 10

    @Test
    fun `records are returned when there are no gaps`() {
        val recordsCache = RecordsCache(topicPartitions, cacheEntriesPerPartition)
        val records = (1L..5L).map {  index ->
            index to RecordDbEntry(topic, 1, index, "key-$index".toByteArray(), "value-$index".toByteArray())
        }.toMap()
        recordsCache.getAllEntries(topic, 1).putAll(records)

        assertThat(recordsCache.readRecords(topic, 1, 1, 5, 5))
            .isEqualTo(records.values.toList())
        assertThat(recordsCache.readRecords(topic, 1, 1, 5, 3))
            .isEqualTo(records.filterValues { it.offset in 1..3 }.values.toList())
        assertThat(recordsCache.readRecords(topic, 1, 3, 5, 5))
            .isEqualTo(records.filterValues { it.offset in 3..5 }.values.toList())
    }

    @Test
    fun `when there is a gap in the beginning, no records are returned`() {
        val recordsCache = RecordsCache(topicPartitions, cacheEntriesPerPartition)
        val records = (10L..15L).map {  index ->
            index to RecordDbEntry(topic, 1, index, "key-$index".toByteArray(), "value-$index".toByteArray())
        }.toMap()
        recordsCache.getAllEntries(topic, 1).putAll(records)

        assertThat(recordsCache.readRecords(topic, 1, 5, 15, 5)).isEmpty()
    }

    @Test
    fun `when max size threshold is crossed, old records are cleaned up`() {
        val recordsCache = RecordsCache(topicPartitions, cacheEntriesPerPartition)
        val records = (1L..20L).map {  index ->
            index to RecordDbEntry(topic, 1, index, "key-$index".toByteArray(), "value-$index".toByteArray())
        }.toMap()

        recordsCache.addRecords(records.values.toList())

        val entries = recordsCache.getAllEntries(topic, 1)
        assertThat(entries).hasSize(cacheEntriesPerPartition)
        assertThat(recordsCache.getAllEntries(topic, 1)).isEqualTo(records.filterValues { it.offset in 11..20 })
    }

    @Test
    fun `added records are returned`() {
        val recordsCache = RecordsCache(topicPartitions, cacheEntriesPerPartition)
        val records = (1L..5L).map {  index ->
            index to RecordDbEntry(topic, 1, index, "key-$index".toByteArray(), "value-$index".toByteArray())
        }.toMap()

        recordsCache.addRecords(records.values.toList())

        assertThat(recordsCache.readRecords(topic, 1, 1, 5, 5)).isEqualTo(records.values.toList())
    }

    @Test
    fun `added records are returned for a topic that is added on an initialised cache`() {
        val recordsCache = RecordsCache(emptyMap(), cacheEntriesPerPartition)
        recordsCache.addTopic(topic, 5)
        val records = (1L..5L).map {  index ->
            index to RecordDbEntry(topic, 1, index, "key-$index".toByteArray(), "value-$index".toByteArray())
        }.toMap()

        recordsCache.addRecords(records.values.toList())

        assertThat(recordsCache.readRecords(topic, 1, 1, 5, 5)).isEqualTo(records.values.toList())
    }

    @Test
    fun `if an existing topic is added, an exception is thrown`() {
        val recordsCache = RecordsCache(topicPartitions, cacheEntriesPerPartition)
        assertThatThrownBy { recordsCache.addTopic(topic, 5) }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

}