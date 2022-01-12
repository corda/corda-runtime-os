package net.corda.messaging.db.persistence

import net.corda.messaging.db.util.DbUtils
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.Assume
import org.junit.BeforeClass
import org.junit.jupiter.api.*
import org.mockito.kotlin.isNotNull
import java.sql.DriverManager
import java.time.Instant


@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class DbAccessProviderTestBase {

    private val topic1 = "test.topic1"
    private val topic2 = "test.topic2"

    private val partitions = 2

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

    abstract fun hasDbConfigured(): Boolean

    @BeforeAll
    fun setupBeforeAllTests() {
        Assume.assumeTrue(hasDbConfigured())
        startDatabase()
        createTables()
        dbAccessProvider = DBAccessProviderImpl(getJdbcUrl(), getUsername(), getPassword(), getDbType(), 5)
        dbAccessProvider.start()
    }

    @AfterAll
    fun cleanupAfterAllTests() {
        if(hasDbConfigured()) {
            dbAccessProvider.stop()
            stopDatabase()
        }
    }

    @BeforeEach
    fun setupBeforeEachTest() {
        dbAccessProvider.createTopic(topic1, partitions)
        dbAccessProvider.createTopic(topic2, partitions)
    }

    @AfterEach
    fun cleanupAfterEachTest() {
        val connection = DriverManager.getConnection(getJdbcUrl(), getUsername(), getPassword())
        connection.prepareStatement(DbUtils.cleanupTopicRecordsTableStmt).execute()
        connection.prepareStatement(DbUtils.cleanupOffsetsTableStmt).execute()
        connection.prepareStatement(DbUtils.cleanupTopicsTableStmt).execute()
    }

    @Test
    fun `getTopics returns topics with their number of partitions successfully`() {
        val topicsWithPartitions = dbAccessProvider.getTopics()

        assertThat(topicsWithPartitions).containsExactlyEntriesOf(mapOf(
            topic1 to partitions,
            topic2 to partitions
        ))
    }

    @Test
    fun `max offsets per topic are calculated properly`() {
        val records = listOf(
            RecordDbEntry(topic1, 1, 1, "key-1".toByteArray(), "value-1".toByteArray()),
            RecordDbEntry(topic1, 1, 2, "key-2".toByteArray(), "value-2".toByteArray()),
            RecordDbEntry(topic2, 2, 7, "key-3".toByteArray(), "value-3".toByteArray()),
            RecordDbEntry(topic2, 2, 8, "key-4".toByteArray(), "value-4".toByteArray()),
            RecordDbEntry(topic2, 2, 9, "key-5".toByteArray(), "value-4".toByteArray())
        )
        dbAccessProvider.writeRecords(records) { _, _ -> }

        val maxOffsetsPerTopic = dbAccessProvider.getMaxOffsetsPerTopic()

        assertThat(maxOffsetsPerTopic[topic1]).containsExactlyEntriesOf(mapOf(
            1 to 2,
            2 to null
        ))
        assertThat(maxOffsetsPerTopic[topic2]).containsExactlyEntriesOf(mapOf(
            1 to null,
            2 to 9
        ))
    }

    @Test
    fun `offsets are committed successfully and maximum committed offsets are calculated properly`() {
        dbAccessProvider.writeOffsets(topic1, consumer1, mapOf(1 to 3))
        dbAccessProvider.writeOffsets(topic1, consumer1, mapOf(1 to 7))
        dbAccessProvider.writeOffsets(topic1, consumer2, mapOf(1 to 2))
        dbAccessProvider.writeOffsets(topic1, consumer2, mapOf(1 to 5))
        dbAccessProvider.writeOffsets(topic2, consumer1, mapOf(1 to 10))
        dbAccessProvider.writeOffsets(topic2, consumer1, mapOf(1 to 15))
        dbAccessProvider.writeOffsets(topic2, consumer2, mapOf(1 to 20))
        dbAccessProvider.writeOffsets(topic2, consumer2, mapOf(1 to 27))

        assertThat(dbAccessProvider.getMaxCommittedOffset(topic1, consumer1, setOf(1))).containsExactlyEntriesOf(mapOf(
            1 to 7
        ))
        assertThat(dbAccessProvider.getMaxCommittedOffset(topic1, consumer2, setOf(1))).containsExactlyEntriesOf(mapOf(
            1 to 5
        ))
        assertThat(dbAccessProvider.getMaxCommittedOffset(topic2, consumer1, setOf(1))).containsExactlyEntriesOf(mapOf(
            1 to 15
        ))
        assertThat(dbAccessProvider.getMaxCommittedOffset(topic2, consumer2, setOf(1))).containsExactlyEntriesOf(mapOf(
            1 to 27
        ))
    }

    @Test
    fun `when there are no committed offsets, null is returned as the maximum committed offset for that topic`() {
        assertThat(dbAccessProvider.getMaxCommittedOffset(topic1, consumer1, setOf(1))).containsExactlyEntriesOf(mapOf(
            1 to null
        ))
    }

    @Test
    fun `when set of partitions is empty on query for committed offsets, an empty map is returned`() {
        assertThat(dbAccessProvider.getMaxCommittedOffset(topic1, consumer1, emptySet())).isEmpty()
    }

    @Test
    fun `when you try to commit offset that has already been committed, an exception is thrown`() {
        dbAccessProvider.writeOffsets(topic1, consumer1, mapOf(1 to 3))
        val records = listOf(
            RecordDbEntry(topic1, 1, 1, "key-1".toByteArray(), "value-1".toByteArray()),
            RecordDbEntry(topic1, 1, 2, "key-2".toByteArray(), "value-2".toByteArray()),
        )

        assertThatThrownBy { dbAccessProvider.writeOffsets(topic1, consumer1, mapOf(1 to 3)) }
            .isInstanceOf(OffsetsAlreadyCommittedException::class.java)
        assertThatThrownBy { dbAccessProvider.writeOffsetsAndRecordsAtomically(topic1, consumer1, mapOf(1 to 3), records) { _, _ -> } }
            .isInstanceOf(OffsetsAlreadyCommittedException::class.java)
    }

    @Test
    fun `when there are no records for a topic, null is returned as the maximum offset for that topic`() {
        val results = dbAccessProvider.getMaxOffsetsPerTopic()

        assertThat(results[topic1]).containsExactlyEntriesOf(mapOf(
            1 to null,
            2 to null
        ))
        assertThat(results[topic2]).containsExactlyEntriesOf(mapOf(
            1 to null,
            2 to null
        ))
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
        dbAccessProvider.writeRecords(records) { _, _ -> called = true }

        val topic1Records = dbAccessProvider.readRecords(topic1, listOf(FetchWindow(1, 0, 10, 10)))
        val topic2Records = dbAccessProvider.readRecords(topic2, listOf(FetchWindow(1, 0, 10, 10)))

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
        dbAccessProvider.writeRecords(records) { _, _ -> }

        val returnedRecords = dbAccessProvider.readRecords(topic1, listOf(FetchWindow(1, 0, 3, 10)))

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
        dbAccessProvider.writeRecords(records) { _, _ -> }

        val returnedRecords = dbAccessProvider.readRecords(topic1, listOf(FetchWindow(1, 0, 5, 2)))

        assertThat(returnedRecords).hasSizeLessThanOrEqualTo(2)
    }

    @Test
    fun `read records returns records from multiple partitions successfully`() {
        var called = false
        val records = listOf(
            RecordDbEntry(topic1, 1, 1, "key-1".toByteArray(), "value-1".toByteArray()),
            RecordDbEntry(topic1, 1, 2, "key-2".toByteArray(), "value-2".toByteArray()),
            RecordDbEntry(topic1, 2, 1, "key-3".toByteArray(), "value-3".toByteArray()),
            RecordDbEntry(topic1, 2, 2, "key-4".toByteArray(), "value-4".toByteArray()),
            RecordDbEntry(topic1, 2, 3, "key-5".toByteArray(), "value-5".toByteArray())
        )
        dbAccessProvider.writeRecords(records) { _, _ -> called = true }

        val fetchWindows = listOf(
            FetchWindow(1, 0, 5, 10),
            FetchWindow(2, 0, 5, 10)
        )
        val topicRecords = dbAccessProvider.readRecords(topic1, fetchWindows)

        assertThat(topicRecords).containsExactlyElementsOf(records)
        assertThat(called).isTrue
    }

    @Test
    fun `can write offset and records atomically`() {
        val committedOffset = 3L
        val records = listOf(
            RecordDbEntry(topic1, 1, 4, "key-1".toByteArray(), "value-1".toByteArray()),
            RecordDbEntry(topic1, 1, 5, "key-2".toByteArray(), "value-2".toByteArray()),
            RecordDbEntry(topic1, 1, 6, "key-3".toByteArray(), "value-3".toByteArray())
        )
        dbAccessProvider.writeOffsetsAndRecordsAtomically(topic1, consumer1, mapOf(1 to committedOffset), records) { _, _ -> }

        val returnedRecords = dbAccessProvider.readRecords(topic1, listOf(FetchWindow(1, 0, 10, 10)))
        assertThat(returnedRecords).containsExactlyElementsOf(records)

        val returnedCommittedOffset = dbAccessProvider.getMaxCommittedOffset(topic1, consumer1, setOf(1))
        assertThat(returnedCommittedOffset).containsExactlyEntriesOf(mapOf(
            1 to 3
        ))
    }

    @Test
    fun `can retrieve record at specific location`() {
        val committedOffset = 3L
        val records = listOf(
            RecordDbEntry(topic1, 1, 1, "key-1".toByteArray(), "value-1".toByteArray()),
            RecordDbEntry(topic1, 1, 2, "key-2".toByteArray(), "value-2".toByteArray()),
            RecordDbEntry(topic1, 1, 3, "key-3".toByteArray(), "value-3".toByteArray())
        )
        dbAccessProvider.writeOffsetsAndRecordsAtomically(topic1, consumer1, mapOf(1 to committedOffset), records) { _, _ -> }

        val existingRecord = dbAccessProvider.getRecord(topic1, 1, 2)
        assertThat(existingRecord).isEqualTo(records[1])

        val nonExistingRecord = dbAccessProvider.getRecord(topic1, 1, 10)
        assertThat(nonExistingRecord).isNull()
    }

    @Test
    fun `can delete records before timestamp successfully`() {
        val recordsBeforeWindow = listOf(
            RecordDbEntry(topic1, 1, 1, "key-1".toByteArray(), "value-1".toByteArray()),
            RecordDbEntry(topic1, 1, 2, "key-2".toByteArray(), "value-2".toByteArray()),
            RecordDbEntry(topic1, 1, 3, "key-3".toByteArray(), "value-3".toByteArray())
        )
        val recordsAfterWindow = listOf(
            RecordDbEntry(topic1, 1, 4, "key-4".toByteArray(), "value-4".toByteArray()),
            RecordDbEntry(topic1, 1, 5, "key-5".toByteArray(), "value-5".toByteArray())
        )

        dbAccessProvider.writeRecords(recordsBeforeWindow) { _, _ -> }
        Thread.sleep(100)
        val cutoffWindow = Instant.now()
        Thread.sleep(100)
        dbAccessProvider.writeRecords(recordsAfterWindow) { _, _ -> }

        dbAccessProvider.deleteRecordsOlderThan(topic1, cutoffWindow)

        val readRecordsAfterCleanup = dbAccessProvider.readRecords(topic1, listOf(FetchWindow(1, 1, 5, 10)))
        assertThat(readRecordsAfterCleanup).doesNotContainAnyElementsOf(recordsBeforeWindow)
        assertThat(readRecordsAfterCleanup).containsAll(recordsAfterWindow)
    }

    @Test
    fun `can delete offsets before timestamp successfully`() {
        val offsetsBeforeWindow = mapOf(
            1 to 3L
        )
        val offsetsAfterWindow = mapOf(
            2 to 11L
        )

        dbAccessProvider.writeOffsets(topic1, consumer1, offsetsBeforeWindow)
        Thread.sleep(100)
        val cutoffWindow = Instant.now()
        Thread.sleep(100)
        dbAccessProvider.writeOffsets(topic1, consumer1, offsetsAfterWindow)

        dbAccessProvider.deleteOffsetsOlderThan(topic1, cutoffWindow)
        assertThat(dbAccessProvider.getMaxCommittedOffset(topic1, consumer1, setOf(1, 2))).isEqualTo(mapOf(
            1 to null,
            2 to 11L
        ))
    }
}