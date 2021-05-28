package net.corda.messaging.db.subscription

import com.typesafe.config.Config
import net.corda.messaging.api.processor.DurableProcessor
import net.corda.messaging.api.records.Record
import net.corda.messaging.api.subscription.PartitionAssignmentListener
import net.corda.messaging.api.subscription.Subscription
import net.corda.messaging.api.subscription.factory.config.SubscriptionConfig
import net.corda.messaging.db.properties.DbProperties
import net.corda.messaging.db.schema.Schema.OffsetTable.Companion.COMMITTED_OFFSET_COLUMN_NAME
import net.corda.messaging.db.schema.Schema.OffsetTable.Companion.CONSUMER_GROUP_COLUMN_NAME
import net.corda.messaging.db.schema.Schema.TableNames.Companion.OFFSET_TABLE_PREFIX
import net.corda.messaging.db.schema.Schema.TableNames.Companion.TOPIC_TABLE_PREFIX
import net.corda.messaging.db.schema.Schema.TopicTable.Companion.KEY_COLUMN_NAME
import net.corda.messaging.db.schema.Schema.TopicTable.Companion.MESSAGE_PAYLOAD_COLUMN_NAME
import net.corda.messaging.db.schema.Schema.TopicTable.Companion.OFFSET_COLUMN_NAME
import net.corda.messaging.db.sync.OffsetTracker
import net.corda.schema.registry.AvroSchemaRegistry
import net.corda.v5.base.util.seconds
import org.apache.avro.util.ByteBufferInputStream
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.nio.ByteBuffer
import java.sql.Blob
import java.sql.Connection
import java.sql.DriverManager
import java.sql.PreparedStatement
import java.sql.SQLIntegrityConstraintViolationException
import java.sql.Statement
import java.sql.Types
import java.time.Duration
import java.util.Properties
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.thread
import kotlin.concurrent.withLock
import kotlin.math.max

@Suppress("LongParameterList")
class DBDurableSubscription<K: Any, V: Any>(
    private val subscriptionConfig: SubscriptionConfig,
    private val dbConfig: Config,
    private val durableProcessor: DurableProcessor<K, V>,
    private val partitionAssignmentListener: PartitionAssignmentListener?,
    private val avroSchemaRegistry: AvroSchemaRegistry,
    private val offsetTracker: OffsetTracker,
    private val pollingTimeout: Duration = 1.seconds,
    private val batchSize: Int = 100
) : Subscription<K, V> {

    init {
        require(batchSize > 0) { "The batch size needs to be positive." }
    }

    companion object {
        private val log: Logger = LoggerFactory.getLogger(this::class.java)
    }

    private val jdbcUrl = dbConfig.getString(DbProperties.JDBC_URL)
    private val username = dbConfig.getString(DbProperties.DB_USERNAME)
    private val password = dbConfig.getString(DbProperties.DB_PASSWORD)

    private val topicTableName = "$TOPIC_TABLE_PREFIX${subscriptionConfig.eventTopic.replace(".", "_")}"
    private val offsetTableName = "$OFFSET_TABLE_PREFIX${subscriptionConfig.eventTopic.replace(".", "_")}"

    private lateinit var connection: Connection
    private lateinit var readMessageStatement: PreparedStatement
    private lateinit var readOffsetStatement: PreparedStatement
    private lateinit var writeOffsetStatement: PreparedStatement

    private var eventLoopThread: Thread? = null

    @Volatile
    private var stopped = true
    private val lock = ReentrantLock()

    override val isRunning: Boolean
        get() {
            return !stopped
        }

    override fun start() {
        lock.withLock {
            if (!isRunning) {
                val props = Properties()
                props.setProperty("user", username)
                props.setProperty("password", password)
                connection = DriverManager.getConnection(jdbcUrl, props)
                connection.autoCommit = false
                readMessageStatement = connection.prepareStatement("SELECT * FROM $topicTableName WHERE $OFFSET_COLUMN_NAME >= ? LIMIT ?")
                readOffsetStatement = connection.prepareStatement("SELECT MAX($COMMITTED_OFFSET_COLUMN_NAME) FROM $offsetTableName " +
                                                                        "WHERE $CONSUMER_GROUP_COLUMN_NAME = ?")
                writeOffsetStatement = connection.prepareStatement("INSERT INTO $offsetTableName  " +
                                                            "($CONSUMER_GROUP_COLUMN_NAME, $COMMITTED_OFFSET_COLUMN_NAME) VALUES (?, ?)")

                partitionAssignmentListener?.onPartitionsAssigned(listOf(subscriptionConfig.eventTopic to 0))
                val initialOffset = getOffset()
                offsetTracker.advanceOffset(subscriptionConfig.eventTopic, initialOffset)
                eventLoopThread = thread(
                    true,
                    true,
                    null,
                    "DB Durable subscription processing thread ${subscriptionConfig.groupName}-${subscriptionConfig.eventTopic}",
                    -1
                ) { processingLoop(initialOffset) }
                log.info("Subscription started for group: ${subscriptionConfig.groupName}, connected to database: $jdbcUrl.")
                stopped = false
            }
        }
    }

    override fun stop() {
        lock.withLock {
            if (!stopped) {
                stopped = true
                partitionAssignmentListener?.onPartitionsUnassigned(listOf(subscriptionConfig.eventTopic to 0))
                eventLoopThread!!.join(pollingTimeout.toMillis() * 2)
                connection.close()
                log.info("Subscription stopped for group: ${subscriptionConfig.groupName}.")
            }
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private fun processingLoop(initialOffset: Long) {
        var nextItemOffset = initialOffset
        while (!stopped) {
            try {
                val awaitedOffset = nextItemOffset + batchSize - 1
                offsetTracker.waitForOffset(subscriptionConfig.eventTopic, awaitedOffset, pollingTimeout)

                val recordsByOffset = fetchRecords(nextItemOffset, batchSize)
                if (recordsByOffset.isNotEmpty()) {
                    val newRecords = durableProcessor.onNext(recordsByOffset.values.toList())
                    val maxOffset = recordsByOffset.keys.maxOrNull()!!
                    processRecordsAndCommitOffsetAtomically(newRecords, maxOffset)
                    nextItemOffset = maxOffset + 1
                }
            } catch (e: Exception) {
                log.error("Received error while processing records from topic ${subscriptionConfig.eventTopic} " +
                        "for ${subscriptionConfig.groupName} at offset $nextItemOffset", e)
            }
        }
    }

    private fun fetchRecords(fromOffset: Long, batchSize: Int): Map<Long, Record<K, V>> {
        readMessageStatement.setLong(1, fromOffset)
        readMessageStatement.setInt(2, batchSize)
        val resultSet = readMessageStatement.executeQuery()

        val records = mutableMapOf<Long, Record<K, V>>()
        while (resultSet.next()) {
            val offset = resultSet.getLong(OFFSET_COLUMN_NAME)
            val serialisedKey = resultSet.getBlob(KEY_COLUMN_NAME)
            val serialisedValue = resultSet.getBlob(MESSAGE_PAYLOAD_COLUMN_NAME)

            val key = avroSchemaRegistry.deserialize(serialisedKey.toByteBuffer(), durableProcessor.keyClass, null)
            val value = if (serialisedValue != null) {
                avroSchemaRegistry.deserialize(serialisedValue.toByteBuffer(), durableProcessor.valueClass, null)
            } else {
                null
            }
            records[offset] = Record(subscriptionConfig.eventTopic, key, value)
        }

        return records
    }

    private fun processRecordsAndCommitOffsetAtomically(records: List<Record<*, *>>, offset: Long) {
        val offsetsPerTopic = processRecords(records)
        commitOffset(offset)
        connection.commit()
        offsetsPerTopic.forEach { (topic, offset) ->
            offsetTracker.advanceOffset(topic, offset)
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private fun processRecords(records:List<Record<*, *>>): Map<String, Long> {
        val maxOffsets = mutableMapOf<String, Long>()
        records.forEach { record ->
            maxOffsets.putIfAbsent(record.topic, -1L)

            val tableName = "$TOPIC_TABLE_PREFIX${record.topic.replace(".", "_")}"
            val statement = connection.prepareStatement("INSERT INTO $tableName ($KEY_COLUMN_NAME, $MESSAGE_PAYLOAD_COLUMN_NAME) " +
                                                                             "VALUES (?, ?)", Statement.RETURN_GENERATED_KEYS)

            val serialisedKey = avroSchemaRegistry.serialize(record.key)
            statement.setBlob(1, ByteBufferInputStream(listOf(serialisedKey)))

            if (record.value != null) {
                val serialisedValue = avroSchemaRegistry.serialize(record.value!!)
                statement.setBlob(2, ByteBufferInputStream(listOf(serialisedValue)))
            } else {
                statement.setNull(2, Types.BLOB)
            }

            try {
                statement.executeUpdate()
                val resultSet = statement.generatedKeys.apply { next() }
                val offset = resultSet.getLong(1)
                maxOffsets[record.topic] = max(maxOffsets[record.topic]!!, offset)
            } catch (e: Exception) {
                val errorMessage = "Subscription for group ${subscriptionConfig.groupName} failed to write record on topic ${record.topic}."
                log.error(errorMessage, e)
                throw e
            }
        }

        return maxOffsets
    }

    private fun commitOffset(offset: Long) {
        try {
            writeOffsetStatement.setString(1, subscriptionConfig.groupName)
            writeOffsetStatement.setLong(2, offset)
            writeOffsetStatement.execute()
        } catch (e: SQLIntegrityConstraintViolationException) {
            log.warn("Offset $offset already committed for topic ${subscriptionConfig.eventTopic} " +
                    "and group ${subscriptionConfig.groupName}.")
        }
    }

    private fun getOffset(): Long {
        readOffsetStatement.setString(1, subscriptionConfig.groupName)
        val resultSet = readOffsetStatement.executeQuery()

        return if (resultSet.next()) {
            resultSet.getLong(1)
        } else {
            0
        }
    }

    private fun Blob.toByteBuffer(): ByteBuffer {
        val byteArray = this.getBytes(0, this.length().toInt())
        return ByteBuffer.wrap(byteArray)
    }

}