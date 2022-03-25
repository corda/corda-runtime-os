package net.corda.messaging.subscription

import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.messagebus.api.CordaTopicPartition
import net.corda.messagebus.api.consumer.CordaConsumer
import net.corda.messagebus.api.consumer.CordaConsumerRecord
import net.corda.messagebus.api.consumer.builder.CordaConsumerBuilder
import net.corda.messaging.TOPIC_PREFIX
import net.corda.messaging.api.exception.CordaMessageAPIFatalException
import net.corda.messaging.api.exception.CordaMessageAPIIntermittentException
import net.corda.messaging.api.processor.CompactedProcessor
import net.corda.messaging.api.records.Record
import net.corda.messaging.constants.SubscriptionType
import net.corda.messaging.createResolvedSubscriptionConfig
import net.corda.messaging.subscription.factory.MapFactory
import net.corda.v5.base.util.contextLogger
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.mockito.invocation.InvocationOnMock
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class CompactedSubscriptionImplTest {

    companion object {
        private const val TEST_TIMEOUT_SECONDS = 2L
    }

    private val mapFactory = object : MapFactory<String, String> {
        override fun createMap(): MutableMap<String, String> = ConcurrentHashMap<String, String>()
        override fun destroyMap(map: MutableMap<String, String>) {}
    }

    private val config = createResolvedSubscriptionConfig(SubscriptionType.COMPACTED)

    private val initialSnapshotResult = List(10) {
        CordaConsumerRecord(config.topic, 0, it.toLong(), it.toString(), "0", 0)
    }
    private val lifecycleCoordinatorFactory: LifecycleCoordinatorFactory = mock()
    private val lifecycleCoordinator: LifecycleCoordinator = mock()

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

    @BeforeEach
    fun setup() {
        doReturn(lifecycleCoordinator).`when`(lifecycleCoordinatorFactory).createCoordinator(any(), any())
    }

    @Test
    @Timeout(TEST_TIMEOUT_SECONDS, unit = TimeUnit.SECONDS)
    fun `compacted subscription returns correct results`() {
        val latch = CountDownLatch(4)
        val processor = TestProcessor()
        val (consumer, consumerBuilder)  = setupStandardMocks(4) {
            val iteration = latch.count
            when (iteration) {
                4L -> {
                    initialSnapshotResult
                }
                0L -> throw CordaMessageAPIFatalException("Stop here")
                else -> {
                    listOf(
                        CordaConsumerRecord(
                            config.topic,
                            0,
                            iteration,
                            iteration.toString(),
                            iteration.toString(),
                            0
                        )
                    )
                }
            }.also {
                latch.countDown()
            }
        }

        val subscription = CompactedSubscriptionImpl(
            config,
            mapFactory,
            consumerBuilder,
            processor,
            lifecycleCoordinatorFactory
        )
        subscription.start()
        while (subscription.isRunning) {
            Thread.sleep(10)
        }

        verify(consumer, times(1)).assign(listOf(CordaTopicPartition(config.topic, 0)))
        assertThat(processor.snapshotMap.size).isEqualTo(10)
        assertThat(processor.snapshotMap).isEqualTo(initialSnapshotResult.associate { it.key to it.value!! })

        assertThat(processor.incomingRecords.size).isEqualTo(3)
        assertThat(processor.incomingRecords[0]).isEqualTo(Record(config.topic, "3", "3"))
        assertThat(processor.incomingRecords[1]).isEqualTo(Record(config.topic, "2", "2"))
        assertThat(processor.incomingRecords[2]).isEqualTo(Record(config.topic, "1", "1"))
    }

    @Test
    @Timeout(TEST_TIMEOUT_SECONDS, unit = TimeUnit.SECONDS)
    fun `compacted subscription removes record on null value`() {
        val latch = CountDownLatch(5)
        val processor = TestProcessor()
        val (_, consumerBuilder) = setupStandardMocks(5) {
            when (val iteration = latch.count) {
                5L -> {
                    initialSnapshotResult
                }
                2L -> {
                    listOf(
                        CordaConsumerRecord<Any, Any>(config.topic, 0, 2, "2", null, 0)
                    )
                }
                0L -> throw CordaMessageAPIFatalException("Stop here")
                else -> {
                    listOf(
                        CordaConsumerRecord(config.topic, 0, iteration, iteration.toString(), iteration.toString(), 0)
                    )
                }
            }.also {
                latch.countDown()
            }
        }

        val subscription = CompactedSubscriptionImpl(
            config,
            mapFactory,
            consumerBuilder,
            processor,
            lifecycleCoordinatorFactory
        )
        subscription.start()
        while (subscription.isRunning) {
            Thread.sleep(10)
        }

        assertThat(processor.incomingRecords.size).isEqualTo(4)
        assertThat(processor.incomingRecords[0]).isEqualTo(Record(config.topic, "4", "4"))
        assertThat(processor.incomingRecords[1]).isEqualTo(Record(config.topic, "3", "3"))
        assertThat(processor.incomingRecords[2]).isEqualTo(Record(config.topic, "2", null))
        assertThat(processor.incomingRecords[3]).isEqualTo(Record(config.topic, "1", "1"))
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
        val (_, consumerBuilder) = setupStandardMocks(0) { initialSnapshotResult }

        processor.fatalFailSnapshot = true
        val subscription = CompactedSubscriptionImpl(
            config,
            mapFactory,
            consumerBuilder,
            processor,
            lifecycleCoordinatorFactory
        )
        subscription.start()

        while (subscription.isRunning) {
            Thread.sleep(10)
        }
    }

    @Test
    @Timeout(TEST_TIMEOUT_SECONDS, unit = TimeUnit.SECONDS)
    fun `subscription attempts to reconnect after intermittent failure`() {
        val latch = CountDownLatch(7)
        val processor = TestProcessor()
        val (_, consumerBuilder) = setupStandardMocks(7) {
            latch.countDown()
            when (latch.count) {
                4L, 2L -> throw CordaMessageAPIIntermittentException("Kaboom!")
                0L -> throw CordaMessageAPIFatalException("Stop here.")
            }
            emptyList<CordaConsumerRecord<Any, Any>>()
        }

        val subscription = CompactedSubscriptionImpl(
            config,
            mapFactory,
            consumerBuilder,
            processor,
            lifecycleCoordinatorFactory
        )
        subscription.start()
        while (subscription.isRunning) {
            Thread.sleep(10)
        }

        // Three calls: First time and after each exception thrown
        verify(consumerBuilder, times(3)).createConsumer<Any, Any>(any(), any(), any(), any(), any(), anyOrNull())
    }

    @Test
    @Timeout(TEST_TIMEOUT_SECONDS, unit = TimeUnit.SECONDS)
    fun `subscription attempts to reconnect after processor failure`() {
        val latch = CountDownLatch(6)
        val processor = TestProcessor()
        val (_, consumerBuilder) = setupStandardMocks(6) {
            val iteration = latch.count
            when (iteration) {
                6L, 4L, 2L -> {
                    initialSnapshotResult
                }
                0L -> throw CordaMessageAPIFatalException("Stop here.")
                else -> {
                    listOf(
                        CordaConsumerRecord(TOPIC_PREFIX + config.topic, 0, iteration, iteration.toString(), iteration.toString(), 0)
                    )
                }
            }.also {
                latch.countDown()
            }
        }

        processor.failNext = true
        val subscription = CompactedSubscriptionImpl(
            config,
            mapFactory,
            consumerBuilder,
            processor,
            lifecycleCoordinatorFactory
        )
        subscription.start()
        while (subscription.isRunning) {
            Thread.sleep(10)
        }

        // Four calls: First time and after each exception thrown
        verify(consumerBuilder, times(4)).createConsumer<Any, Any>(any(), any(), any(), any(), any(), anyOrNull())
    }

    private fun setupStandardMocks(
        numberOfRecords: Long,
        onPoll: (InvocationOnMock) -> List<CordaConsumerRecord<*, *>>
    ):  Pair<CordaConsumer<Any, Any>, CordaConsumerBuilder> {
        val consumerMock: CordaConsumer<Any, Any> = mock()
        val cordaConsumerBuilder: CordaConsumerBuilder = mock()
        doReturn(consumerMock).whenever(cordaConsumerBuilder).createConsumer<Any, Any>(any(), any(), any(), any(), any(), anyOrNull())
        doReturn(mutableMapOf(CordaTopicPartition(config.topic, 0) to 0L, CordaTopicPartition(config.topic, 1) to 0L))
            .whenever(consumerMock).beginningOffsets(any())
        doReturn(
            mutableMapOf(
                CordaTopicPartition(config.topic, 0) to numberOfRecords + 1,
                CordaTopicPartition(config.topic, 1) to 0,
            )
        ).whenever(consumerMock).endOffsets(any())
        doReturn(numberOfRecords + 1).whenever(consumerMock).position(any())
        doReturn(setOf(CordaTopicPartition(config.topic, 0), CordaTopicPartition(config.topic, 1))).whenever(consumerMock)
            .assignment()
        doAnswer(onPoll).whenever(consumerMock).poll(any())
        doAnswer {
            listOf(CordaTopicPartition(config.topic, 0))
        }.whenever(consumerMock).getPartitions(any(), any())

        return Pair(consumerMock, cordaConsumerBuilder)
    }
}
