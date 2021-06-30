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
import net.corda.v5.base.util.debug
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.sql.Connection
import java.sql.SQLException
import java.sql.Timestamp
import java.sql.Types
import java.time.Instant
import java.util.concurrent.locks.ReentrantLock
import javax.sql.DataSource
import javax.sql.rowset.serial.SerialBlob
import kotlin.concurrent.withLock

class DBAccessProviderImpl(private val jdbcUrl: String, private val username: String, private val password: String): DBAccessProvider {

    companion object {
        private val log: Logger = LoggerFactory.getLogger(this::class.java)
    }

    private lateinit var hikariDatasource: DataSource

    private var running = false
    private val startStopLock = ReentrantLock()

    private val commitOffsetStmt = "insert into ${DbSchema.CommittedOffsetsTable.TABLE_NAME} " +
                                  "(${DbSchema.CommittedOffsetsTable.TOPIC_COLUMN_NAME}, $CONSUMER_GROUP_COLUMN_NAME, " +
                                  "${DbSchema.CommittedOffsetsTable.PARTITION_COLUMN_NAME}, $COMMITTED_OFFSET_COLUMN_NAME)" +
                                  "values (?, ?, ?, ?)"

    private val maxCommittedOffsetStmt = "select max($COMMITTED_OFFSET_COLUMN_NAME) " +
                                     "from ${DbSchema.CommittedOffsetsTable.TABLE_NAME} " +
                                     "where ${DbSchema.CommittedOffsetsTable.TOPIC_COLUMN_NAME} = ? and " +
                                     "$CONSUMER_GROUP_COLUMN_NAME = ? and " +
                                     "${DbSchema.CommittedOffsetsTable.PARTITION_COLUMN_NAME} = ?"

    private val maxOffsetStatement = "select ${DbSchema.RecordsTable.TOPIC_COLUMN_NAME}, max($RECORD_OFFSET_COLUMN_NAME) " +
                "from ${DbSchema.RecordsTable.TABLE_NAME} where " +
                "$PARTITION_COLUMN_NAME = ${DbSchema.FIXED_PARTITION_NO} " +
                "group by ${DbSchema.RecordsTable.TOPIC_COLUMN_NAME}"

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

    private val selectRecordByPartitionOffsetStmt = "select $RECORD_KEY_COLUMN_NAME, $RECORD_VALUE_COLUMN_NAME " +
                                        "from ${DbSchema.RecordsTable.TABLE_NAME} where " +
                                        "${DbSchema.RecordsTable.TOPIC_COLUMN_NAME} = ? and " +
                                        "$PARTITION_COLUMN_NAME = ? and " +
                                        "$RECORD_OFFSET_COLUMN_NAME = ?"

    private val readTopicsStmt = "select ${DbSchema.TopicsTable.TOPIC_COLUMN_NAME} from ${DbSchema.TopicsTable.TABLE_NAME}"

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
                running = true
                log.debug { "Database access provider started configured with database: $jdbcUrl" }
            }
        }

    }

    override fun stop() {
        startStopLock.withLock {
            if (running) {
                running = false
                log.debug { "Database access provider stopped." }
            }
        }
    }


    override fun writeOffset(topic: String, consumerGroup: String, offset: Long) {
        executeWithErrorHandling({
            writeOffset(topic, consumerGroup, offset, it)
        }, "write offset $offset for consumer group $consumerGroup on topic $topic")
    }


    override fun getMaxCommittedOffset(topic: String, consumerGroup: String): Long? {
        var maxOffset: Long? = null

        executeWithErrorHandling({
            val stmt = it.prepareStatement(maxCommittedOffsetStmt)
            stmt.setString(1, topic)
            stmt.setString(2, consumerGroup)
            stmt.setInt(3, DbSchema.FIXED_PARTITION_NO)

            val result = stmt.executeQuery()
            if (result.next()) {
                maxOffset = result.getLong(1)
                if (result.wasNull()) {
                    maxOffset = null
                }
            }
        }, "retrieve max committed offset for consumer group $consumerGroup on topic $topic")

        return maxOffset
    }

    override fun getMaxOffsetPerTopic(): Map<String, Long?> {
        val maxOffsetsPerTopic = mutableMapOf<String, Long?>()

        executeWithErrorHandling({
            val topicsStmt = it.prepareStatement(readTopicsStmt)
            val topicsResult = topicsStmt.executeQuery()
            while (topicsResult.next()) {
                val topic = topicsResult.getString(1)
                maxOffsetsPerTopic[topic] = null
            }

            val stmt = it.prepareStatement(maxOffsetStatement)
            val result = stmt.executeQuery()
            while (result.next()) {
                val topic = result.getString(1)
                val maxOffset = result.getLong(2)
                maxOffsetsPerTopic[topic] = maxOffset
            }
        }, "retrieve max offsets per topic")

        return maxOffsetsPerTopic
    }


    override fun writeRecords(records: List<RecordDbEntry>, postTxFn: (records: List<RecordDbEntry>) -> Unit) {
        executeWithErrorHandling({
            writeRecords(records, it)
        }, "write records", { postTxFn(records) })
    }

    override fun readRecords(topic: String, startOffset: Long, maxOffset: Long, maxNumberOfRecords: Int): List<RecordDbEntry> {
        val records = mutableListOf<RecordDbEntry>()

        executeWithErrorHandling({
            val stmt = it.prepareStatement(readRecordsStmt)
            stmt.setString(1, topic)
            stmt.setInt(2, DbSchema.FIXED_PARTITION_NO)
            stmt.setLong(3, startOffset)
            stmt.setLong(4, maxOffset)
            stmt.setInt(5, maxNumberOfRecords)

            val result = stmt.executeQuery()
            while (result.next()) {
                val keyBlob = result.getBlob(RECORD_KEY_COLUMN_NAME)
                val valueBlob = result.getBlob(RECORD_VALUE_COLUMN_NAME)
                val record = RecordDbEntry(topic,
                                           result.getInt(PARTITION_COLUMN_NAME),
                                           result.getLong(RECORD_OFFSET_COLUMN_NAME),
                                           keyBlob.getBytes(0, keyBlob.length().toInt()),
                                           valueBlob?.getBytes(0, valueBlob.length().toInt()))
                records.add(record)
            }
        }, "retrieve (up to $maxNumberOfRecords) records from $topic starting from offset $startOffset up to offset $maxOffset")

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
                                       keyBlob.getBytes(0, keyBlob.length().toInt()),
                                       valueBlob?.getBytes(0, valueBlob.length().toInt()))
            }
        }, "retrieve record from topic $topic at location (partition: $partition, offset: $offset)")

        return record
    }

    override fun createTopic(topic: String) {
        executeWithErrorHandling({
            val stmt = it.prepareStatement(insertTopicStmt)
            stmt.setString(1, topic)
            // only single-partition topics supported currently.
            stmt.setInt(2, 1)

            stmt.execute()
        }, "create the topic $topic")
    }

    override fun writeOffsetAndRecordsAtomically(topic: String, consumerGroup: String,
                                        offset: Long, records: List<RecordDbEntry>,
                                        postTxFn: (records: List<RecordDbEntry>) -> Unit) {
        executeWithErrorHandling({
            writeOffset(topic, consumerGroup, offset, it)
            writeRecords(records, it)
        }, "write offset $offset for consumer group $consumerGroup on topic $topic and records atomically.", { postTxFn(records) })
    }

    private fun writeOffset(topic: String, consumerGroup: String, offset: Long, connection: Connection) {
        val stmt = connection.prepareStatement(commitOffsetStmt)
        stmt.setString(1, topic)
        stmt.setString(2, consumerGroup)
        stmt.setInt(3, DbSchema.FIXED_PARTITION_NO)
        stmt.setLong(4, offset)

        stmt.execute()
    }

    private fun writeRecords(records: List<RecordDbEntry>, connection: Connection) {
        records.forEach { record ->
            val stmt = connection.prepareStatement(insertRecordStatement)
            stmt.setString(1, record.topic)
            stmt.setInt(2, record.partition)
            stmt.setLong(3, record.offset)
            stmt.setBlob(4, SerialBlob(record.key))
            if (record.value != null) {
                stmt.setBlob(5, SerialBlob(record.value))
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

}
