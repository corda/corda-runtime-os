package net.corda.messaging.kafka.subscription

import com.typesafe.config.Config
import net.corda.messaging.api.exception.CordaMessageAPIFatalException
import net.corda.messaging.api.exception.CordaMessageAPIIntermittentException
import net.corda.messaging.api.processor.CompactedProcessor
import net.corda.messaging.api.records.Record
import net.corda.messaging.kafka.properties.ConfigProperties.Companion.PATTERN_COMPACTED
import net.corda.messaging.kafka.properties.ConfigProperties.Companion.TOPIC
import net.corda.messaging.kafka.subscription.consumer.builder.ConsumerBuilder
import net.corda.messaging.kafka.subscription.consumer.wrapper.ConsumerRecordAndMeta
import net.corda.messaging.kafka.subscription.consumer.wrapper.CordaKafkaConsumer
import net.corda.messaging.kafka.subscription.factory.SubscriptionMapFactory
import net.corda.messaging.kafka.subscription.net.corda.messaging.kafka.TOPIC_PREFIX
import net.corda.messaging.kafka.subscription.net.corda.messaging.kafka.createStandardTestConfig
import net.corda.v5.base.util.contextLogger
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.common.TopicPartition
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class KafkaCompactedSubscriptionImplTest {

    companion object {
        private const val TEST_TIMEOUT_SECONDS = 2L
    }

    private val mapFactory = object : SubscriptionMapFactory<String, String> {
        override fun createMap(): MutableMap<String, String> = ConcurrentHashMap<String, String>()
        override fun destroyMap(map: MutableMap<String, String>) {}
    }

    private val config: Config = createStandardTestConfig().getConfig(PATTERN_COMPACTED)

    private val initialSnapshotResult = List(10) {
        ConsumerRecordAndMeta<String, String>(
            TOPIC_PREFIX,
            ConsumerRecord(TOPIC, 0, it.toLong(), it.toString(), "0")
        )
    }

    private class TestProcessor : CompactedProcessor<String, String> {
        val log = contextLogger()

        override val keyClass: Class<String>
            get() = String::class.java
        override val valueClass: Class<String>
            get() = String::class.java

        var fatalFailSnapshot = false
        var intermittentFailSnapshot = false
        val snapshotMap = mutableMapOf<String, String>()
        override fun onSnapshot(currentData: Map<String, String>) {
            log.info("Processing snapshot: $currentData")
            if (intermittentFailSnapshot) {
                throw CordaMessageAPIIntermittentException("Torpedo ahead!")
            } else if (fatalFailSnapshot) {
                throw CordaMessageAPIFatalException("Abandon Ship!")
            }
            snapshotMap.putAll(currentData)
        }

        var failNext = false
        val incomingRecords = mutableListOf<Record<String, String>>()
        var latestCurrentData: Map<String, String>? = null
        override fun onNext(newRecord: Record<String, String>, oldValue: String?, currentData: Map<String, String>) {
            log.info("Processing new record: $newRecord")
            log.info("Current Data: $currentData")
            if (failNext) {
                throw CordaMessageAPIIntermittentException("Abandon Ship!")
            }
            incomingRecords += newRecord
            latestCurrentData = currentData
        }
    }

    @Test
    @Timeout(TEST_TIMEOUT_SECONDS, unit = TimeUnit.SECONDS)
    fun `compacted subscription returns correct results`() {
        val latch = CountDownLatch(4)
        val processor = TestProcessor()
        val (kafkaConsumer, consumerBuilder) = setupStandardMocks(4)

        doAnswer {
            val iteration = latch.count
            when (iteration) {
                4L -> {
                    initialSnapshotResult
                }
                0L -> throw CordaMessageAPIFatalException("Stop here")
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

        doAnswer {
            listOf(TopicPartition(TOPIC, 0))
        }.whenever(kafkaConsumer).getPartitions(any(), any())

        val subscription = KafkaCompactedSubscriptionImpl(
            config,
            mapFactory,
            consumerBuilder,
            processor,
        )
        subscription.start()
        while (subscription.isRunning) { Thread.sleep(10) }

        verify(kafkaConsumer, times(1)).assign(listOf(TopicPartition(TOPIC, 0)))
        assertThat(processor.snapshotMap.size).isEqualTo(10)
        assertThat(processor.snapshotMap).isEqualTo(initialSnapshotResult.associate { it.record.key() to it.record.value() })

        assertThat(processor.incomingRecords.size).isEqualTo(3)
        assertThat(processor.incomingRecords[0]).isEqualTo(Record(TOPIC, "3", "3"))
        assertThat(processor.incomingRecords[1]).isEqualTo(Record(TOPIC, "2", "2"))
        assertThat(processor.incomingRecords[2]).isEqualTo(Record(TOPIC, "1", "1"))
    }

    @Test
    @Timeout(TEST_TIMEOUT_SECONDS, unit = TimeUnit.SECONDS)
    fun `compacted subscription removes record on null value`() {
        val latch = CountDownLatch(5)
        val processor = TestProcessor()
        val (kafkaConsumer, consumerBuilder) = setupStandardMocks(5)

        doAnswer {
            when (val iteration = latch.count) {
                5L -> {
                    initialSnapshotResult
                }
                2L -> {
                    listOf(
                        ConsumerRecordAndMeta(
                            TOPIC_PREFIX,
                            ConsumerRecord<String, String>(TOPIC, 0, 2, "2", null)
                        )
                    )
                }
                0L -> throw CordaMessageAPIFatalException("Stop here")
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
            config,
            mapFactory,
            consumerBuilder,
            processor,
        )
        subscription.start()
        while (subscription.isRunning) { Thread.sleep(10) }

        assertThat(processor.incomingRecords.size).isEqualTo(4)
        assertThat(processor.incomingRecords[0]).isEqualTo(Record(TOPIC, "4", "4"))
        assertThat(processor.incomingRecords[1]).isEqualTo(Record(TOPIC, "3", "3"))
        assertThat(processor.incomingRecords[2]).isEqualTo(Record(TOPIC, "2", null))
        assertThat(processor.incomingRecords[3]).isEqualTo(Record(TOPIC, "1", "1"))
        assertThat(processor.latestCurrentData?.containsKey("2")).isFalse
        val expectedMap = mapOf(
            "0" to "0", "1" to "1", "3" to "3", "4" to "4", "5" to "0", "6" to "0", "7" to "0", "8" to "0", "9" to "0"
        )
        assertThat(processor.latestCurrentData).isEqualTo(expectedMap)
    }

    @Test
    @Timeout(TEST_TIMEOUT_SECONDS, unit = TimeUnit.SECONDS)
    fun `subscription stops on processor snapshot error`() {
        val processor = TestProcessor()
        val (kafkaConsumer, consumerBuilder) = setupStandardMocks(0)
        doAnswer { initialSnapshotResult }.whenever(kafkaConsumer).poll()

        processor.fatalFailSnapshot = true
        val subscription = KafkaCompactedSubscriptionImpl(
            config,
            mapFactory,
            consumerBuilder,
            processor,
        )
        subscription.start()

        while (subscription.isRunning) { Thread.sleep(10) }
    }

    @Test
    @Timeout(TEST_TIMEOUT_SECONDS, unit = TimeUnit.SECONDS)
    fun `subscription attempts to reconnect after intermittent failure`() {
        val latch = CountDownLatch(7)
        val (kafkaConsumer, consumerBuilder) = setupStandardMocks(7)
        val processor = TestProcessor()

        doAnswer {
            latch.countDown()
            when (latch.count) {
                4L, 2L -> throw CordaMessageAPIIntermittentException("Kaboom!")
                0L -> throw CordaMessageAPIFatalException("Stop here.")
            }
            emptyList<ConsumerRecordAndMeta<String, String>>()
        }.whenever(kafkaConsumer).poll()

        val subscription = KafkaCompactedSubscriptionImpl(
            config,
            mapFactory,
            consumerBuilder,
            processor,
        )
        subscription.start()
        while (subscription.isRunning) {
            Thread.sleep(10)
        }

        // Three calls: First time and after each exception thrown
        verify(consumerBuilder, times(3)).createCompactedConsumer(any(), any(), any(), any())
    }

    @Test
    @Timeout(TEST_TIMEOUT_SECONDS, unit = TimeUnit.SECONDS)
    fun `subscription attempts to reconnect after processor failure`() {
        val latch = CountDownLatch(6)
        val (kafkaConsumer, consumerBuilder) = setupStandardMocks(6)
        val processor = TestProcessor()

        doAnswer {
            mutableMapOf(
                TopicPartition(TOPIC, 0) to 0L,
                TopicPartition(TOPIC, 1) to 0L
            )
        }.whenever(kafkaConsumer).beginningOffsets(any())
        doAnswer {
            val iteration = latch.count
            when (iteration) {
                6L, 4L, 2L -> {
                    initialSnapshotResult
                }
                0L -> throw CordaMessageAPIFatalException("Stop here.")
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

        processor.failNext = true
        val subscription = KafkaCompactedSubscriptionImpl(
            config,
            mapFactory,
            consumerBuilder,
            processor,
        )
        subscription.start()
        while (subscription.isRunning) { Thread.sleep(10) }

        // Four calls: First time and after each exception thrown
        verify(consumerBuilder, times(4)).createCompactedConsumer(any(), any(), any(), any())
    }

    private fun setupStandardMocks(numberOfRecords: Long): Pair<CordaKafkaConsumer<String, String>, ConsumerBuilder<String, String>> {
        val kafkaConsumer: CordaKafkaConsumer<String, String> = mock()
        val consumerBuilder: ConsumerBuilder<String, String> = mock()
        doReturn(kafkaConsumer).whenever(consumerBuilder).createCompactedConsumer(any(), any(), any(), any())
        doReturn(mutableMapOf(TopicPartition(TOPIC, 0) to 0L, TopicPartition(TOPIC, 1) to 0L)).whenever(kafkaConsumer)
            .beginningOffsets(any())
        doReturn(
            mutableMapOf(
                TopicPartition(TOPIC, 0) to initialSnapshotResult.size.toLong() - 1,
                TopicPartition(TOPIC, 1) to 0,
            )
        ).whenever(kafkaConsumer).endOffsets(any())
        doReturn(numberOfRecords+1).whenever(kafkaConsumer).position(any())
        doReturn(setOf(TopicPartition(TOPIC, 0))).whenever(kafkaConsumer).assignment()
        doReturn(mapOf(TopicPartition(TOPIC, 0) to numberOfRecords+1)).whenever(kafkaConsumer).endOffsets(any())
        return Pair(kafkaConsumer, consumerBuilder)
    }
}
