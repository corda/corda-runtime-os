package net.corda.messaging.db.persistence

import net.corda.messaging.db.util.DbUtils
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.sql.DriverManager

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class DbAccessProviderTestBase {

    private val topic1 = "test.topic1"
    private val topic2 = "test.topic2"

    private val consumer1 = "consumer-group-1"
    private val consumer2 = "consumer-group-2"

    private lateinit var dbAccessProvider: DBAccessProvider

    abstract fun startDatabase()

    abstract fun stopDatabase()

    abstract fun createTables()

    abstract fun getDbType(): DBType

    abstract fun getJdbcUrl(): String

    abstract fun getUsername(): String

    abstract fun getPassword(): String

    @BeforeAll
    fun setupBeforeAllTests() {
        startDatabase()
        createTables()

        dbAccessProvider = DBAccessProviderImpl(getJdbcUrl(), getUsername(), getPassword(), getDbType())
        dbAccessProvider.start()

        dbAccessProvider.createTopic(topic1)
        dbAccessProvider.createTopic(topic2)
    }

    @AfterAll
    fun cleanupAfterAllTests() {
        dbAccessProvider.stop()
        stopDatabase()
    }

    @AfterEach
    fun cleanupAfterEachTest() {
        val connection = DriverManager.getConnection(getJdbcUrl(), getUsername(), getPassword())
        connection.prepareStatement(DbUtils.cleanupTopicRecordsTableStmt).execute()
        connection.prepareStatement(DbUtils.cleanupOffsetsTableStmt).execute()
        connection.prepareStatement(DbUtils.cleanupTopicsTableStmt).execute()
    }

    @Test
    fun `max offsets per topic are calculated properly`() {
        val records = listOf(
            RecordDbEntry(topic1, 1, 1, "key-1".toByteArray(), "value-1".toByteArray()),
            RecordDbEntry(topic1, 1, 2, "key-2".toByteArray(), "value-2".toByteArray()),
            RecordDbEntry(topic2, 1, 7, "key-3".toByteArray(), "value-3".toByteArray()),
            RecordDbEntry(topic2, 1, 8, "key-4".toByteArray(), "value-4".toByteArray()),
            RecordDbEntry(topic2, 1, 9, "key-5".toByteArray(), "value-4".toByteArray())
        )
        dbAccessProvider.writeRecords(records) {}

        val maxOffsetsPerTopic = dbAccessProvider.getMaxOffsetPerTopic()

        assertThat(maxOffsetsPerTopic).containsExactlyEntriesOf(mapOf(
            topic1 to 2,
            topic2 to 9
        ))
    }

    @Test
    fun `offsets are committed successfully and maximum offsets are calculated properly`() {
        dbAccessProvider.writeOffset(topic1, consumer1, 3)
        dbAccessProvider.writeOffset(topic1, consumer1, 7)
        dbAccessProvider.writeOffset(topic1, consumer2, 2)
        dbAccessProvider.writeOffset(topic1, consumer2, 5)
        dbAccessProvider.writeOffset(topic2, consumer1, 10)
        dbAccessProvider.writeOffset(topic2, consumer1, 15)
        dbAccessProvider.writeOffset(topic2, consumer2, 20)
        dbAccessProvider.writeOffset(topic2, consumer2, 27)

        assertThat(dbAccessProvider.getMaxCommittedOffset(topic1, consumer1)).isEqualTo(7)
        assertThat(dbAccessProvider.getMaxCommittedOffset(topic1, consumer2)).isEqualTo(5)
        assertThat(dbAccessProvider.getMaxCommittedOffset(topic2, consumer1)).isEqualTo(15)
        assertThat(dbAccessProvider.getMaxCommittedOffset(topic2, consumer2)).isEqualTo(27)
    }

    @Test
    fun `when there are no committed offsets, null is returned as the maximum committed offset for that topic`() {
        assertThat(dbAccessProvider.getMaxCommittedOffset(topic1, consumer1)).isNull()
    }

    @Test
    fun `when there are no records for a topic, null is returned as the maximum offset for that topic`() {
        val results = dbAccessProvider.getMaxOffsetPerTopic()

        assertThat(results[topic1]).isNull()
        assertThat(results[topic2]).isNull()
    }

    @Test
    fun `records can be written and read successfully`() {
        var called = false
        val records = listOf(
            RecordDbEntry(topic1, 1, 1, "key-1".toByteArray(), "value-1".toByteArray()),
            RecordDbEntry(topic1, 1, 2, "key-2".toByteArray(), "value-2".toByteArray()),
            RecordDbEntry(topic2, 1, 7, "key-3".toByteArray(), "value-3".toByteArray()),
            RecordDbEntry(topic2, 1, 8, "key-4".toByteArray(), "value-4".toByteArray()),
            RecordDbEntry(topic2, 1, 9, "key-5".toByteArray(), "value-5".toByteArray())
        )
        dbAccessProvider.writeRecords(records) { called = true }

        val topic1Records = dbAccessProvider.readRecords(topic1, 0, 10, 10)
        val topic2Records = dbAccessProvider.readRecords(topic2, 0, 10, 10)

        assertThat(topic1Records).containsExactlyElementsOf(records.filter { it.topic == topic1 })
        assertThat(topic2Records).containsExactlyElementsOf(records.filter { it.topic == topic2 })
        assertThat(called).isTrue
    }

    @Test
    fun `records after the max offset are not returned`() {
        val records = listOf(
            RecordDbEntry(topic1, 1, 1, "key-1".toByteArray(), "value-1".toByteArray()),
            RecordDbEntry(topic1, 1, 2, "key-2".toByteArray(), "value-2".toByteArray()),
            RecordDbEntry(topic1, 1, 3, "key-3".toByteArray(), "value-3".toByteArray()),
            RecordDbEntry(topic1, 1, 4, "key-4".toByteArray(), "value-4".toByteArray()),
            RecordDbEntry(topic1, 1, 5, "key-5".toByteArray(), "value-5".toByteArray()),
        )
        dbAccessProvider.writeRecords(records) {}

        val returnedRecords = dbAccessProvider.readRecords(topic1, 0, 3, 10)

        assertThat(returnedRecords).containsExactlyElementsOf(records.subList(0, 3))
    }

    @Test
    fun `returned records do not exceed the specified max limit`() {
        val records = listOf(
            RecordDbEntry(topic1, 1, 1, "key-1".toByteArray(), "value-1".toByteArray()),
            RecordDbEntry(topic1, 1, 2, "key-2".toByteArray(), "value-2".toByteArray()),
            RecordDbEntry(topic1, 1, 3, "key-3".toByteArray(), "value-3".toByteArray()),
            RecordDbEntry(topic1, 1, 4, "key-4".toByteArray(), "value-4".toByteArray()),
            RecordDbEntry(topic1, 1, 5, "key-5".toByteArray(), "value-5".toByteArray()),
        )
        dbAccessProvider.writeRecords(records) {}

        val returnedRecords = dbAccessProvider.readRecords(topic1, 0, 5, 2)

        assertThat(returnedRecords).hasSizeLessThanOrEqualTo(2)
    }

    @Test
    fun `can write offset and records atomically`() {
        val committedOffset = 3L
        val records = listOf(
            RecordDbEntry(topic1, 1, 4, "key-1".toByteArray(), "value-1".toByteArray()),
            RecordDbEntry(topic1, 1, 5, "key-2".toByteArray(), "value-2".toByteArray()),
            RecordDbEntry(topic1, 1, 6, "key-3".toByteArray(), "value-3".toByteArray())
        )
        dbAccessProvider.writeOffsetAndRecordsAtomically(topic1, consumer1, committedOffset, records) {}

        val returnedRecords = dbAccessProvider.readRecords(topic1, 0, 10, 10)
        assertThat(returnedRecords).containsExactlyElementsOf(records)

        val returnedCommittedOffset = dbAccessProvider.getMaxCommittedOffset(topic1, consumer1)
        assertThat(returnedCommittedOffset).isEqualTo(committedOffset)
    }

    @Test
    fun `can retrieve record at specific location`() {
        val committedOffset = 3L
        val records = listOf(
            RecordDbEntry(topic1, 1, 1, "key-1".toByteArray(), "value-1".toByteArray()),
            RecordDbEntry(topic1, 1, 2, "key-2".toByteArray(), "value-2".toByteArray()),
            RecordDbEntry(topic1, 1, 3, "key-3".toByteArray(), "value-3".toByteArray())
        )
        dbAccessProvider.writeOffsetAndRecordsAtomically(topic1, consumer1, committedOffset, records) {}

        val existingRecord = dbAccessProvider.getRecord(topic1, 1, 2)
        assertThat(existingRecord).isEqualTo(records[1])

        val nonExistingRecord = dbAccessProvider.getRecord(topic1, 1, 10)
        assertThat(nonExistingRecord).isNull()
    }

}