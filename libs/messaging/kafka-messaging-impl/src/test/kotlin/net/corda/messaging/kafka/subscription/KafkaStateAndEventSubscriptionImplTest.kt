package net.corda.messaging.kafka.subscription

import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.doAnswer
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.times
import com.nhaarman.mockito_kotlin.verify
import com.nhaarman.mockito_kotlin.whenever
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory
import net.corda.messaging.api.subscription.factory.config.StateAndEventSubscriptionConfig
import net.corda.messaging.kafka.producer.builder.ProducerBuilder
import net.corda.messaging.kafka.producer.wrapper.CordaKafkaProducer
import net.corda.messaging.kafka.properties.KafkaProperties
import net.corda.messaging.kafka.properties.PublisherConfigProperties
import net.corda.messaging.kafka.subscription.consumer.builder.StateAndEventConsumerBuilder
import net.corda.messaging.kafka.subscription.consumer.wrapper.ConsumerRecordAndMeta
import net.corda.messaging.kafka.subscription.consumer.wrapper.CordaKafkaConsumer
import net.corda.messaging.kafka.subscription.factory.SubscriptionMapFactory
import net.corda.messaging.kafka.subscription.net.corda.messaging.kafka.stubs.StubStateAndEventProcessor
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.common.TopicPartition
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class KafkaStateAndEventSubscriptionImplTest {

    companion object {
        private const val TEST_TIMEOUT_SECONDS = 1L
        private const val TOPIC_PREFIX = "test"
        private const val TOPIC = "topic"
        private const val CONSUMER_POLL_AND_PROCESS_RETRIES_COUNT = 2
    }

    private val consumerBuilder: StateAndEventConsumerBuilder<String, String, String> = mock()
    private val producerBuilder: ProducerBuilder = mock()
    private val eventConsumer: CordaKafkaConsumer<String, String> = mock()
    private val stateConsumer: CordaKafkaConsumer<String, String> = mock()
    private val producer: CordaKafkaProducer = mock()

    private val config: Config = ConfigFactory.empty()
        .withValue(KafkaProperties.CONSUMER_THREAD_STOP_TIMEOUT, ConfigValueFactory.fromAnyRef(1000))
        .withValue(KafkaProperties.KAFKA_TOPIC_PREFIX, ConfigValueFactory.fromAnyRef(TOPIC_PREFIX))
        .withValue(KafkaProperties.PRODUCER_CLOSE_TIMEOUT, ConfigValueFactory.fromAnyRef(1))
        .withValue(KafkaProperties.CONSUMER_CLOSE_TIMEOUT, ConfigValueFactory.fromAnyRef(1))
        .withValue(PublisherConfigProperties.PUBLISHER_CLIENT_ID, ConfigValueFactory.fromAnyRef("clientId1"))
        .withValue(
            KafkaProperties.CONSUMER_POLL_AND_PROCESS_RETRIES, ConfigValueFactory.fromAnyRef(
                CONSUMER_POLL_AND_PROCESS_RETRIES_COUNT
            )
        )
        .withValue(KafkaProperties.CONSUMER_THREAD_STOP_TIMEOUT, ConfigValueFactory.fromAnyRef(1000))

    val map = ConcurrentHashMap<String, Pair<Long, String>>()
    private val subscriptionMapFactory = object : SubscriptionMapFactory<String, Pair<Long, String>> {
        override fun createMap(): MutableMap<String, Pair<Long, String>> = map
        override fun destroyMap(map: MutableMap<String, Pair<Long, String>>) {}
    }

    @BeforeEach
    fun setUp() {
        doAnswer { eventConsumer }.whenever(consumerBuilder).createEventConsumer(any())
        doAnswer { stateConsumer }.whenever(consumerBuilder).createStateConsumer()
        doAnswer { producer }.whenever(producerBuilder).createProducer()
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
    fun `state and event subscription processes correct state after event`() {
        val iterations = 5
        val latch = CountDownLatch(iterations)
        val subscriptionConfig = StateAndEventSubscriptionConfig(
            "group",
            0,
            "states",
            "events",
        )
        val topicPartition = TopicPartition(TOPIC, 0)
        val state = ConsumerRecordAndMeta<String, String>(
            TOPIC_PREFIX,
            ConsumerRecord(TOPIC, 0, 0, "key", "state5")
        )
        val processor = StubStateAndEventProcessor(latch)
        val mockConsumerRecords = generateMockConsumerRecordList(iterations, "topic", 0)
        var eventsPaused = false

        doAnswer { setOf(topicPartition) }.whenever(stateConsumer).assignment()
        doAnswer { listOf(state) }.whenever(stateConsumer).poll()
        doAnswer { eventsPaused = true }.whenever(eventConsumer).pause(any())
        doAnswer { eventsPaused = false }.whenever(eventConsumer).resume(any())
        doAnswer {
            if (eventsPaused) {
                emptyList()
            } else {
                listOf(mockConsumerRecords[latch.count.toInt() - 1])
            }
        }.whenever(eventConsumer).poll()

        val subscription = KafkaStateAndEventSubscriptionImpl(
            subscriptionConfig,
            config,
            subscriptionMapFactory,
            consumerBuilder,
            producerBuilder,
            processor
        )

        subscription.start()
        latch.await(TEST_TIMEOUT_SECONDS, TimeUnit.DAYS)
        subscription.stop()

        verify(consumerBuilder, times(1)).createEventConsumer(any())
        verify(consumerBuilder, times(1)).createStateConsumer()
        verify(producerBuilder, times(1)).createProducer()
        verify(stateConsumer, times(5)).poll()
        verify(eventConsumer, times(5)).poll()
        verify(producer, times(5)).beginTransaction()
        verify(producer, times(5)).sendRecords(any())
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
