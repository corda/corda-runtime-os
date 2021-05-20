package net.corda.messaging.db.subscription

import com.typesafe.config.Config
import net.corda.messaging.api.processor.DurableProcessor
import net.corda.messaging.api.records.Record
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
import net.corda.schema.registry.AvroSchemaRegistry
import net.corda.v5.base.util.seconds
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.lang.Exception
import java.nio.ByteBuffer
import java.sql.Blob
import java.sql.Connection
import java.sql.DriverManager
import java.sql.PreparedStatement
import java.sql.SQLIntegrityConstraintViolationException
import java.time.Duration
import java.util.Properties
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class DBDurableSubscription<K: Any, V: Any>(
    private val subscriptionConfig: SubscriptionConfig,
    private val dbConfig: Config,
    private val durableProcessor: DurableProcessor<K, V>,
    private val avroSchemaRegistry: AvroSchemaRegistry,
    private val pollingDelay: Duration = 1.seconds,
    private val batchSize: Int = 100,
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

    private val executor = Executors.newSingleThreadExecutor()

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
                readOffsetStatement = connection.prepareStatement("SELECT MAX($COMMITTED_OFFSET_COLUMN_NAME) FROM $offsetTableName WHERE $CONSUMER_GROUP_COLUMN_NAME = ?")
                writeOffsetStatement = connection.prepareStatement("INSERT INTO $offsetTableName ($CONSUMER_GROUP_COLUMN_NAME, $COMMITTED_OFFSET_COLUMN_NAME) VALUES (?, ?)")

                val initialOffset = getOffset()
                executor.submit { processingLoop(initialOffset) }
                log.info("Subscription started for group: ${subscriptionConfig.groupName}, connected to database: $jdbcUrl.")
                stopped = false
            }
        }
    }

    override fun stop() {
        lock.withLock {
            if (!stopped) {
                stopped = true
                executor.shutdown()
                executor.awaitTermination(pollingDelay.toMillis() * 2, TimeUnit.MILLISECONDS)
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
                val recordsByOffset = fetchRecords(nextItemOffset, batchSize)
                if (recordsByOffset.isEmpty()) {
                    Thread.sleep(pollingDelay.toMillis())
                } else {
                    durableProcessor.onNext(recordsByOffset.values.toList())
                    val maxOffset = recordsByOffset.keys.maxOrNull()!!
                    commitOffset(maxOffset)
                    nextItemOffset = maxOffset + 1
                }
            } catch (e: Exception) {
                log.error("Received error while processing records from topic ${subscriptionConfig.eventTopic} for ${subscriptionConfig.groupName} at offset $nextItemOffset", e)
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
            val value = if (serialisedValue != null) avroSchemaRegistry.deserialize(serialisedValue.toByteBuffer(), durableProcessor.valueClass, null) else null
            records[offset] = Record(subscriptionConfig.eventTopic, key, value)
        }

        return records
    }

    private fun commitOffset(offset: Long) {
        try {
            writeOffsetStatement.setString(1, subscriptionConfig.groupName)
            writeOffsetStatement.setLong(2, offset)
            writeOffsetStatement.execute()
            connection.commit()
        } catch (e: SQLIntegrityConstraintViolationException) {
            log.warn("Offset $offset already committed for topic ${subscriptionConfig.eventTopic} and group ${subscriptionConfig.groupName}.")
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