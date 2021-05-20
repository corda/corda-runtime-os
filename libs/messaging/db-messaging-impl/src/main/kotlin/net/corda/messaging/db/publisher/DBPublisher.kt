package net.corda.messaging.db.publisher

import com.typesafe.config.Config
import net.corda.messaging.api.exception.CordaMessageAPIFatalException
import net.corda.messaging.api.exception.CordaMessageAPIIntermittentException
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.records.Record
import net.corda.messaging.db.properties.DbProperties
import net.corda.messaging.db.schema.Schema.TableNames.Companion.TOPIC_TABLE_PREFIX
import net.corda.messaging.db.schema.Schema.TopicTable.Companion.KEY_COLUMN_NAME
import net.corda.messaging.db.schema.Schema.TopicTable.Companion.MESSAGE_PAYLOAD_COLUMN_NAME
import net.corda.schema.registry.AvroSchemaRegistry
import net.corda.v5.base.concurrent.CordaFuture
import net.corda.v5.base.internal.concurrent.asCordaFuture
import org.apache.avro.util.ByteBufferInputStream
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.lang.Exception
import java.sql.Connection
import java.sql.DriverManager
import java.sql.SQLClientInfoException
import java.sql.SQLNonTransientException
import java.sql.Types
import java.util.Properties
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class DBPublisher<K: Any, V: Any>(
    private val publisherConfig: PublisherConfig,
    dbConfig: Config,
    private val schemaRegistry: AvroSchemaRegistry,
    threadPoolSize: Int = 25): Publisher<K, V> {

    companion object {
        private val log: Logger = LoggerFactory.getLogger(this::class.java)
    }

    private val executor = Executors.newFixedThreadPool(threadPoolSize)

    private val jdbcUrl = dbConfig.getString(DbProperties.JDBC_URL)
    private val username = dbConfig.getString(DbProperties.DB_USERNAME)
    private val password = dbConfig.getString(DbProperties.DB_PASSWORD)

    private lateinit var connection: Connection

    @Volatile
    private var stopped = true
    private val lock = ReentrantLock()

    override fun start() {
        lock.withLock {
            if (stopped) {
                val props = Properties()
                props.setProperty("user", username)
                props.setProperty("password", password)
                connection = DriverManager.getConnection(jdbcUrl, props)
                connection.autoCommit = false
                log.info("Publisher started for client ID: ${publisherConfig.clientId}, connected to database: $jdbcUrl.")
                stopped = false
            }
        }
    }

    @Suppress("TooGenericExceptionCaught")
    override fun publish(record: Record<K, V>): CordaFuture<Unit> {
        return CompletableFuture.supplyAsync({
            val tableName = "$TOPIC_TABLE_PREFIX${record.topic.replace(".", "_")}"
            val statement = connection.prepareStatement("INSERT INTO $tableName ($KEY_COLUMN_NAME, $MESSAGE_PAYLOAD_COLUMN_NAME) VALUES (?, ?)")

            val serialisedKey = schemaRegistry.serialize(record.key)
            statement.setBlob(1, ByteBufferInputStream(listOf(serialisedKey)))

            if (record.value != null) {
                val serialisedValue = schemaRegistry.serialize(record.value!!)
                statement.setBlob(2, ByteBufferInputStream(listOf(serialisedValue)))
            } else {
                statement.setNull(2, Types.BLOB)
            }

            try {
                statement.execute()
                connection.commit()
            } catch (e: Exception) {
                val errorMessage = "Failed to publish record for client ID: ${publisherConfig.clientId}"
                log.error(errorMessage, e)
                when(e) {
                    is SQLNonTransientException, is SQLClientInfoException,  -> {
                        throw CordaMessageAPIFatalException(errorMessage, e)
                    }
                    else -> {
                        throw CordaMessageAPIIntermittentException(errorMessage, e)
                    }
                }
            }
        }, executor).asCordaFuture()
    }

    override fun publishToPartition(record: Record<K, V>, partition: Int): CordaFuture<Unit> {
        return publish(record)
    }

    override fun close() {
        lock.withLock {
            if (!stopped) {
                connection.close()
                log.info("Publisher stopped for client ID: ${publisherConfig.clientId}.")
            }
        }
    }
}