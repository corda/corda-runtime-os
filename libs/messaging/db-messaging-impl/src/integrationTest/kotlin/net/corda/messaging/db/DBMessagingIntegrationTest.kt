package net.corda.messaging.db

import net.corda.lifecycle.impl.LifecycleCoordinatorFactoryImpl
import net.corda.lifecycle.impl.registry.LifecycleRegistryImpl
import net.corda.messaging.api.processor.DurableProcessor
import net.corda.messaging.api.processor.EventLogProcessor
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.records.EventLogRecord
import net.corda.messaging.api.records.Record
import net.corda.messaging.api.subscription.factory.config.SubscriptionConfig
import net.corda.messaging.db.partition.PartitionAllocator
import net.corda.messaging.db.partition.PartitionAssignor
import net.corda.messaging.db.persistence.DBAccessProvider
import net.corda.messaging.db.persistence.DBAccessProviderCached
import net.corda.messaging.db.persistence.DBAccessProviderImpl
import net.corda.messaging.db.persistence.DBType
import net.corda.messaging.db.publisher.DBPublisher
import net.corda.messaging.db.subscription.DBDurableSubscription
import net.corda.messaging.db.subscription.DBEventLogSubscription
import net.corda.messaging.db.sync.OffsetTrackersManager
import net.corda.messaging.db.util.DbUtils.Companion.createOffsetsTableStmt
import net.corda.messaging.db.util.DbUtils.Companion.createTopicRecordsTableStmt
import net.corda.messaging.db.util.DbUtils.Companion.createTopicsTableStmt
import net.corda.schema.registry.AvroSchemaRegistry
import net.corda.test.util.eventually
import net.corda.v5.base.util.millis
import net.corda.v5.base.util.seconds
import org.assertj.core.api.Assertions.assertThat
import org.h2.tools.Server
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.Mockito
import org.mockito.Mockito.mock
import org.mockito.kotlin.anyOrNull
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.sql.DriverManager
import java.util.*

/**
 * Testing the integration of all the components by producing and consuming messages to/from topics.
 */
class DBMessagingIntegrationTest {

    @TempDir
    lateinit var tempFolder: Path

    private lateinit var server: Server
    private val h2Port = 9092
    private val jdbcUrl = "jdbc:h2:tcp://localhost:$h2Port/test"
    private val username = "sa"
    private val password = ""

    private val topic1 = "test.topic1"
    private val topic2 = "test.topic2"
    private val partitions = 10

    private val pollingTimeout = 150.millis

    private lateinit var dbAccessProvider: DBAccessProvider
    private lateinit var offsetTrackersManager: OffsetTrackersManager
    private lateinit var partitionAllocator: PartitionAllocator
    private val partitionAssignor = PartitionAssignor()
    private val avroSchemaRegistry = mock(AvroSchemaRegistry::class.java).apply {
        Mockito.`when`(serialize(anyOrNull())).thenAnswer { invocation ->
            val bytes = (invocation.arguments.first() as String).toByteArray()
            ByteBuffer.wrap(bytes)
        }
        Mockito.`when`(deserialize(anyOrNull(), anyOrNull(), anyOrNull())).thenAnswer { invocation ->
            val bytes = invocation.arguments.first() as ByteBuffer
            StandardCharsets.UTF_8.decode(bytes).toString()
        }
    }
    private val lifecycleCoordinatorFactory = LifecycleCoordinatorFactoryImpl(LifecycleRegistryImpl())

    private val publisherConfig = PublisherConfig("client-id")

    private val subscriptionConfigTopic1 = SubscriptionConfig("group-1", topic1)
    private val subscriptionConfigTopic2 = SubscriptionConfig("group-1", topic2)

    @BeforeEach
    fun setup() {
        server = Server.createTcpServer(
            "-tcpPort",
            h2Port.toString(),
            "-tcpAllowOthers",
            "-ifNotExists",
            "-baseDir",
            tempFolder.toAbsolutePath().toString()
        )
        server.start()

        val connection = DriverManager.getConnection(jdbcUrl, username, password)
        connection.prepareStatement(createTopicRecordsTableStmt).execute()
        connection.prepareStatement(createOffsetsTableStmt).execute()
        connection.prepareStatement(createTopicsTableStmt).execute()

        val dbAccessProviderImpl = DBAccessProviderImpl(jdbcUrl, username, password, DBType.H2, 5)
        dbAccessProvider = DBAccessProviderCached(dbAccessProviderImpl, 50)
        dbAccessProvider.start()

        dbAccessProviderImpl.createTopic(topic1, partitions)
        dbAccessProviderImpl.createTopic(topic2, partitions)

        dbAccessProvider.stop()
        dbAccessProvider.start()

        offsetTrackersManager = OffsetTrackersManager(dbAccessProvider)
        offsetTrackersManager.start()

        partitionAllocator = PartitionAllocator(dbAccessProvider)
        partitionAllocator.start()
    }

    @AfterEach
    fun cleanup() {
        offsetTrackersManager.stop()
        dbAccessProvider.stop()
        server.stop()
    }

    @Test
    fun `test messages are published and consumed via a durable subscription successfully`() {
        val topic1ProcessedRecords = Collections.synchronizedList<Record<String, String>>(mutableListOf())
        val topic2ProcessedRecords = Collections.synchronizedList<Record<String, String>>(mutableListOf())
        val processor1 =
            InMemoryDurableProcessor(topic1ProcessedRecords, String::class.java, String::class.java, topic2)
        val processor2 = InMemoryDurableProcessor(topic2ProcessedRecords, String::class.java, String::class.java, null)
        val subscriptionTopic1 = DBDurableSubscription(
            subscriptionConfigTopic1,
            processor1,
            null,
            avroSchemaRegistry,
            offsetTrackersManager,
            partitionAllocator,
            partitionAssignor,
            dbAccessProvider,
            lifecycleCoordinatorFactory,
            pollingTimeout
        )
        val subscriptionTopic2 = DBDurableSubscription(
            subscriptionConfigTopic2,
            processor2,
            null,
            avroSchemaRegistry,
            offsetTrackersManager,
            partitionAllocator,
            partitionAssignor,
            dbAccessProvider,
            lifecycleCoordinatorFactory,
            pollingTimeout
        )
        val publisher =
            DBPublisher(publisherConfig, avroSchemaRegistry, dbAccessProvider, offsetTrackersManager, partitionAssignor)

        publisher.start()
        subscriptionTopic1.start()
        subscriptionTopic2.start()

        val topic1Records = (1..10).map { Record(topic1, "key-$it", "value-$it") }
        val topic2ExpectedRecords = topic1Records.map { Record(topic2, it.key, it.value) }
        publisher.publish(topic1Records).map { it.get() }

        eventually(5.seconds, 5.millis) {
            synchronized(topic1ProcessedRecords) {
                assertThat(topic1ProcessedRecords.size).`as`("not enough records read from topic 1")
                    .isGreaterThanOrEqualTo(topic1Records.size)
                assertThat(topic1ProcessedRecords).containsAll(topic1Records)
            }
            synchronized(topic2ProcessedRecords) {
                assertThat(topic2ProcessedRecords.size).`as`("not enough records read from topic 2")
                    .isGreaterThanOrEqualTo(topic1Records.size)
                assertThat(topic2ProcessedRecords).containsAll(topic2ExpectedRecords)
            }
        }

        subscriptionTopic1.stop()
        subscriptionTopic2.stop()
        publisher.stop()
    }

    @Test
    fun `test messages are published and consumed via a log event subscription successfully`() {
        val topic1ProcessedRecords = Collections.synchronizedList<Record<String, String>>(mutableListOf())
        val topic2ProcessedRecords = Collections.synchronizedList<Record<String, String>>(mutableListOf())
        val processor1 =
            InMemoryEventLogProcessor(topic1ProcessedRecords, String::class.java, String::class.java, topic2)
        val processor2 = InMemoryEventLogProcessor(topic2ProcessedRecords, String::class.java, String::class.java, null)
        val subscriptionTopic1 = DBEventLogSubscription(
            subscriptionConfigTopic1,
            processor1,
            null,
            avroSchemaRegistry,
            offsetTrackersManager,
            partitionAllocator,
            partitionAssignor,
            dbAccessProvider,
            lifecycleCoordinatorFactory,
            pollingTimeout
        )
        val subscriptionTopic2 = DBEventLogSubscription(
            subscriptionConfigTopic2,
            processor2,
            null,
            avroSchemaRegistry,
            offsetTrackersManager,
            partitionAllocator,
            partitionAssignor,
            dbAccessProvider,
            lifecycleCoordinatorFactory,
            pollingTimeout
        )
        val publisher =
            DBPublisher(publisherConfig, avroSchemaRegistry, dbAccessProvider, offsetTrackersManager, partitionAssignor)

        publisher.start()
        subscriptionTopic1.start()
        subscriptionTopic2.start()

        val topic1Records = (1..10).map { Record(topic1, "key-$it", "value-$it") }
        val topic2ExpectedRecords = topic1Records.map { Record(topic2, it.key, it.value) }
        publisher.publish(topic1Records).map { it.get() }

        eventually(5.seconds, 5.millis) {
            synchronized(topic1ProcessedRecords) {
                assertThat(topic1ProcessedRecords.size).`as`("not enough records read from topic 1")
                    .isGreaterThanOrEqualTo(topic1Records.size)
                assertThat(topic1ProcessedRecords).containsAll(topic1Records)
            }
            synchronized(topic2ProcessedRecords) {
                assertThat(topic2ProcessedRecords.size).`as`("not enough records read from topic 2")
                    .isGreaterThanOrEqualTo(topic1Records.size)
                assertThat(topic2ProcessedRecords).containsAll(topic2ExpectedRecords)
            }
        }

        subscriptionTopic1.stop()
        subscriptionTopic2.stop()
        publisher.stop()
    }

    @Test
    fun `messages can be consumed in parallel by multiple subscriptions`() {
        val topic1ProcessedRecords = Collections.synchronizedList<Record<String, String>>(mutableListOf())
        val topic2ProcessedRecords = Collections.synchronizedList<Record<String, String>>(mutableListOf())
        val processor1 =
            InMemoryDurableProcessor(topic1ProcessedRecords, String::class.java, String::class.java, topic2)
        val processor2 = InMemoryDurableProcessor(topic2ProcessedRecords, String::class.java, String::class.java, null)
        val subscriptionsForTopic1 = (1..3).map {
            val subscriptionConfigTopic1 = SubscriptionConfig("group-1", topic1, it)
            DBDurableSubscription(
                subscriptionConfigTopic1,
                processor1,
                null,
                avroSchemaRegistry,
                offsetTrackersManager,
                partitionAllocator,
                partitionAssignor,
                dbAccessProvider,
                lifecycleCoordinatorFactory,
                pollingTimeout
            )
        }
        val subscriptionTopic2 = DBDurableSubscription(
            subscriptionConfigTopic2,
            processor2,
            null,
            avroSchemaRegistry,
            offsetTrackersManager,
            partitionAllocator,
            partitionAssignor,
            dbAccessProvider,
            lifecycleCoordinatorFactory,
            pollingTimeout
        )
        val publisher =
            DBPublisher(publisherConfig, avroSchemaRegistry, dbAccessProvider, offsetTrackersManager, partitionAssignor)

        publisher.start()
        subscriptionsForTopic1.forEach { it.start() }
        subscriptionTopic2.start()

        val topic1Records = (1..10).map { Record(topic1, "key-$it", "value-$it") }
        val topic2ExpectedRecords = topic1Records.map { Record(topic2, it.key, it.value) }
        publisher.publish(topic1Records).map { it.get() }

        eventually(5.seconds, 5.millis) {
            synchronized(topic1ProcessedRecords) {
                assertThat(topic1ProcessedRecords.size).`as`("not enough records read from topic 1")
                    .isGreaterThanOrEqualTo(topic1Records.size)
                assertThat(topic1ProcessedRecords).containsAll(topic1Records)
            }
            synchronized(topic2ProcessedRecords) {
                assertThat(topic2ProcessedRecords.size).`as`("not enough records read from topic 2")
                    .isGreaterThanOrEqualTo(topic1Records.size)
                assertThat(topic2ProcessedRecords).containsAll(topic2ExpectedRecords)
            }
        }

        subscriptionsForTopic1.forEach { it.stop() }
        subscriptionTopic2.stop()
        publisher.stop()
    }

    class InMemoryEventLogProcessor<K : Any, V : Any>(
        private val processedRecords: MutableList<Record<K, V>>,
        override val keyClass: Class<K>,
        override val valueClass: Class<V>,
        private val topicToWriteTo: String?
    ) : EventLogProcessor<K, V> {

        override fun onNext(events: List<EventLogRecord<K, V>>): List<Record<*, *>> {
            processedRecords.addAll(events.map { Record(it.topic, it.key, it.value) })
            return if (topicToWriteTo != null) {
                events.map { Record(topicToWriteTo, it.key, it.value) }
            } else {
                emptyList()
            }
        }
    }

    class InMemoryDurableProcessor<K : Any, V : Any>(
        private val processedRecords: MutableList<Record<K, V>>,
        override val keyClass: Class<K>,
        override val valueClass: Class<V>,
        private val topicToWriteTo: String?
    ) : DurableProcessor<K, V> {

        override fun onNext(events: List<Record<K, V>>): List<Record<*, *>> {
            processedRecords.addAll(events)
            return if (topicToWriteTo != null) {
                events.map { Record(topicToWriteTo, it.key, it.value) }
            } else {
                emptyList()
            }
        }
    }

}
