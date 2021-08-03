package net.corda.messaging.kafka.subscription

import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import com.typesafe.config.Config
import net.corda.messaging.api.exception.CordaMessageAPIIntermittentException
import net.corda.messaging.kafka.producer.wrapper.CordaKafkaProducer
import net.corda.messaging.kafka.properties.KafkaProperties.Companion.PATTERN_STATEANDEVENT
import net.corda.messaging.kafka.properties.KafkaProperties.Companion.TOPIC
import net.corda.messaging.kafka.subscription.consumer.builder.StateAndEventBuilder
import net.corda.messaging.kafka.subscription.consumer.wrapper.ConsumerRecordAndMeta
import net.corda.messaging.kafka.subscription.consumer.wrapper.CordaKafkaConsumer
import net.corda.messaging.kafka.subscription.factory.SubscriptionMapFactory
import net.corda.messaging.kafka.subscription.net.corda.messaging.kafka.TOPIC_PREFIX
import net.corda.messaging.kafka.subscription.net.corda.messaging.kafka.createStandardTestConfig
import net.corda.messaging.kafka.subscription.net.corda.messaging.kafka.stubs.StubStateAndEventProcessor
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.common.TopicPartition
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch

class KafkaStateAndEventSubscriptionImplTest {

    companion object {
        private const val TEST_TIMEOUT_SECONDS = 4L
    }

    private val config: Config = createStandardTestConfig().getConfig(PATTERN_STATEANDEVENT)

    val map = ConcurrentHashMap<String, Pair<Long, String>>()
    private val subscriptionMapFactory = object : SubscriptionMapFactory<String, Pair<Long, String>> {
        override fun createMap(): MutableMap<String, Pair<Long, String>> = map
        override fun destroyMap(map: MutableMap<String, Pair<Long, String>>) {}
    }

    data class Mocks(
        val builder: StateAndEventBuilder<String, String, String>,
        val producer: CordaKafkaProducer,
        val eventConsumer: CordaKafkaConsumer<String, String>,
        val stateConsumer: CordaKafkaConsumer<String, String>,
    )

    private fun setupMocks(iterations: Int, latch: CountDownLatch): Mocks {
        val eventConsumer: CordaKafkaConsumer<String, String> = mock()
        val stateConsumer: CordaKafkaConsumer<String, String> = mock()
        val producer: CordaKafkaProducer = mock()
        val builder: StateAndEventBuilder<String, String, String> = mock()

        val topicPartition = TopicPartition(TOPIC, 0)
        val state = ConsumerRecordAndMeta<String, String>(
            TOPIC_PREFIX,
            ConsumerRecord(TOPIC, 0, 0, "key", "state5")
        )

        doAnswer { eventConsumer }.whenever(builder).createEventConsumer(any(), any(), any(), any())
        doAnswer { stateConsumer }.whenever(builder).createStateConsumer(any(), any(), any())
        doAnswer { producer }.whenever(builder).createProducer(any())
        doAnswer { setOf(topicPartition) }.whenever(stateConsumer).assignment()
        doAnswer { listOf(state) }.whenever(stateConsumer).poll()

        val mockConsumerRecords = generateMockConsumerRecordList(iterations, "topic", 0)
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

        return Mocks(builder, producer, eventConsumer, stateConsumer)
    }

    private fun generateMockConsumerRecordList(
        numberOfRecords: Int,
        topic: String,
        partition: Int
    ): List<ConsumerRecordAndMeta<String, String>> {
        val records = mutableListOf<ConsumerRecord<String, String>>()
        for (i in 0 until numberOfRecords) {
            val value = "value$i"
            val record = ConsumerRecord(topic, partition, i.toLong(), "key", value)
            records.add(record)
        }
        return records
            .map { ConsumerRecordAndMeta("", it) }
    }

    @Test
    @Timeout(TEST_TIMEOUT_SECONDS)
    fun `state and event subscription aetries`() {
        val iterations = 5
        val latch = CountDownLatch(iterations)
        val (builder, producer, eventConsumer, stateConsumer) = setupMocks(iterations, latch)
        val processor = StubStateAndEventProcessor(latch, CordaMessageAPIIntermittentException("Test exception"))
        val subscription = KafkaStateAndEventSubscriptionImpl(
            config,
            subscriptionMapFactory,
            builder,
            processor
        )

        subscription.start()
        while (subscription.isRunning) { Thread.sleep(10) }
        assertThat(latch.count).isEqualTo(0)

        verify(builder, times(1)).createEventConsumer(any(), any(), any(), any())
        verify(builder, times(1)).createStateConsumer(any(), any(), any())
        verify(builder, times(1)).createProducer(any())
        verify(stateConsumer, times(6)).poll()
        verify(eventConsumer, times(7)).poll()
        verify(producer, times(5)).beginTransaction()
        verify(producer, times(5)).sendRecords(any())
        verify(producer, times(5)).sendRecordOffsetToTransaction(any(), any())
        verify(producer, times(5)).tryCommitTransaction()

        assertThat(processor.inputs.size).isEqualTo(iterations)
        for (i in 0 until iterations) {
            // The list is made counting down so we need our expectations to count down too
            val countValue = iterations - i - 1
            val input = processor.inputs[i]
            assertThat(input.first).isEqualTo("state$countValue")
            assertThat(input.second.key).isEqualTo("key")
            assertThat(input.second.value).isEqualTo("value$countValue")
        }
    }

    @Test
    @Timeout(TEST_TIMEOUT_SECONDS)
    fun `state and event subscription processes correct state after event`() {
        val iterations = 5
        val latch = CountDownLatch(iterations)
        val (builder, producer, eventConsumer, stateConsumer) = setupMocks(iterations, latch)
        val processor = StubStateAndEventProcessor(latch)
        val subscription = KafkaStateAndEventSubscriptionImpl(
            config,
            subscriptionMapFactory,
            builder,
            processor
        )

        subscription.start()
        while (subscription.isRunning) { Thread.sleep(10) }
        assertThat(latch.count).isEqualTo(0)

        verify(builder, times(1)).createEventConsumer(any(), any(), any(), any())
        verify(builder, times(1)).createStateConsumer(any(), any(), any())
        verify(builder, times(1)).createProducer(any())
        verify(stateConsumer, times(6)).poll()
        verify(eventConsumer, times(6)).poll()
        verify(producer, times(5)).beginTransaction()
        verify(producer, times(5)).sendRecords(any())
        verify(producer, times(5)).sendRecordOffsetToTransaction(any(), any())
        verify(producer, times(5)).tryCommitTransaction()

        assertThat(processor.inputs.size).isEqualTo(iterations)
        for (i in 0 until iterations) {
            // The list is made counting down so we need our expectations to count down too
            val countValue = iterations - i - 1
            val input = processor.inputs[i]
            assertThat(input.first).isEqualTo("state$countValue")
            assertThat(input.second.key).isEqualTo("key")
            assertThat(input.second.value).isEqualTo("value$countValue")
        }
    }



}
