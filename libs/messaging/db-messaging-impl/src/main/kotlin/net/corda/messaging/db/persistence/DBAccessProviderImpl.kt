package net.corda.messaging.db.persistence

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import net.corda.messaging.db.persistence.DbSchema.CommittedOffsetsTable.Companion.COMMITTED_OFFSET_COLUMN_NAME
import net.corda.messaging.db.persistence.DbSchema.CommittedOffsetsTable.Companion.CONSUMER_GROUP_COLUMN_NAME
import net.corda.messaging.db.persistence.DbSchema.RecordsTable.Companion.PARTITION_COLUMN_NAME
import net.corda.messaging.db.persistence.DbSchema.RecordsTable.Companion.RECORD_KEY_COLUMN_NAME
import net.corda.messaging.db.persistence.DbSchema.RecordsTable.Companion.RECORD_OFFSET_COLUMN_NAME
import net.corda.messaging.db.persistence.DbSchema.RecordsTable.Companion.RECORD_TIMESTAMP_COLUMN_NAME
import net.corda.messaging.db.persistence.DbSchema.RecordsTable.Companion.RECORD_VALUE_COLUMN_NAME
import net.corda.v5.base.concurrent.getOrThrow
import net.corda.v5.base.util.debug
import net.corda.v5.base.util.seconds
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.sql.Blob
import java.sql.Connection
import java.sql.SQLException
import java.sql.SQLIntegrityConstraintViolationException
import java.sql.Timestamp
import java.sql.Types
import java.time.Duration
import java.time.Instant
import java.util.concurrent.Callable
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.locks.ReentrantLock
import javax.sql.DataSource
import javax.sql.rowset.serial.SerialBlob
import kotlin.concurrent.withLock

/**
 * @property threadPoolSize the size of the thread pool size used to execute queries in parallel, when needed
 *                          (i.e. for requests that perform multiple queries to the database).
 */
@Suppress("TooManyFunctions", "LongParameterList")
class DBAccessProviderImpl(private val jdbcUrl: String,
                           private val username: String,
                           private val password: String,
                           private val dbType: DBType,
                           private val threadPoolSize: Int,
                           private val dbTimeout: Duration = 5.seconds): DBAccessProvider {

    companion object {
        private val log: Logger = LoggerFactory.getLogger(this::class.java)
    }

    private lateinit var hikariDatasource: DataSource

    private var running = false
    private val startStopLock = ReentrantLock()

    private lateinit var executor: ExecutorService

    private val commitOffsetStmt = "insert into ${DbSchema.CommittedOffsetsTable.TABLE_NAME} " +
            "(${DbSchema.CommittedOffsetsTable.TOPIC_COLUMN_NAME}, $CONSUMER_GROUP_COLUMN_NAME, " +
            "${DbSchema.CommittedOffsetsTable.PARTITION_COLUMN_NAME}, $COMMITTED_OFFSET_COLUMN_NAME)" +
            "values (?, ?, ?, ?)"

    private val maxCommittedOffsetsStmt =
            "select ${DbSchema.CommittedOffsetsTable.PARTITION_COLUMN_NAME}, max($COMMITTED_OFFSET_COLUMN_NAME) " +
            "from ${DbSchema.CommittedOffsetsTable.TABLE_NAME} " +
            "where ${DbSchema.CommittedOffsetsTable.TOPIC_COLUMN_NAME} = ? and " +
            "$CONSUMER_GROUP_COLUMN_NAME = ? and " +
            "${DbSchema.CommittedOffsetsTable.PARTITION_COLUMN_NAME} in [partitions_list] " +
            "group by ${DbSchema.CommittedOffsetsTable.PARTITION_COLUMN_NAME}"

    private val maxOffsetsStatement =
            "select ${DbSchema.RecordsTable.TOPIC_COLUMN_NAME}, ${PARTITION_COLUMN_NAME}, max($RECORD_OFFSET_COLUMN_NAME) " +
            "from ${DbSchema.RecordsTable.TABLE_NAME} " +
            "group by ${DbSchema.RecordsTable.TOPIC_COLUMN_NAME}, $PARTITION_COLUMN_NAME"

    private val insertRecordStatement = "insert into ${DbSchema.RecordsTable.TABLE_NAME} " +
            "(${DbSchema.RecordsTable.TOPIC_COLUMN_NAME}, $PARTITION_COLUMN_NAME, " +
            "$RECORD_OFFSET_COLUMN_NAME, $RECORD_KEY_COLUMN_NAME, " +
            "$RECORD_VALUE_COLUMN_NAME, $RECORD_TIMESTAMP_COLUMN_NAME) " +
            "values (?, ?, ?, ?, ?, ?)"

    private val readRecordsStmt = "select ${PARTITION_COLUMN_NAME}, $RECORD_OFFSET_COLUMN_NAME, " +
            "$RECORD_KEY_COLUMN_NAME, $RECORD_VALUE_COLUMN_NAME " +
            "from ${DbSchema.RecordsTable.TABLE_NAME} where " +
            "${DbSchema.RecordsTable.TOPIC_COLUMN_NAME} = ? and " +
            "$PARTITION_COLUMN_NAME = ? and " +
            "$RECORD_OFFSET_COLUMN_NAME >= ? and $RECORD_OFFSET_COLUMN_NAME <= ? " +
            "order by $RECORD_OFFSET_COLUMN_NAME asc " +
            "limit ?"

    private val readRecordsStmtForSQLServer = "select top (?) ${PARTITION_COLUMN_NAME}, $RECORD_OFFSET_COLUMN_NAME, " +
            "$RECORD_KEY_COLUMN_NAME, $RECORD_VALUE_COLUMN_NAME " +
            "from ${DbSchema.RecordsTable.TABLE_NAME} where " +
            "${DbSchema.RecordsTable.TOPIC_COLUMN_NAME} = ? and " +
            "$PARTITION_COLUMN_NAME = ? and " +
            "$RECORD_OFFSET_COLUMN_NAME >= ? and $RECORD_OFFSET_COLUMN_NAME <= ? " +
            "order by $RECORD_OFFSET_COLUMN_NAME asc"

    private val readRecordsStmtForOracle = "select * from (" +
            "select ${PARTITION_COLUMN_NAME}, $RECORD_OFFSET_COLUMN_NAME, " +
            "$RECORD_KEY_COLUMN_NAME, $RECORD_VALUE_COLUMN_NAME " +
            "from ${DbSchema.RecordsTable.TABLE_NAME} where " +
            "${DbSchema.RecordsTable.TOPIC_COLUMN_NAME} = ? and " +
            "$PARTITION_COLUMN_NAME = ? and " +
            "$RECORD_OFFSET_COLUMN_NAME >= ? and $RECORD_OFFSET_COLUMN_NAME <= ? " +
            "order by $RECORD_OFFSET_COLUMN_NAME asc) " +
            "where ROWNUM <= ?"

    private val selectRecordByPartitionOffsetStmt = "select $RECORD_KEY_COLUMN_NAME, $RECORD_VALUE_COLUMN_NAME " +
            "from ${DbSchema.RecordsTable.TABLE_NAME} where " +
            "${DbSchema.RecordsTable.TOPIC_COLUMN_NAME} = ? and " +
            "$PARTITION_COLUMN_NAME = ? and " +
            "$RECORD_OFFSET_COLUMN_NAME = ?"

    private val readTopicsStmt = "select ${DbSchema.TopicsTable.TOPIC_COLUMN_NAME}, ${DbSchema.TopicsTable.PARTITIONS_COLUMN_NAME}" +
            " from ${DbSchema.TopicsTable.TABLE_NAME}"

    private val insertTopicStmt = "insert into ${DbSchema.TopicsTable.TABLE_NAME} " +
            "(${DbSchema.TopicsTable.TOPIC_COLUMN_NAME}, ${DbSchema.TopicsTable.PARTITIONS_COLUMN_NAME}) " +
            "values (?, ?)"

    override val isRunning: Boolean
        get() = running

    override fun start() {
        startStopLock.withLock {
            if (!running) {
                val hikariConfig = HikariConfig()
                hikariConfig.jdbcUrl = jdbcUrl
                hikariConfig.username = username
                hikariConfig.password = password
                hikariConfig.isAutoCommit = false
                hikariDatasource = HikariDataSource(hikariConfig)
                executor = Executors.newFixedThreadPool(threadPoolSize)
                running = true
                log.debug { "Database access provider started configured with database: $jdbcUrl" }
            }
        }

    }

    override fun stop() {
        startStopLock.withLock {
            if (running) {
                executor.shutdown()
                running = false
                log.debug { "Database access provider stopped." }
            }
        }
    }


    override fun writeOffsets(topic: String, consumerGroup: String, offsetsPerPartition: Map<Int, Long>) {
        executeWithErrorHandling({
            writeOffsets(topic, consumerGroup, offsetsPerPartition, it)
        }, "write offset $offsetsPerPartition for consumer group $consumerGroup on topic $topic")
    }

    override fun getMaxCommittedOffset(topic: String, consumerGroup: String, partitions: Set<Int>): Map<Int, Long?> {
        if (partitions.isEmpty()) {
            return emptyMap()
        }

        val maxOffsets: MutableMap<Int, Long?> = partitions.map { it to null }.toMap().toMutableMap()
        executeWithErrorHandling({
            val partitionsList = MutableList(partitions.size) { "?" }.joinToString(", ", "(", ")")
            val sqlStatement = maxCommittedOffsetsStmt.replace("[partitions_list]", partitionsList)
            val stmt = it.prepareStatement(sqlStatement)
            stmt.setString(1, topic)
            stmt.setString(2, consumerGroup)
            partitions.forEachIndexed { index, partition -> stmt.setInt(3 + index, partition) }

            val result = stmt.executeQuery()
            while (result.next()) {
                val partition = result.getInt(1)
                val maxOffset = result.getLong(2)
                if (!result.wasNull()) {
                    maxOffsets[partition] = maxOffset
                }
            }
        }, "retrieve max committed offsets for consumer group $consumerGroup on topic $topic and partitions $partitions")

        return maxOffsets
    }

    override fun getMaxOffsetsPerTopic(): Map<String, Map<Int, Long?>> {
        val maxOffsetsPerTopic = mutableMapOf<String, MutableMap<Int, Long?>>()

        executeWithErrorHandling({
            val partitionsPerTopic = getTopics()
            partitionsPerTopic.forEach { (topic, partitions) ->
                maxOffsetsPerTopic[topic] = mutableMapOf()
                (1..partitions).forEach { partition -> maxOffsetsPerTopic[topic]!![partition] = null }
            }

            val stmt = it.prepareStatement(maxOffsetsStatement)
            val result = stmt.executeQuery()
            while (result.next()) {
                val topic = result.getString(1)
                val partition = result.getInt(2)
                val maxOffset = result.getLong(3)
                maxOffsetsPerTopic[topic]!![partition] = maxOffset
            }
        }, "retrieve max offsets per topic")

        return maxOffsetsPerTopic
    }

    override fun writeRecords(records: List<RecordDbEntry>, postTxFn: (records: List<RecordDbEntry>) -> Unit) {
        executeWithErrorHandling({
            writeRecords(records, it)
        }, "write records", { postTxFn(records) })
    }

    override fun readRecords(topic: String, fetchWindows: List<FetchWindow>): List<RecordDbEntry> {
        val futures = fetchWindows.map { window ->
            executor.submit( Callable { readRecords(topic, window.partition, window.startOffset, window.endOffset, window.limit) } )
        }

        return futures.flatMap { it.getOrThrow(dbTimeout) }
    }

    private fun readRecords(topic: String,
                            partition: Int,
                            startOffset: Long,
                            endOffset: Long, maxNumberOfRecords: Int): List<RecordDbEntry> {
        val records = mutableListOf<RecordDbEntry>()

        executeWithErrorHandling({
            val stmt = when (dbType) {
                DBType.SQL_SERVER -> {
                    val stmt = it.prepareStatement(readRecordsStmtForSQLServer)
                    stmt.setInt(1, maxNumberOfRecords)
                    stmt.setString(2, topic)
                    stmt.setInt(3, partition)
                    stmt.setLong(4, startOffset)
                    stmt.setLong(5, endOffset)

                    stmt
                }
                DBType.ORACLE -> {
                    val stmt = it.prepareStatement(readRecordsStmtForOracle)
                    stmt.setString(1, topic)
                    stmt.setInt(2, partition)
                    stmt.setLong(3, startOffset)
                    stmt.setLong(4, endOffset)
                    stmt.setInt(5, maxNumberOfRecords)

                    stmt
                }
                else -> {
                    val stmt = it.prepareStatement(readRecordsStmt)

                    stmt.setString(1, topic)
                    stmt.setInt(2, partition)
                    stmt.setLong(3, startOffset)
                    stmt.setLong(4, endOffset)
                    stmt.setInt(5, maxNumberOfRecords)

                    stmt
                }
            }

            val result = stmt.executeQuery()
            while (result.next()) {
                val keyBlob = result.getBlob(RECORD_KEY_COLUMN_NAME)
                val valueBlob = result.getBlob(RECORD_VALUE_COLUMN_NAME)
                val record = RecordDbEntry(topic,
                    result.getInt(PARTITION_COLUMN_NAME),
                    result.getLong(RECORD_OFFSET_COLUMN_NAME),
                    getBytes(keyBlob)!!,
                    getBytes(valueBlob))
                records.add(record)
            }
        }, "retrieve (up to $maxNumberOfRecords) records from (topic $topic, partition $partition) " +
            "starting from offset $startOffset up to offset $endOffset")

        return records
    }

    override fun getRecord(topic: String, partition: Int, offset: Long): RecordDbEntry? {
        var record: RecordDbEntry? = null

        executeWithErrorHandling({
            val stmt = it.prepareStatement(selectRecordByPartitionOffsetStmt)
            stmt.setString(1, topic)
            stmt.setInt(2, partition)
            stmt.setLong(3, offset)

            val result = stmt.executeQuery()
            if (result.next()) {
                val keyBlob = result.getBlob(1)
                val valueBlob = result.getBlob(2)
                record = RecordDbEntry(topic,
                    partition,
                    offset,
                    getBytes(keyBlob)!!,
                    getBytes(valueBlob))
            }
        }, "retrieve record from topic $topic at location (partition: $partition, offset: $offset)")

        return record
    }

    override fun createTopic(topic: String, partitions: Int) {
        executeWithErrorHandling({
            val stmt = it.prepareStatement(insertTopicStmt)
            stmt.setString(1, topic)
            stmt.setInt(2, partitions)

            stmt.execute()
        }, "create the topic $topic")
    }

    override fun getTopics(): Map<String, Int> {
        val partitionsPerTopic = mutableMapOf<String, Int>()

        executeWithErrorHandling({
            val topicsStmt = it.prepareStatement(readTopicsStmt)
            val topicsResult = topicsStmt.executeQuery()
            while (topicsResult.next()) {
                val topic = topicsResult.getString(1)
                val partitions = topicsResult.getInt(2)
                partitionsPerTopic[topic] = partitions
            }
        }, "retrieve all the topics")

        return partitionsPerTopic
    }

    override fun writeOffsetsAndRecordsAtomically(topic: String, consumerGroup: String,
                                                  offsetsPerPartition: Map<Int, Long>,
                                                  records: List<RecordDbEntry>,
                                                  postTxFn: (records: List<RecordDbEntry>) -> Unit) {
        executeWithErrorHandling({
            writeOffsets(topic, consumerGroup, offsetsPerPartition, it)
            writeRecords(records, it)
        }, "write offset $offsetsPerPartition for consumer group $consumerGroup on topic $topic and records atomically.",
            { postTxFn(records) })
    }

    private fun writeOffsets(topic: String, consumerGroup: String, offsetsPerPartition: Map<Int, Long>, connection: Connection) {
        offsetsPerPartition.forEach { (partition, offset) ->
            val stmt = connection.prepareStatement(commitOffsetStmt)
            stmt.setString(1, topic)
            stmt.setString(2, consumerGroup)
            stmt.setInt(3, partition)
            stmt.setLong(4, offset)

            try {
                stmt.execute()
            } catch (e: SQLException) {
                if (isPrimaryKeyViolation(e)) {
                    log.warn("Attempted to write offset that has already been committed", e)
                    throw OffsetsAlreadyCommittedException()
                }

                throw e
            }
        }
    }

    private fun writeRecords(records: List<RecordDbEntry>, connection: Connection) {
        records.forEach { record ->
            val stmt = connection.prepareStatement(insertRecordStatement)
            stmt.setString(1, record.topic)
            stmt.setInt(2, record.partition)
            stmt.setLong(3, record.offset)
            stmt.setBlob(4, toBlob(record.key, connection))
            if (record.value != null) {
                stmt.setBlob(5, toBlob(record.value, connection))
            } else {
                stmt.setNull(5, Types.BLOB)
            }
            stmt.setTimestamp(6, Timestamp.from(Instant.now()))

            stmt.execute()
        }
    }

    /**
     * Executes the specified operation with the necessary error handling.
     * If an SQL error arises during execution, the transaction is rolled back and the exception is re-thrown.
     *
     * The provided callback function [postTxFn] will be invoked in the end regardless of whether the transaction was successful or not.
     */
    private fun executeWithErrorHandling(operation: (connection: Connection) -> Unit, operationName: String, postTxFn: () -> Unit = {}) {
        hikariDatasource.connection.use {
            try {
                operation(it)

                it.commit()
            } catch (e: SQLException) {
                log.error("Error while trying to $operationName. Transaction will be rolled back.", e)
                try {
                    it.rollback()
                } catch (e: SQLException) {
                    log.error("Error while trying to roll back a transaction.", e)
                    throw e
                }
                throw e
            } finally {
                postTxFn()
            }
        }
    }

    private fun getBytes(blob: Blob?): ByteArray? {
        return blob?.getBytes(1, blob.length().toInt())
    }

    private fun toBlob(bytes: ByteArray, connection: Connection): Blob {
        return when(dbType) {
            DBType.ORACLE -> {
                val blob = connection.createBlob()
                blob.setBytes(1, bytes)
                blob
            }
            else -> SerialBlob(bytes)
        }
    }

    private fun isPrimaryKeyViolation(e: SQLException): Boolean {
        return e is SQLIntegrityConstraintViolationException ||
                (e.message != null &&
                        e.message!!.contains("duplicate key value violates unique constraint") || // postgres
                        e.message!!.contains("Violation of PRIMARY KEY constraint") // SQL Server
                )
    }

}

enum class DBType {
    H2,
    POSTGRESQL,
    ORACLE,
    SQL_SERVER
}