package net.corda.messaging.kafka.subscription

import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.doAnswer
import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.whenever
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory
import net.corda.messaging.api.processor.CompactedProcessor
import net.corda.messaging.api.records.Record
import net.corda.messaging.api.subscription.factory.config.SubscriptionConfig
import net.corda.messaging.kafka.properties.KafkaProperties
import net.corda.messaging.kafka.subscription.consumer.builder.ConsumerBuilder
import net.corda.messaging.kafka.subscription.consumer.wrapper.ConsumerRecordAndMeta
import net.corda.messaging.kafka.subscription.consumer.wrapper.CordaKafkaConsumer
import net.corda.messaging.kafka.subscription.factory.SubscriptionMapFactory
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.common.TopicPartition
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class KafkaCompactedSubscriptionImplTest {

    companion object {
        private const val TEST_TIMEOUT_SECONDS = 5L
        private const val TOPIC_PREFIX = "test"
        private const val TOPIC = "topic"
    }

    private val mapFactory = object : SubscriptionMapFactory<String, String> {
        override fun createMap(): MutableMap<String, String> = ConcurrentHashMap<String, String>()
        override fun destroyMap(map: MutableMap<String, String>) { }
    }

    private val subscriptionConfig = SubscriptionConfig("group", TOPIC)
    private val config: Config = ConfigFactory.empty()
        .withValue(KafkaProperties.CONSUMER_THREAD_STOP_TIMEOUT, ConfigValueFactory.fromAnyRef(1000))
        .withValue(KafkaProperties.KAFKA_TOPIC_PREFIX, ConfigValueFactory.fromAnyRef(TOPIC_PREFIX))

    private val initialSnapshotResult = List(10) {
        ConsumerRecordAndMeta<String, String>(
            TOPIC_PREFIX,
            ConsumerRecord(TOPIC, 0, it.toLong(), it.toString(), it.toString())
        )
    }

    private val kafkaConsumer: CordaKafkaConsumer<String, String> = mock()
    private val consumerBuilder: ConsumerBuilder<String, String> = mock()

    @BeforeEach
    fun setup() {
        doReturn(kafkaConsumer).whenever(consumerBuilder).createCompactedConsumer(any(), any())
        doReturn(
            mutableMapOf(TopicPartition(TOPIC, 0) to 0L),
            mutableMapOf(TopicPartition(TOPIC, 1) to 0L),
        ).whenever(kafkaConsumer).beginningOffsets(any())
        doReturn(
            mutableMapOf(
                TopicPartition(TOPIC, 0) to initialSnapshotResult.size.toLong()-1,
                TopicPartition(TOPIC, 1) to 0,
            )
        ).whenever(kafkaConsumer).endOffsets(any())
    }

    private val processor = object : CompactedProcessor<String, String> {
        override val keyClass: Class<String>
            get() = String::class.java
        override val valueClass: Class<String>
            get() = String::class.java

        var failSnapshot = false
        val snapshotMap = mutableMapOf<String, String>()
        override fun onSnapshot(currentData: Map<String, String>) {
            if (failSnapshot) {
                throw RuntimeException("Abandon Ship!")
            }
            snapshotMap.putAll(currentData)
        }

        var failNext = false
        val incomingRecords = mutableListOf<Record<String, String>>()
        var latestCurrentData: Map<String, String>? = null
        override fun onNext(newRecord: Record<String, String>, oldValue: String?, currentData: Map<String, String>) {
            if (failNext) {
                throw RuntimeException("Abandon Ship!")
            }
            incomingRecords += newRecord
            latestCurrentData = currentData
        }
    }

    @Test
    fun `compacted subscription returns correct results`() {
        val latch = CountDownLatch(4)

        doAnswer {
            val iteration = latch.count
            when (iteration) {
                4L -> {
                    initialSnapshotResult
                }
                0L -> emptyList() // Don't return anything on errant extra polls
                else -> {
                    listOf(
                        ConsumerRecordAndMeta<String, String>(
                            TOPIC_PREFIX,
                            ConsumerRecord(TOPIC, 0, iteration, iteration.toString(), iteration.toString())
                        )
                    )
                }
            }.also {
                latch.countDown()
            }
        }.whenever(kafkaConsumer).poll()

        val subscription = KafkaCompactedSubscriptionImpl(
            subscriptionConfig,
            config,
            mapFactory,
            consumerBuilder,
            processor,
        )
        subscription.start()
        latch.await(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        subscription.stop()

        assertThat(processor.snapshotMap.size).isEqualTo(10)
        assertThat(processor.snapshotMap).isEqualTo(initialSnapshotResult.associate { it.record.key() to it.record.value() })

        assertThat(processor.incomingRecords.size).isEqualTo(3)
        assertThat(processor.incomingRecords[0]).isEqualTo(Record(TOPIC, "3", "3"))
        assertThat(processor.incomingRecords[1]).isEqualTo(Record(TOPIC, "2", "2"))
        assertThat(processor.incomingRecords[2]).isEqualTo(Record(TOPIC, "1", "1"))
    }

    @Test
    fun `compacted subscription removes record on null value`() {
        val latch = CountDownLatch(5)

        doAnswer {
            when (val iteration = latch.count) {
                5L -> {
                    initialSnapshotResult
                }
                2L -> {
                    listOf(
                        ConsumerRecordAndMeta(
                            TOPIC_PREFIX,
                            ConsumerRecord<String, String>(TOPIC, 0, iteration, iteration.toString(), null)
                        )
                    )
                }
                0L -> emptyList() // Don't return anything on errant extra polls
                else -> {
                    listOf(
                        ConsumerRecordAndMeta(
                            TOPIC_PREFIX,
                            ConsumerRecord(TOPIC, 0, iteration, iteration.toString(), iteration.toString())
                        )
                    )
                }
            }.also {
                latch.countDown()
            }
        }.whenever(kafkaConsumer).poll()

        val subscription = KafkaCompactedSubscriptionImpl(
            subscriptionConfig,
            config,
            mapFactory,
            consumerBuilder,
            processor,
        )
        subscription.start()
        latch.await(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        subscription.stop()

        assertThat(processor.incomingRecords.size).isEqualTo(4)
        assertThat(processor.incomingRecords[0]).isEqualTo(Record(TOPIC, "4", "4"))
        assertThat(processor.incomingRecords[1]).isEqualTo(Record(TOPIC, "3", "3"))
        assertThat(processor.incomingRecords[2]).isEqualTo(Record(TOPIC, "2", null))
        assertThat(processor.incomingRecords[3]).isEqualTo(Record(TOPIC, "1", "1"))
        assertThat(processor.latestCurrentData?.containsKey("2")).isFalse
        val expectedMap = mutableMapOf<String, String>()
        initialSnapshotResult.associateTo(expectedMap) { it.record.key() to it.record.value() }
        expectedMap.remove("2")
        assertThat(processor.latestCurrentData).isEqualTo(expectedMap)
    }

    @Test
    @Timeout(2, unit = TimeUnit.SECONDS)
    fun `subscription stops on processor snapshot error`() {
        doAnswer { initialSnapshotResult }.whenever(kafkaConsumer).poll()

        processor.failSnapshot = true
        val subscription = KafkaCompactedSubscriptionImpl(
            subscriptionConfig,
            config,
            mapFactory,
            consumerBuilder,
            processor,
        )
        subscription.start()
        
        while (subscription.isRunning) {
            Thread.sleep(500)
        }
    }
}
