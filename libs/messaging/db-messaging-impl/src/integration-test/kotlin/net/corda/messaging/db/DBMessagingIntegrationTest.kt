package net.corda.messaging.db

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import net.corda.data.crypto.SecureHash
import net.corda.messaging.api.processor.DurableProcessor
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.records.Record
import net.corda.messaging.api.subscription.factory.config.SubscriptionConfig
import net.corda.messaging.db.properties.DbProperties.Companion.DB_PASSWORD
import net.corda.messaging.db.properties.DbProperties.Companion.DB_USERNAME
import net.corda.messaging.db.properties.DbProperties.Companion.JDBC_URL
import net.corda.messaging.db.publisher.DBPublisher
import net.corda.messaging.db.schema.Schema.OffsetTable.Companion.COMMITTED_OFFSET_COLUMN_NAME
import net.corda.messaging.db.schema.Schema.OffsetTable.Companion.CONSUMER_GROUP_COLUMN_NAME
import net.corda.messaging.db.schema.Schema.TableNames.Companion.OFFSET_TABLE_PREFIX
import net.corda.messaging.db.schema.Schema.TableNames.Companion.TOPIC_TABLE_PREFIX
import net.corda.messaging.db.schema.Schema.TopicTable.Companion.KEY_COLUMN_NAME
import net.corda.messaging.db.schema.Schema.TopicTable.Companion.MESSAGE_PAYLOAD_COLUMN_NAME
import net.corda.messaging.db.schema.Schema.TopicTable.Companion.OFFSET_COLUMN_NAME
import net.corda.messaging.db.subscription.DBDurableSubscription
import net.corda.messaging.db.sync.OffsetTracker
import net.corda.schema.registry.impl.AvroSchemaRegistryImpl
import net.corda.v5.base.util.millis
import org.assertj.core.api.Assertions.assertThat
import org.h2.tools.Server
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.ByteBuffer
import java.nio.file.Path
import java.sql.Connection
import java.sql.DriverManager
import java.util.concurrent.CountDownLatch


class DBMessagingIntegrationTest {

    private val groupName = "test-group"
    private val clientId = "test-client"
    private val topicName = "test.topic"

    private val latch = CountDownLatch(1)

    private val offsetTracker = OffsetTracker()

    @TempDir
    lateinit var tempFolder: Path

    private lateinit var server: Server
    private lateinit var dbConfig: Config
    private lateinit var connection: Connection

    @BeforeEach
    fun setup() {
        server = Server.createTcpServer("-tcpPort", "9092", "-tcpAllowOthers", "-ifNotExists", "-baseDir", tempFolder.toAbsolutePath().toString())
        server.start()

        dbConfig = ConfigFactory.load()
        connection = DriverManager.getConnection(dbConfig.getString(JDBC_URL), dbConfig.getString(DB_USERNAME), dbConfig.getString(DB_PASSWORD))
        connection.prepareStatement("CREATE TABLE ${TOPIC_TABLE_PREFIX}${topicName.replace(".", "_")} ($OFFSET_COLUMN_NAME BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, $KEY_COLUMN_NAME VARCHAR, $MESSAGE_PAYLOAD_COLUMN_NAME VARCHAR);").execute()
        connection.prepareStatement("CREATE TABLE ${OFFSET_TABLE_PREFIX}${topicName.replace(".", "_")} ($CONSUMER_GROUP_COLUMN_NAME VARCHAR NOT NULL, $COMMITTED_OFFSET_COLUMN_NAME BIGINT NOT NULL, PRIMARY KEY ($CONSUMER_GROUP_COLUMN_NAME, $COMMITTED_OFFSET_COLUMN_NAME))").execute()
    }

    @AfterEach
    fun cleanup() {
        server.stop()
    }

    @Test
    fun `can write and read messages with null values successfully from topic backed by database`() {
        val subscriptionConfig = SubscriptionConfig(groupName, topicName)
        val publisherConfig = PublisherConfig(clientId)
        val dbPublisher = DBPublisher<SecureHash, SecureHash>(publisherConfig, dbConfig, AvroSchemaRegistryImpl(), offsetTracker)
        dbPublisher.start()

        val messagesToWrite = listOf("msg-1", "msg-2", "msg-3").map {
            SecureHash("algorithm", ByteBuffer.wrap(it.toByteArray(Charsets.UTF_8)))
        }
        messagesToWrite.forEach { value ->
            dbPublisher.publish(listOf(Record(topicName, value, null))).first().getOrThrow()
        }

        val readMessages = mutableListOf<Pair<SecureHash?, SecureHash?>>()
        val processor = InMemoryHolderProcessor(readMessages, latch, 3)
        val subscription =  DBDurableSubscription(subscriptionConfig, dbConfig, processor, null, AvroSchemaRegistryImpl(), offsetTracker, 100.millis)

        subscription.start()
        latch.await()
        subscription.stop()

        assertThat(readMessages).hasSize(3)
        readMessages.forEachIndexed { index, _ ->
            assertNull(readMessages[index].second)
            assertTrue(readMessages[index].first!!.algorithm == messagesToWrite[index].algorithm)
            assertTrue(readMessages[index].first!!.serverHash.array().contentEquals(messagesToWrite[index].serverHash.array()))
        }
    }

    @Test
    fun `can write and read messages with non-null values successfully from topic backed by database`() {
        val subscriptionConfig = SubscriptionConfig(groupName, topicName)
        val publisherConfig = PublisherConfig(clientId)
        val dbPublisher = DBPublisher<SecureHash, SecureHash>(publisherConfig, dbConfig, AvroSchemaRegistryImpl(), offsetTracker)
        dbPublisher.start()

        val messagesToWrite = listOf("msg-1", "msg-2", "msg-3").map {
            SecureHash("algorithm", ByteBuffer.wrap(it.toByteArray(Charsets.UTF_8)))
        }
        messagesToWrite.forEach { value ->
            dbPublisher.publish(listOf(Record(topicName, value, value))).first().getOrThrow()
        }

        val readMessages = mutableListOf<Pair<SecureHash?, SecureHash?>>()
        val processor = InMemoryHolderProcessor(readMessages, latch, 3)
        val subscription =  DBDurableSubscription(subscriptionConfig, dbConfig, processor, null, AvroSchemaRegistryImpl(), offsetTracker, 100.millis)

        subscription.start()
        latch.await()
        subscription.stop()

        assertThat(readMessages).hasSize(3)
        readMessages.forEachIndexed { index, _ ->
            assertThat(readMessages[index].first!!.serverHash == readMessages[index].second!!.serverHash)
            assertTrue(readMessages[index].first!!.algorithm == messagesToWrite[index].algorithm)
            assertTrue(readMessages[index].first!!.serverHash.array().contentEquals(messagesToWrite[index].serverHash.array()))
        }
    }

    @Test
    fun `multiple consumers groups can read records successfully from topic backed by database`() {
        val subscriptionConfig = SubscriptionConfig(groupName, topicName)
        val publisherConfig = PublisherConfig(clientId)
        val dbPublisher = DBPublisher<SecureHash, SecureHash>(publisherConfig, dbConfig, AvroSchemaRegistryImpl(), offsetTracker)
        dbPublisher.start()

        val messagesToWrite = listOf("msg-1", "msg-2", "msg-3").map {
            SecureHash("algorithm", ByteBuffer.wrap(it.toByteArray(Charsets.UTF_8)))
        }
        messagesToWrite.forEach { value ->
            dbPublisher.publish(listOf(Record(topicName, value, value))).first().getOrThrow()
        }

        val readMessages1 = mutableListOf<Pair<SecureHash?, SecureHash?>>()
        val readMessages2 = mutableListOf<Pair<SecureHash?, SecureHash?>>()
        val latch1 = CountDownLatch(1)
        val latch2 = CountDownLatch(1)
        val processor1 = InMemoryHolderProcessor(readMessages1, latch1, 3)
        val processor2 = InMemoryHolderProcessor(readMessages2, latch2, 3)
        val subscription1 =  DBDurableSubscription(subscriptionConfig.copy(groupName = "group1"), dbConfig, processor1, null, AvroSchemaRegistryImpl(), offsetTracker, 100.millis)
        val subscription2 = DBDurableSubscription(subscriptionConfig.copy(groupName = "group2"), dbConfig, processor2, null, AvroSchemaRegistryImpl(), offsetTracker, 100.millis)

        subscription1.start()
        latch1.await()
        subscription1.stop()

        subscription2.start()
        latch2.await()
        subscription2.stop()

        assertThat(readMessages1).hasSize(3)
        assertThat(readMessages2).hasSize(3)
        readMessages1.forEachIndexed { index, _ ->
            assertThat(readMessages1[index].first!!.algorithm == readMessages2[index].first!!.algorithm)
            assertThat(readMessages1[index].first!!.serverHash.array().contentEquals(readMessages2[index].first!!.serverHash.array()))
            assertThat(readMessages1[index].second!!.algorithm == readMessages2[index].second!!.algorithm)
            assertThat(readMessages1[index].second!!.serverHash.array().contentEquals(readMessages2[index].second!!.serverHash.array()))
        }
    }

    class InMemoryHolderProcessor(private val readItems: MutableList<Pair<SecureHash?, SecureHash?>>, private val latch: CountDownLatch, private val numberOfMessagesToWaitFor: Int): DurableProcessor<SecureHash, SecureHash> {
        override fun onNext(events: List<Record<SecureHash, SecureHash>>): List<Record<*, *>> {
            events.forEach { readItems.add(it.key to it.value) }
            if (events.size == numberOfMessagesToWaitFor) {
                latch.countDown()
            }
            return emptyList()
        }

        override val keyClass: Class<SecureHash>
            get() = SecureHash::class.java
        override val valueClass: Class<SecureHash>
            get() = SecureHash::class.java
    }

}