package net.corda.messaging.kafka.subscription

import com.typesafe.config.Config
import com.typesafe.config.ConfigValueFactory
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.messaging.api.exception.CordaMessageAPIIntermittentException
import net.corda.messaging.api.processor.StateAndEventProcessor
import net.corda.messaging.kafka.producer.wrapper.CordaKafkaProducer
import net.corda.messaging.kafka.properties.ConfigProperties.Companion.CONSUMER_MAX_POLL_INTERVAL
import net.corda.messaging.kafka.properties.ConfigProperties.Companion.CONSUMER_PROCESSOR_TIMEOUT
import net.corda.messaging.kafka.properties.ConfigProperties.Companion.PATTERN_STATEANDEVENT
import net.corda.messaging.kafka.subscription.consumer.builder.StateAndEventBuilder
import net.corda.messaging.kafka.subscription.consumer.wrapper.CordaKafkaConsumer
import net.corda.messaging.kafka.subscription.consumer.wrapper.StateAndEventConsumer
import net.corda.messaging.kafka.subscription.net.corda.messaging.kafka.TOPIC_PREFIX
import net.corda.messaging.kafka.subscription.net.corda.messaging.kafka.createStandardTestConfig
import net.corda.messaging.kafka.types.StateAndEventConfig.Companion.getStateAndEventConfig
import net.corda.schema.registry.AvroSchemaRegistry
import org.apache.kafka.clients.consumer.ConsumerRebalanceListener
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.common.TopicPartition
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch

class KafkaStateAndEventSubscriptionImplTest {

    companion object {
        private const val TEST_TIMEOUT_SECONDS = 20L
        private const val TOPIC = "topic"
    }

    private val config: Config = createStandardTestConfig().getConfig(PATTERN_STATEANDEVENT)
    private val avroSchemaRegistry: AvroSchemaRegistry = mock()
    private val lifecycleCoordinatorFactory: LifecycleCoordinatorFactory = mock()
    private val lifecycleCoordinator: LifecycleCoordinator = mock()
    private val stateAndEventConfig = getStateAndEventConfig(config)

    data class Mocks(
        val builder: StateAndEventBuilder<String, String, String>,
        val producer: CordaKafkaProducer,
        val stateAndEventConsumer: StateAndEventConsumer<String, String, String>,
    )

    private fun setupMocks(iterations: Long): Mocks {
        val latch = CountDownLatch(iterations.toInt() + 1)
        val stateAndEventConsumer: StateAndEventConsumer<String, String, String> = mock()
        val rebalanceListener: ConsumerRebalanceListener = mock()
        val eventConsumer: CordaKafkaConsumer<String, String> = mock()
        val stateConsumer: CordaKafkaConsumer<String, String> = mock()
        val producer: CordaKafkaProducer = mock()
        val builder: StateAndEventBuilder<String, String, String> = mock()

        val topicPartition = TopicPartition(TOPIC, 0)
        val state = ConsumerRecord(TOPIC_PREFIX + TOPIC, 0, 0, "key", "state5")

        doAnswer {
            CompletableFuture.completedFuture(
                StateAndEventProcessor.Response(
                    "newstate",
                    emptyList()
                )
            )
        }.whenever(
            stateAndEventConsumer
        ).waitForFunctionToFinish(any(), any(), any())
        doAnswer { eventConsumer }.whenever(stateAndEventConsumer).eventConsumer
        doAnswer { stateConsumer }.whenever(stateAndEventConsumer).stateConsumer
        doAnswer { producer }.whenever(builder).createProducer(any())
        doAnswer { setOf(topicPartition) }.whenever(stateConsumer).assignment()
        doAnswer { listOf(state) }.whenever(stateConsumer).poll()
        doAnswer { Pair(stateAndEventConsumer, rebalanceListener) }.whenever(builder)
            .createStateEventConsumerAndRebalanceListener(any(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull())

        val mockConsumerRecords = generateMockConsumerRecordList(iterations, TOPIC, 0)
        var eventsPaused = false


        doAnswer { eventsPaused = true }.whenever(eventConsumer).pause(any())
        doAnswer { eventsPaused = false }.whenever(eventConsumer).resume(any())
        doAnswer {
            if (eventsPaused) {
                emptyList()
            } else {
                latch.countDown()
                listOf(mockConsumerRecords[latch.count.toInt() - 1])
            }
        }.whenever(eventConsumer).poll()

        doReturn(lifecycleCoordinator).`when`(lifecycleCoordinatorFactory).createCoordinator(any(), any())

        return Mocks(builder, producer, stateAndEventConsumer)
    }

    @Test
    @Timeout(TEST_TIMEOUT_SECONDS)
    fun `state and event subscription retries`() {
        val (builder, producer, stateAndEventConsumer) = setupMocks(5)

        var exceptionThrown = false
        doAnswer {
            if (!exceptionThrown) {
                exceptionThrown = true
                throw CordaMessageAPIIntermittentException("test")
            } else {
                CompletableFuture.completedFuture(null)
            }
        }.whenever(stateAndEventConsumer).waitForFunctionToFinish(any(), any(), any())

        val subscription = KafkaStateAndEventSubscriptionImpl(
            stateAndEventConfig,
            builder,
            mock(),
            avroSchemaRegistry,
            lifecycleCoordinatorFactory
        )

        subscription.start()
        while (subscription.isRunning) {
            Thread.sleep(10)
        }

        val eventConsumer = stateAndEventConsumer.eventConsumer
        verify(builder, times(1)).createStateEventConsumerAndRebalanceListener(
            any(),
            anyOrNull(),
            anyOrNull(),
            anyOrNull(),
            anyOrNull()
        )
        verify(builder, times(1)).createProducer(any())
        verify(eventConsumer, times(6)).poll()
        verify(producer, times(4)).beginTransaction()
        verify(producer, times(4)).sendRecords(any())
        verify(producer, times(4)).sendRecordOffsetsToTransaction(any(), any())
        verify(producer, times(4)).commitTransaction()
    }

    @Test
    @Timeout(TEST_TIMEOUT_SECONDS)
    fun `state and event subscription no retries`() {
        val (builder, producer, stateAndEventConsumer) = setupMocks(5)
        val subscription = KafkaStateAndEventSubscriptionImpl(
            stateAndEventConfig,
            builder,
            mock(),
            avroSchemaRegistry,
            lifecycleCoordinatorFactory
        )

        subscription.start()
        while (subscription.isRunning) {
            Thread.sleep(10)
        }

        val eventConsumer = stateAndEventConsumer.eventConsumer

        verify(builder, times(1)).createStateEventConsumerAndRebalanceListener(
            any(),
            anyOrNull(),
            anyOrNull(),
            anyOrNull(),
            anyOrNull()
        )
        verify(builder, times(1)).createProducer(any())
        verify(eventConsumer, times(6)).poll()
        verify(producer, times(5)).beginTransaction()
        verify(producer, times(5)).sendRecords(any())
        verify(producer, times(5)).sendRecordOffsetsToTransaction(any(), any())
        verify(producer, times(5)).commitTransaction()

    }

    @Test
    fun `state and event subscription processes multiples events by key, small batches`() {
        val (builder, producer, stateAndEventConsumer) = setupMocks(0)
        val records = mutableListOf<ConsumerRecord<String, String>>()
        var offset = 0
        for (i in 0 until 3) {
            for (j in 0 until 10) {
                records.add(ConsumerRecord(TOPIC, 1, offset.toLong(), "key$i", "value$j"))
                offset++
            }
        }

        var eventsPaused = false
        val eventConsumer = stateAndEventConsumer.eventConsumer
        doAnswer {
            if (eventsPaused) {
                emptyList()
            } else {
                eventsPaused = true
                records
            }
        }.whenever(eventConsumer).poll()
        val subscription = KafkaStateAndEventSubscriptionImpl(
            stateAndEventConfig,
            builder,
            mock(),
            avroSchemaRegistry,
            lifecycleCoordinatorFactory
        )

        subscription.start()
        while (subscription.isRunning && !eventsPaused) {
            Thread.sleep(10)
        }
        subscription.stop()

        verify(builder, times(1)).createStateEventConsumerAndRebalanceListener(
            any(),
            anyOrNull(),
            anyOrNull(),
            anyOrNull(),
            anyOrNull()
        )
        verify(builder, times(1)).createProducer(any())
        verify(producer, times(28)).beginTransaction()
        verify(producer, times(28)).sendRecords(any())
        verify(producer, times(28)).sendRecordOffsetsToTransaction(any(), any())
        verify(producer, times(28)).commitTransaction()
    }

    @Test
    fun `state and event subscription processes multiples events by key, large batches`() {
        val (builder, producer, stateAndEventConsumer) = setupMocks(0)
        val records = mutableListOf<ConsumerRecord<String, String>>()
        var offset = 0
        for (j in 0 until 3) {
            for (i in 0 until 10) {
                records.add(ConsumerRecord(TOPIC, 1, offset.toLong(), "key$i", "value$j"))
                offset++
            }
        }

        var eventsPaused = false
        val eventConsumer = stateAndEventConsumer.eventConsumer
        doAnswer {
            if (eventsPaused) {
                emptyList()
            } else {
                eventsPaused = true
                records
            }
        }.whenever(eventConsumer).poll()

        val subscription = KafkaStateAndEventSubscriptionImpl(
            stateAndEventConfig,
            builder,
            mock(),
            avroSchemaRegistry,
            lifecycleCoordinatorFactory
        )

        subscription.start()
        while (subscription.isRunning && !eventsPaused) {
            Thread.sleep(10)
        }
        subscription.stop()

        verify(builder, times(1)).createStateEventConsumerAndRebalanceListener(
            any(),
            anyOrNull(),
            anyOrNull(),
            anyOrNull(),
            anyOrNull()
        )
        verify(builder, times(1)).createProducer(any())
        verify(producer, times(3)).beginTransaction()
        verify(producer, times(3)).sendRecords(any())
        verify(producer, times(3)).sendRecordOffsetsToTransaction(any(), any())
        verify(producer, times(3)).commitTransaction()
    }

    @Test
    fun `state and event subscription verify dead letter`() {
        val (builder, producer, stateAndEventConsumer) = setupMocks(0)
        val records = mutableListOf<ConsumerRecord<String, String>>()
        records.add(ConsumerRecord(TOPIC, 1, 1, "key1", "value1"))

        var eventsPaused = false
        val eventConsumer = stateAndEventConsumer.eventConsumer
        doAnswer {
            if (eventsPaused) {
                emptyList()
            } else {
                eventsPaused = true
                records
            }
        }.whenever(eventConsumer).poll()

        //null response from waitForFunctionToFinish indicates slow function exceeded timeout
        doAnswer {
            CompletableFuture.completedFuture(null)
        }.whenever(stateAndEventConsumer).waitForFunctionToFinish(any(), any(), any())

        val shortWaitProcessorConfig = getStateAndEventConfig(
            config
                .withValue(
                    CONSUMER_MAX_POLL_INTERVAL.replace("consumer", "eventConsumer"),
                    ConfigValueFactory.fromAnyRef(10000)
                )
                .withValue(
                    CONSUMER_PROCESSOR_TIMEOUT.replace("consumer", "eventConsumer"),
                    ConfigValueFactory.fromAnyRef(100)
                )
        )

        val subscription = KafkaStateAndEventSubscriptionImpl(
            shortWaitProcessorConfig,
            builder,
            mock(),
            avroSchemaRegistry,
            lifecycleCoordinatorFactory
        )

        subscription.start()
        while (subscription.isRunning && !eventsPaused) {
            Thread.sleep(10)
        }
        subscription.stop()

        verify(builder, times(1)).createStateEventConsumerAndRebalanceListener(
            any(), anyOrNull(),
            anyOrNull(), anyOrNull(), anyOrNull()
        )
        verify(builder, times(1)).createProducer(any())
        verify(producer, times(1)).beginTransaction()
        verify(producer, times(1)).sendRecords(any())
        verify(producer, times(1)).sendRecordOffsetsToTransaction(any(), any())
        verify(producer, times(1)).commitTransaction()
    }
}
