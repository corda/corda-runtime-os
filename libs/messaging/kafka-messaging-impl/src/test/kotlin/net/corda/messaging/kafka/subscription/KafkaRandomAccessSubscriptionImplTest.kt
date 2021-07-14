package net.corda.messaging.kafka.subscription.net.corda.messaging.kafka.subscription

import com.nhaarman.mockito_kotlin.anyOrNull
import com.typesafe.config.ConfigValueFactory
import net.corda.messaging.api.exception.CordaMessageAPIFatalException
import net.corda.messaging.kafka.properties.KafkaProperties
import net.corda.messaging.kafka.properties.KafkaProperties.Companion.PATTERN_RANDOMACCESS
import net.corda.messaging.kafka.subscription.KafkaRandomAccessSubscriptionImpl
import net.corda.messaging.kafka.subscription.consumer.builder.ConsumerBuilder
import net.corda.messaging.kafka.subscription.consumer.wrapper.ConsumerRecordAndMeta
import net.corda.messaging.kafka.subscription.consumer.wrapper.CordaKafkaConsumer
import net.corda.messaging.kafka.subscription.net.corda.messaging.kafka.createStandardTestConfig
import net.corda.v5.base.util.seconds
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.common.TopicPartition
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.mockito.Mockito.times
import org.mockito.Mockito.verify

class KafkaRandomAccessSubscriptionImplTest {

    private val topic = "test.topic"
    private val partitions = 10

    private val config = createStandardTestConfig().getConfig(PATTERN_RANDOMACCESS)
        .withValue(KafkaProperties.TOPIC_NAME, ConfigValueFactory.fromAnyRef(topic))
    private val consumer = mock(CordaKafkaConsumer::class.java).apply {
        val topicPartitions = (1..partitions).map { TopicPartition(topic, it) }
        `when`(getPartitions(topic, 5.seconds)).thenReturn(topicPartitions)
    }
    @Suppress("UNCHECKED_CAST")
    private val consumerBuilder = mock(ConsumerBuilder::class.java).apply {
        `when`(createDurableConsumer(anyOrNull(), anyOrNull(), anyOrNull())).thenReturn(consumer)
    } as ConsumerBuilder<String, String>

    private lateinit var randomAccessSubscription: KafkaRandomAccessSubscriptionImpl<String, String>

    @BeforeEach
    fun setup() {
        randomAccessSubscription = KafkaRandomAccessSubscriptionImpl(config, consumerBuilder)
        randomAccessSubscription.start()
        randomAccessSubscription.stop()
    }

    @Test
    fun `all partitions are assigned manually during startup`() {
        verify(consumer, times(1)).assignPartitionsManually((1..partitions).toSet())
    }

    @Test
    fun `when poll finds the record, subscription returns it to the user`() {
        val recordReturnedFromPoll = ConsumerRecordAndMeta("", ConsumerRecord(topic, 1, 4, "key", "value"))
        `when`(consumer.poll()).thenReturn(listOf(recordReturnedFromPoll))

        val record = randomAccessSubscription.getRecord(1, 4)

        assertThat(record).isNotNull
        assertThat(record!!.key).isEqualTo("key")
        assertThat(record.value).isEqualTo("value")
        val resumedPartition = TopicPartition(topic, 1)
        val pausedPartitions = (1..partitions).filter { it != resumedPartition.partition() }.map { TopicPartition(topic, it) }
        verify(consumer, times(1)).pause(pausedPartitions)
        verify(consumer, times(1)).resume(listOf(resumedPartition))
        verify(consumer, times(1)).seek(resumedPartition, 4)
    }

    @Test
    fun `when poll returns other records, a null is returned by subscription`() {
        val recordReturnedFromPoll = ConsumerRecordAndMeta("", ConsumerRecord(topic, 1, 6, "key", "value"))
        `when`(consumer.poll()).thenReturn(listOf(recordReturnedFromPoll))

        val record = randomAccessSubscription.getRecord(1, 4)

        assertThat(record).isNull()
        val resumedPartition = TopicPartition(topic, 1)
        val pausedPartitions = (1..partitions).filter { it != resumedPartition.partition() }.map { TopicPartition(topic, it) }
        verify(consumer, times(1)).pause(pausedPartitions)
        verify(consumer, times(1)).resume(listOf(resumedPartition))
        verify(consumer, times(1)).seek(resumedPartition, 4)
    }

    @Test
    fun `when poll returns no records, a null is returned by subscription`() {
        `when`(consumer.poll()).thenReturn(emptyList())

        val record = randomAccessSubscription.getRecord(1, 4)

        assertThat(record).isNull()
        val resumedPartition = TopicPartition(topic, 1)
        val pausedPartitions = (1..partitions).filter { it != resumedPartition.partition() }.map { TopicPartition(topic, it) }
        verify(consumer, times(1)).pause(pausedPartitions)
        verify(consumer, times(1)).resume(listOf(resumedPartition))
        verify(consumer, times(1)).seek(resumedPartition, 4)
    }

    @Test
    fun `when poll returns the same record multiple times, subscription throws an error`() {
        val recordReturnedFromPoll = ConsumerRecordAndMeta("", ConsumerRecord(topic, 1, 4, "key", "value"))
        `when`(consumer.poll()).thenReturn(listOf(recordReturnedFromPoll, recordReturnedFromPoll))

        assertThatThrownBy { randomAccessSubscription.getRecord(1, 4) }
            .isInstanceOf(CordaMessageAPIFatalException::class.java)
            .hasMessageContaining("Multiple records located")

        val resumedPartition = TopicPartition(topic, 1)
        val pausedPartitions = (1..partitions).filter { it != resumedPartition.partition() }.map { TopicPartition(topic, it) }
        verify(consumer, times(1)).pause(pausedPartitions)
        verify(consumer, times(1)).resume(listOf(resumedPartition))
        verify(consumer, times(1)).seek(resumedPartition, 4)
    }

}