package net.corda.messaging.db.performance

import com.codahale.metrics.MetricRegistry
import net.corda.lifecycle.impl.LifecycleCoordinatorFactoryImpl
import net.corda.lifecycle.impl.registry.LifecycleRegistryImpl
import net.corda.messaging.api.processor.DurableProcessor
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.records.Record
import net.corda.messaging.api.subscription.config.SubscriptionConfig
import net.corda.messaging.db.partition.PartitionAllocator
import net.corda.messaging.db.partition.PartitionAssignor
import net.corda.messaging.db.persistence.DBAccessProvider
import net.corda.messaging.db.persistence.DBAccessProviderCached
import net.corda.messaging.db.persistence.DBAccessProviderImpl
import net.corda.messaging.db.persistence.DBType
import net.corda.messaging.db.publisher.DBPublisher
import net.corda.messaging.db.subscription.DBDurableSubscription
import net.corda.messaging.db.sync.OffsetTrackersManager
import net.corda.messaging.db.util.DbUtils
import net.corda.schema.registry.AvroSchemaRegistry
import net.corda.test.util.eventually
import net.corda.v5.base.concurrent.getOrThrow
import net.corda.v5.base.util.millis
import net.corda.v5.base.util.minutes
import net.corda.v5.base.util.seconds
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.kotlin.anyOrNull
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.sql.DriverManager
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import kotlin.concurrent.thread

@Disabled("Disabled for CI. To be used on an ad-hoc basis for benchmarking")
class PerformanceTests {

    /**
     * Fill in the below details with those corresponding to a running database before executing the test.
     */
    private val jdbcUrl = "<jdbc-url>"
    private val jdbcUsername = "<username>"
    private val jdbcPassword = "<password>"
    private val dbType = DBType.POSTGRESQL

    /**
     * Shared parameters for all the tests
     */
    private val payload = getRandomString(10_000) // ~10 KB
    private val persistenceLayerThreadPoolSize = 15
    private val numberOfPartitions = 50
    private val dbConnectionPoolSize = 30

    private val topic1 = "test.topic1"
    private val topic2 = "test.topic2"

    private lateinit var dbAccessProvider: DBAccessProvider
    private lateinit var offsetTrackersManager: OffsetTrackersManager
    private val avroSchemaRegistry = Mockito.mock(AvroSchemaRegistry::class.java).apply {
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
    private val partitionAssignor = PartitionAssignor()
    private lateinit var partitionAllocator: PartitionAllocator

    private val publisherConfig = PublisherConfig("client-id")

    private val metrics = MetricRegistry()

    @BeforeEach
    fun setup() {
        DriverManager.getConnection(jdbcUrl, jdbcUsername, jdbcPassword).use {
            it.prepareStatement(DbUtils.createTopicRecordsTableStmt).execute()
            it.prepareStatement(DbUtils.createOffsetsTableStmt).execute()
            it.prepareStatement(DbUtils.createTopicsTableStmt).execute()
        }

        val dbAccessProviderImpl = DBAccessProviderImpl(jdbcUrl, jdbcUsername, jdbcPassword, dbType, persistenceLayerThreadPoolSize, 5.seconds, dbConnectionPoolSize)
        dbAccessProvider = DBAccessProviderCached(dbAccessProviderImpl, 1_000)
        dbAccessProvider.start()

        listOf(topic1, topic2).forEach { dbAccessProvider.createTopic(it, numberOfPartitions) }

        offsetTrackersManager = OffsetTrackersManager(dbAccessProvider)
        offsetTrackersManager.start()

        partitionAllocator = PartitionAllocator(dbAccessProvider)
        partitionAllocator.start()
    }

    @AfterEach
    fun cleanup() {
        DriverManager.getConnection(jdbcUrl, jdbcUsername, jdbcPassword).use {
            it.prepareStatement(DbUtils.deleteTopicRecordsTableStmt).execute()
            it.prepareStatement(DbUtils.deleteOffsetsTableStmt).execute()
            it.prepareStatement(DbUtils.deleteTopicsTableStmt).execute()
        }
    }

    @Test
    fun `measure performance of multiple threads writing in parallel to single topic through single publisher`() {
        /**
         * Test Specification - you can adjust the parameters of the test here.
         */
        val numberOfWriters = 10
        val numberOfRecordsPerWriter = 1000
        val batchSize = 1
        val publisherThreadPoolSize = 10

        val publisher = DBPublisher(publisherConfig, avroSchemaRegistry, dbAccessProvider, offsetTrackersManager, partitionAssignor, 1,
            publisherThreadPoolSize)
        publisher.start()

        val latch = CountDownLatch(numberOfWriters)
        val totalDuration = timed {
            (1..numberOfWriters).forEach { writer -> thread {
                val latency = metrics.histogram("writer.latency")
                (1..numberOfRecordsPerWriter).chunked(batchSize).forEach { values ->
                    val records = values.map { Record(topic1, "key-$writer-$it", payload) }
                    val time = timed { publisher.publish(records).map { it.getOrThrow() } }
                    latency.update(time.toMillis())
                }
                latch.countDown()
                println("Writer $writer latency, avg: ${latency.snapshot.mean} ms, p99: ${latency.snapshot.get99thPercentile()}")
            } }
            latch.await()
        }

        publisher.stop()

        val throughput = (numberOfWriters * numberOfRecordsPerWriter) / totalDuration.seconds
        println("Test spec: writers = $numberOfWriters, records per writers = $numberOfRecordsPerWriter, batch size = $batchSize, publisher threads = $publisherThreadPoolSize")
        println("Total duration: $totalDuration, write throughput: $throughput msgs/sec")
    }

    @Test
    fun `measure performance of concurrent publication and consumption on a topic using multiple writers and subsriptions in parallel`() {
        /**
         * Test Specification - you can adjust the parameters of the test here.
         */
        val numberOfWriters = 10
        val numberOfRecordsPerWriter = 1000
        val writerBatchSize = 1
        val publisherThreadPoolSize = 10
        val numberOfSubscriptions = 10
        val subscriptionBatchSize = 100
        val subscriptionPollTimeout = 1.seconds
        val totalRecords = (numberOfWriters * numberOfRecordsPerWriter)

        val publisher = DBPublisher(publisherConfig, avroSchemaRegistry, dbAccessProvider, offsetTrackersManager, partitionAssignor,
            1, publisherThreadPoolSize)
        val processedRecordKeys = ConcurrentHashMap.newKeySet<String>()
        val processor = CopyingProcessor(processedRecordKeys, String::class.java, String::class.java, topic2)
        val subscriptions = (1..numberOfSubscriptions).map {
            val subscriptionConfigTopic = SubscriptionConfig("group-1", topic1)
            DBDurableSubscription(
                subscriptionConfigTopic,
                it,
                processor,
                null,
                avroSchemaRegistry,
                offsetTrackersManager,
                partitionAllocator,
                partitionAssignor,
                dbAccessProvider,
                lifecycleCoordinatorFactory,
                subscriptionPollTimeout,
                subscriptionBatchSize
            )
        }
        subscriptions.forEach { it.start() }
        publisher.start()


        val latch = CountDownLatch(numberOfWriters)
        var publishDuration: Duration? = null
        val totalDuration = timed {
            publishDuration = timed {
                (1..numberOfWriters).forEach { writer -> thread {
                    val latency = metrics.histogram("writer.latency")
                    (1..numberOfRecordsPerWriter).chunked(writerBatchSize).forEach { values ->
                        val records = values.map { Record(topic1, "key-$writer-$it", payload) }
                        val time = timed { publisher.publish(records).map { it.getOrThrow() } }
                        latency.update(time.toMillis())
                    }
                    latch.countDown()
                    println("Writer $writer latency, avg: ${latency.snapshot.mean} ms, p99: ${latency.snapshot.get99thPercentile()}")
                } }
                latch.await()
            }

            eventually(5.minutes, 10.millis) {
                assertThat(processedRecordKeys.size).`as`("not enough records read from topic 1").isEqualTo(totalRecords)
            }
        }

        publisher.stop()
        subscriptions.forEach { it.stop() }

        val writeThroughput = totalRecords / publishDuration!!.seconds
        val readThroughput = totalRecords / totalDuration.seconds
        println("Test spec: writers = $numberOfWriters, records per writer = $numberOfRecordsPerWriter, writer batch size = $writerBatchSize, publisher threads = $publisherThreadPoolSize, " +
                "subscriptions = $numberOfSubscriptions, subscription batch size = $subscriptionBatchSize, subscription poll timeout = $subscriptionPollTimeout")
        println("Total records processed: $totalRecords, total duration: $totalDuration")
        println("Write throughput: $writeThroughput msgs/sec")
        println("Read throughput $readThroughput msgs/sec")
    }

    private fun timed(block: () -> Unit): Duration {
        val before = Instant.now()
        block()
        val after = Instant.now()
        return Duration.between(before, after)
    }

    fun getRandomString(length: Int) : String {
        val allowedChars = ('A'..'Z') + ('a'..'z') + ('0'..'9')
        return (1..length)
            .map { allowedChars.random() }
            .joinToString("")
    }

    class CopyingProcessor<K : Any, V : Any>(
        private val processedRecordKeys: MutableSet<K>,
        override val keyClass: Class<K>,
        override val valueClass: Class<V>,
        private val topicToWriteTo: String?
    ): DurableProcessor<K, V> {

        override fun onNext(events: List<Record<K, V>>): List<Record<*, *>> {
            processedRecordKeys.addAll(events.map { it.key })
            return if (topicToWriteTo != null) {
                events.map { Record(topicToWriteTo, it.key, it.value) }
            } else {
                emptyList()
            }
        }
    }

}
