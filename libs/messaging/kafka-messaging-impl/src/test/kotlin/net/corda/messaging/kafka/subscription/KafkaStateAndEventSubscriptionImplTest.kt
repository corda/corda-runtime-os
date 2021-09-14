package net.corda.messaging.kafka.subscription

import com.typesafe.config.Config
import net.corda.messaging.api.exception.CordaMessageAPIIntermittentException
import net.corda.messaging.kafka.producer.wrapper.CordaKafkaProducer
import net.corda.messaging.kafka.properties.ConfigProperties.Companion.PATTERN_STATEANDEVENT
import net.corda.messaging.kafka.subscription.consumer.builder.StateAndEventBuilder
import net.corda.messaging.kafka.subscription.consumer.wrapper.ConsumerRecordAndMeta
import net.corda.messaging.kafka.subscription.consumer.wrapper.CordaKafkaConsumer
import net.corda.messaging.kafka.subscription.consumer.wrapper.StateAndEventConsumer
import net.corda.messaging.kafka.subscription.net.corda.messaging.kafka.TOPIC_PREFIX
import net.corda.messaging.kafka.subscription.net.corda.messaging.kafka.createStandardTestConfig
import net.corda.messaging.kafka.subscription.net.corda.messaging.kafka.stubs.StubStateAndEventProcessor
import org.apache.kafka.clients.consumer.ConsumerRebalanceListener
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.common.TopicPartition
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit.SECONDS

class KafkaStateAndEventSubscriptionImplTest {

    companion object {
        private const val TEST_TIMEOUT_SECONDS = 20L
        private const val TOPIC = "topic"
    }

    private val config: Config = createStandardTestConfig().getConfig(PATTERN_STATEANDEVENT)

    data class Mocks(
        val builder: StateAndEventBuilder<String, String, ByteBuffer>,
        val producer: CordaKafkaProducer,
        val stateAndEventConsumer: StateAndEventConsumer<String, ByteBuffer, String>,
    )

    private fun setupMocks(iterations: Long, latch: CountDownLatch): Mocks {
        val stateAndEventConsumer: StateAndEventConsumer<String, ByteBuffer, String> = mock()
        val rebalanceListener: ConsumerRebalanceListener = mock()
        val eventConsumer: CordaKafkaConsumer<String, ByteBuffer> = mock()
        val stateConsumer: CordaKafkaConsumer<String, String> = mock()
        val producer: CordaKafkaProducer = mock()
        val builder: StateAndEventBuilder<String, String, ByteBuffer> = mock()

        val topicPartition = TopicPartition(TOPIC, 0)
        val state = ConsumerRecordAndMeta<String, String>(
            TOPIC_PREFIX,
            ConsumerRecord(TOPIC, 0, 0, "key", "state5")
        )

        doAnswer { eventConsumer }.whenever(stateAndEventConsumer).eventConsumer
        doAnswer { stateConsumer }.whenever(stateAndEventConsumer).stateConsumer
        doAnswer { producer }.whenever(builder).createProducer(any())
        doAnswer { setOf(topicPartition) }.whenever(stateConsumer).assignment()
        doAnswer { listOf(state) }.whenever(stateConsumer).poll()
        doAnswer { Pair(stateAndEventConsumer, rebalanceListener) }.whenever(builder)
            .createStateEventConsumerAndRebalanceListener(any(), any(), any(), any(), anyOrNull())

        val mockConsumerRecords = generateMockConsumerRecordAndMetaList(iterations, TOPIC, 0)
        var eventsPaused = false


        doAnswer { eventsPaused = true }.whenever(eventConsumer).pause(any())
        doAnswer { eventsPaused = false }.whenever(eventConsumer).resume(any())
        doAnswer {
            if (eventsPaused) {
                emptyList()
            } else {
                listOf(mockConsumerRecords[latch.count.toInt() - 1])
            }
        }.whenever(eventConsumer).poll()

        return Mocks(builder, producer, stateAndEventConsumer)
    }

    @Test
    @Timeout(TEST_TIMEOUT_SECONDS)
    fun `state and event subscription retries`() {
        val iterations = 5
        val latch = CountDownLatch(iterations)
        val (builder, producer, stateAndEventConsumer) = setupMocks(iterations.toLong(), latch)
        val processor = StubStateAndEventProcessor(latch, CordaMessageAPIIntermittentException("Test exception"))
        val subscription = KafkaStateAndEventSubscriptionImpl(
            config,
            builder,
            processor
        )

        subscription.start()
        while (subscription.isRunning) {
            Thread.sleep(10)
        }
        assertThat(latch.count).isEqualTo(0)

        val eventConsumer = stateAndEventConsumer.eventConsumer
        verify(builder, times(1)).createStateEventConsumerAndRebalanceListener(any(), any(), any(), any(), anyOrNull())
        verify(builder, times(1)).createProducer(any())
        verify(eventConsumer, times(7)).poll()
        verify(producer, times(5)).beginTransaction()
        verify(producer, times(5)).sendRecords(any())
        verify(producer, times(5)).sendRecordOffsetsToTransaction(any(), any())
        verify(producer, times(5)).tryCommitTransaction()

        assertThat(processor.inputs.size).isEqualTo(iterations)
        for (i in 0 until iterations) {
            // The list is made counting down so we need our expectations to count down too
            val countValue = iterations - i - 1
            val input = processor.inputs[i]
            assertThat(input.first).isEqualTo("state$countValue")
            assertThat(input.second.key).isEqualTo("key$countValue")
            assertThat((StandardCharsets.UTF_8.decode(input.second.value)).toString()).isEqualTo("value$countValue")
        }
    }

    @Test
    @Timeout(TEST_TIMEOUT_SECONDS)
    fun `state and event subscription processes correct state after event`() {
        val iterations = 5
        val latch = CountDownLatch(iterations)
        val (builder, producer, stateAndEventConsumer) = setupMocks(iterations.toLong(), latch)
        val processor = StubStateAndEventProcessor(latch)
        val subscription = KafkaStateAndEventSubscriptionImpl(
            config,
            builder,
            processor
        )

        subscription.start()
        while (subscription.isRunning) {
            Thread.sleep(10)
        }
        assertThat(latch.count).isEqualTo(0)

        val eventConsumer = stateAndEventConsumer.eventConsumer

        verify(builder, times(1)).createStateEventConsumerAndRebalanceListener(any(), any(), any(), any(), anyOrNull())
        verify(builder, times(1)).createProducer(any())
        verify(eventConsumer, times(6)).poll()
        verify(producer, times(5)).beginTransaction()
        verify(producer, times(5)).sendRecords(any())
        verify(producer, times(5)).sendRecordOffsetsToTransaction(any(), any())
        verify(producer, times(5)).tryCommitTransaction()

        assertThat(processor.inputs.size).isEqualTo(iterations)
        for (i in 0 until iterations) {
            // The list is made counting down so we need our expectations to count down too
            val countValue = iterations - i - 1
            val input = processor.inputs[i]
            assertThat(input.first).isEqualTo("state$countValue")
            assertThat(input.second.key).isEqualTo("key$countValue")
            assertThat((StandardCharsets.UTF_8.decode(input.second.value)).toString()).isEqualTo("value$countValue")
        }
    }


    @Test
    fun `state and event subscription processes multiples events by key, small batches`() {
        val latch = CountDownLatch(30)
        val (builder, producer, stateAndEventConsumer) = setupMocks(0, latch)
        val records = mutableListOf<ConsumerRecordAndMeta<String, String>>()
        var offset = 0
        for (i in 0 until 3) {
            for (j in 0 until 10) {
                records.add(ConsumerRecordAndMeta("", ConsumerRecord(TOPIC, 1, offset.toLong(), "key$i", "value$j")))
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

        val processor = StubStateAndEventProcessor(latch)
        val subscription = KafkaStateAndEventSubscriptionImpl(
            config,
            builder,
            processor
        )

        subscription.start()
        assertTrue(latch.await(TEST_TIMEOUT_SECONDS, SECONDS))
        subscription.stop()

        verify(builder, times(1)).createStateEventConsumerAndRebalanceListener(any(), any(), any(), any(), anyOrNull())
        verify(builder, times(1)).createProducer(any())
        verify(producer, times(28)).beginTransaction()
        verify(producer, times(28)).sendRecords(any())
        verify(producer, times(28)).sendRecordOffsetsToTransaction(any(), any())
        verify(producer, times(28)).tryCommitTransaction()

        assertThat(processor.inputs.size).isEqualTo(30)
    }


    @Test
    fun `state and event subscription processes multiples events by key, large batches`() {
        val latch = CountDownLatch(30)
        val (builder, producer, stateAndEventConsumer) = setupMocks(0, latch)
        val records = mutableListOf<ConsumerRecordAndMeta<String, String>>()
        var offset = 0
        for (j in 0 until 3) {
            for (i in 0 until 10) {
                records.add(ConsumerRecordAndMeta("", ConsumerRecord(TOPIC, 1, offset.toLong(), "key$i", "value$j")))
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

        val processor = StubStateAndEventProcessor(latch)
        val subscription = KafkaStateAndEventSubscriptionImpl(
            config,
            builder,
            processor
        )

        subscription.start()
        assertTrue(latch.await(TEST_TIMEOUT_SECONDS, SECONDS))
        subscription.stop()

        verify(builder, times(1)).createStateEventConsumerAndRebalanceListener(any(), any(), any(), any(), anyOrNull())
        verify(builder, times(1)).createProducer(any())
        verify(producer, times(3)).beginTransaction()
        verify(producer, times(3)).sendRecords(any())
        verify(producer, times(3)).sendRecordOffsetsToTransaction(any(), any())
        verify(producer, times(3)).tryCommitTransaction()

        assertThat(processor.inputs.size).isEqualTo(30)
    }
}
