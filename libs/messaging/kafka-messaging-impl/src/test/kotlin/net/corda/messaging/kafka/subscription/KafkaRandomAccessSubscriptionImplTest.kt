package net.corda.messaging.kafka.subscription.net.corda.messaging.kafka.subscription

import com.typesafe.config.ConfigValueFactory
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.messaging.api.exception.CordaMessageAPIFatalException
import net.corda.messaging.kafka.properties.ConfigProperties
import net.corda.messaging.kafka.properties.ConfigProperties.Companion.PATTERN_RANDOMACCESS
import net.corda.messaging.kafka.subscription.KafkaRandomAccessSubscriptionImpl
import net.corda.messaging.kafka.subscription.consumer.builder.ConsumerBuilder
import net.corda.messaging.kafka.subscription.consumer.wrapper.CordaKafkaConsumer
import net.corda.messaging.kafka.subscription.net.corda.messaging.kafka.createStandardTestConfig
import net.corda.v5.base.util.seconds
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.common.TopicPartition
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class KafkaRandomAccessSubscriptionImplTest {

    private val topic = "test.topic"
    private val partitions = 10
    private val topicPartitions = (1..partitions).map { TopicPartition(topic, it) }

    private val config = createStandardTestConfig().getConfig(PATTERN_RANDOMACCESS)
        .withValue(ConfigProperties.TOPIC_NAME, ConfigValueFactory.fromAnyRef(topic))
    private val consumer: CordaKafkaConsumer<String, String> = mock()

    @Suppress("UNCHECKED_CAST")
    private val consumerBuilder: ConsumerBuilder<String, String> = mock()
    private val lifecycleCoordinatorFactory: LifecycleCoordinatorFactory = mock()
    private val lifecycleCoordinator: LifecycleCoordinator = mock()

    private lateinit var randomAccessSubscription: KafkaRandomAccessSubscriptionImpl<String, String>

    @BeforeEach
    fun setup() {
        doReturn(topicPartitions).whenever(consumer).getPartitions(topic, 5.seconds)
        doReturn(consumer).whenever(consumerBuilder).createDurableConsumer(any(), any(), any(), any(), anyOrNull())
        doReturn(lifecycleCoordinator).`when`(lifecycleCoordinatorFactory).createCoordinator(any(), any())

        randomAccessSubscription =
            KafkaRandomAccessSubscriptionImpl(
                config,
                consumerBuilder,
                String::class.java,
                String::class.java,
                lifecycleCoordinatorFactory
            )
        randomAccessSubscription.start()
    }

    @Test
    fun `all partitions are assigned manually during startup`() {
        verify(consumer, times(1)).assignPartitionsManually((1..partitions).toSet())
    }

    @Test
    fun `when poll finds the record, subscription returns it to the user`() {
        val recordReturnedFromPoll = ConsumerRecord(topic, 1, 4, "key", "value")
        doReturn(listOf(recordReturnedFromPoll)).whenever(consumer).poll()

        val record = randomAccessSubscription.getRecord(1, 4)

        assertThat(record).isNotNull
        assertThat(record!!.key).isEqualTo("key")
        assertThat(record.value).isEqualTo("value")
        val resumedPartition = TopicPartition(topic, 1)
        val pausedPartitions =
            (1..partitions).filter { it != resumedPartition.partition() }.map { TopicPartition(topic, it) }
        verify(consumer, times(1)).pause(pausedPartitions)
        verify(consumer, times(1)).resume(listOf(resumedPartition))
        verify(consumer, times(1)).seek(resumedPartition, 4)
    }

    @Test
    fun `when poll returns other records, a null is returned by subscription`() {
        val recordReturnedFromPoll = ConsumerRecord(topic, 1, 6, "key", "value")
        doReturn(listOf(recordReturnedFromPoll)).whenever(consumer).poll()

        val record = randomAccessSubscription.getRecord(1, 4)

        assertThat(record).isNull()
        val resumedPartition = TopicPartition(topic, 1)
        val pausedPartitions =
            (1..partitions).filter { it != resumedPartition.partition() }.map { TopicPartition(topic, it) }
        verify(consumer, times(1)).pause(pausedPartitions)
        verify(consumer, times(1)).resume(listOf(resumedPartition))
        verify(consumer, times(1)).seek(resumedPartition, 4)
    }

    @Test
    fun `when poll returns no records, a null is returned by subscription`() {
        doReturn(listOf<ConsumerRecord<String, String>>()).whenever(consumer).poll()

        val record = randomAccessSubscription.getRecord(1, 4)

        assertThat(record).isNull()
        val resumedPartition = TopicPartition(topic, 1)
        val pausedPartitions =
            (1..partitions).filter { it != resumedPartition.partition() }.map { TopicPartition(topic, it) }
        verify(consumer, times(1)).pause(pausedPartitions)
        verify(consumer, times(1)).resume(listOf(resumedPartition))
        verify(consumer, times(1)).seek(resumedPartition, 4)
    }

    @Test
    fun `when poll returns the same record multiple times, subscription throws an error`() {
        val recordReturnedFromPoll = ConsumerRecord(topic, 1, 4, "key", "value")
        doReturn(listOf(recordReturnedFromPoll, recordReturnedFromPoll)).whenever(consumer).poll()

        assertThatThrownBy { randomAccessSubscription.getRecord(1, 4) }
            .isInstanceOf(CordaMessageAPIFatalException::class.java)
            .hasMessageContaining("Multiple records located")

        val resumedPartition = TopicPartition(topic, 1)
        val pausedPartitions =
            (1..partitions).filter { it != resumedPartition.partition() }.map { TopicPartition(topic, it) }
        verify(consumer, times(1)).pause(pausedPartitions)
        verify(consumer, times(1)).resume(listOf(resumedPartition))
        verify(consumer, times(1)).seek(resumedPartition, 4)
    }

    @Test
    fun `subscription throws an error if getRecord is called when it's not started`() {
        randomAccessSubscription.stop()

        assertThatThrownBy { randomAccessSubscription.getRecord(1, 4) }
            .isInstanceOf(IllegalStateException::class.java)
    }

}