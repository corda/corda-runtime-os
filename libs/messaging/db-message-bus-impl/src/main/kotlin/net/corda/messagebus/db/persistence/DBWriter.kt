package net.corda.messagebus.db.persistence

import com.typesafe.config.Config
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import net.corda.messagebus.db.persistence.DbSchema.CommittedOffsetsTable.Companion.commitOffsetStmt
import net.corda.messagebus.db.persistence.DbSchema.CommittedOffsetsTable.Companion.deleteOffsetsStmt
import net.corda.messagebus.db.persistence.DbSchema.CommittedOffsetsTable.Companion.maxCommittedOffsetsStmt
import net.corda.messagebus.db.persistence.DbSchema.RecordsTable.Companion.deleteRecordsStmt
import net.corda.messagebus.db.persistence.DbSchema.RecordsTable.Companion.insertRecordStatement
import net.corda.messagebus.db.persistence.DbSchema.RecordsTable.Companion.maxOffsetsStatement
import net.corda.messagebus.db.persistence.DbSchema.RecordsTable.Companion.updateRecordVisibility
import net.corda.messagebus.db.persistence.DbSchema.TopicsTable.Companion.insertTopicStmt
import net.corda.messagebus.db.persistence.DbSchema.TopicsTable.Companion.readTopicsStmt
import net.corda.v5.base.annotations.VisibleForTesting
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
import javax.sql.DataSource
import javax.sql.rowset.serial.SerialBlob

/**
 * @property threadPoolSize the size of the thread pool size used to execute queries in parallel, when needed
 *                          (i.e. for requests that perform multiple queries to the database).
 * @property maxConnectionPoolSize the max size of the database connection pool.
 */
@Suppress("TooManyFunctions", "LongParameterList")
class DBWriter(
    private val config: Config,
    private val threadPoolSize: Int,
    private val dbTimeout: Duration = 5.seconds,
    private val maxConnectionPoolSize: Int = 10
) {

    companion object {
        private val log: Logger = LoggerFactory.getLogger(this::class.java)
    }

    private val hikariDatasource: DataSource = setupDB(config)

    fun beginTransaction(): Connection {
        return hikariDatasource.connection
    }

    fun writeOffsets(topic: String, consumerGroup: String, offsetsPerPartition: Map<Int, Long>) {
        executeWithErrorHandling("write offset $offsetsPerPartition for consumer group $consumerGroup on topic $topic") {
            writeOffsets(topic, consumerGroup, offsetsPerPartition, it)
        }
    }

    fun getMaxCommittedOffset(topic: String, consumerGroup: String, partitions: Set<Int>): Map<Int, Long?> {
        if (partitions.isEmpty()) {
            return emptyMap()
        }

        val maxOffsets: MutableMap<Int, Long?> = partitions.map { it to null }.toMap().toMutableMap()
        executeWithErrorHandling("retrieve max committed offsets for consumer group $consumerGroup on topic $topic and partitions $partitions") {
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
        }

        return maxOffsets
    }

    fun getMaxOffsetsPerTopic(): Map<String, Map<Int, Long?>> {
        val maxOffsetsPerTopic = mutableMapOf<String, MutableMap<Int, Long?>>()

        executeWithErrorHandling("retrieve max offsets per topic") {
            val partitionsPerTopic = getTopicPartitionMap()
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
        }

        return maxOffsetsPerTopic
    }

    fun createTopic(topic: String, partitions: Int) {
        executeWithErrorHandling("create the topic $topic") {
            val stmt = it.prepareStatement(insertTopicStmt)
            stmt.setString(1, topic)
            stmt.setInt(2, partitions)

            stmt.execute()
        }
    }

    fun getTopicPartitionMap(): Map<String, Int> {
        val partitionsPerTopic = mutableMapOf<String, Int>()

        executeWithErrorHandling("retrieve all the topics") {
            val topicsStmt = it.prepareStatement(readTopicsStmt)
            val topicsResult = topicsStmt.executeQuery()
            while (topicsResult.next()) {
                val topic = topicsResult.getString(1)
                val partitions = topicsResult.getInt(2)
                partitionsPerTopic[topic] = partitions
            }
        }

        return partitionsPerTopic
    }

    fun deleteRecordsOlderThan(topic: String, timestamp: Instant) {
        executeWithErrorHandling("clean up records older than $timestamp") {
            val stmt = it.prepareStatement(deleteRecordsStmt)
            stmt.setString(1, topic)
            stmt.setTimestamp(2, Timestamp.from(timestamp))

            stmt.execute()
        }
    }

    fun deleteOffsetsOlderThan(topic: String, timestamp: Instant) {
        executeWithErrorHandling("clean up offsets older than $timestamp") {
            val stmt = it.prepareStatement(deleteOffsetsStmt)
            stmt.setString(1, topic)
            stmt.setTimestamp(2, Timestamp.from(timestamp))

            stmt.execute()
        }
    }

    private fun writeOffsets(
        topic: String,
        consumerGroup: String,
        offsetsPerPartition: Map<Int, Long>,
        connection: Connection
    ) {
        val stmt = connection.prepareStatement(commitOffsetStmt)

        offsetsPerPartition.forEach { (partition, offset) ->
            stmt.setString(1, topic)
            stmt.setString(2, consumerGroup)
            stmt.setInt(3, partition)
            stmt.setLong(4, offset)
            stmt.setTimestamp(5, Timestamp.from(Instant.now()))
            stmt.setBoolean(6, false)

            stmt.addBatch()
        }

        try {
            stmt.executeBatch()
        } catch (e: SQLException) {
            if (isPrimaryKeyViolation(e)) {
                log.warn("Attempted to write offset that has already been committed", e)
                throw OffsetsAlreadyCommittedException()
            }

            throw e
        }
    }


    internal fun commitRecords(records: List<RecordDbEntry>) {
        executeWithErrorHandling("commitRecords") { connection ->
            val stmt = connection.prepareStatement(updateRecordVisibility)

            records.forEach { record ->
                stmt.setLong(1, record.offset)
                stmt.addBatch()
            }

            stmt.executeBatch()
        }
    }

    fun writeRecords(
        records: List<RecordDbEntry>,
        immediatelyVisible: Boolean,
    ) {
        executeWithErrorHandling("write records") { connection ->
            val stmt = connection.prepareStatement(insertRecordStatement)

            records.forEach { record ->
                stmt.setString(1, record.topic)
                stmt.setInt(2, record.partition)
                stmt.setLong(3, record.offset)
                stmt.setBlob(4, toBlob(record.key))
                if (record.value != null) {
                    stmt.setBlob(5, toBlob(record.value))
                } else {
                    stmt.setNull(5, Types.BLOB)
                }
                stmt.setTimestamp(6, Timestamp.from(Instant.now()))
                stmt.setBoolean(7, immediatelyVisible)

                stmt.addBatch()
            }

            stmt.executeBatch()
        }
    }

    /**
     * Executes the specified operation with the necessary error handling.
     * If an error arises during execution, the transaction is rolled back and the exception is re-thrown.
     */
    @VisibleForTesting
    internal fun executeWithErrorHandling(
        operationName: String,
        operation: (connection: Connection) -> Unit,
    ) {
        hikariDatasource.connection.use {
            try {
                operation(it)

                it.commit()
            } catch (e: Exception) {
                log.error("Error while trying to $operationName. Transaction will be rolled back.", e)
                try {
                    it.rollback()
                } catch (e: SQLException) {
                    log.error("Error while trying to roll back a transaction.", e)
                    throw e
                }
                throw e
            }
        }
    }

    private fun toBlob(bytes: ByteArray): Blob {
        return SerialBlob(bytes)
    }

    private fun isPrimaryKeyViolation(e: SQLException): Boolean {
        return e is SQLIntegrityConstraintViolationException ||
                (e.message != null &&
                        e.message!!.contains("Unique index or primary key violation") || //h2
                        e.message!!.contains("duplicate key value violates unique constraint") || // postgres
                        e.message!!.contains("Violation of PRIMARY KEY constraint") || // SQL Server
                        e.message!!.contains(Regex("unique constraint .* violated")) //Oracle
                        )
    }

    private fun setupDB(config: Config): HikariDataSource {
        val dbSettings = config.getConfig("db")
        val hikariConfig = HikariConfig()
        hikariConfig.jdbcUrl = dbSettings.getString("jdbc")
        hikariConfig.username = dbSettings.getString("user")
        hikariConfig.password = dbSettings.getString("pass")
        hikariConfig.isAutoCommit = false
        hikariConfig.maximumPoolSize = dbSettings.getInt("poolSize")
        return HikariDataSource(hikariConfig)
    }
}
