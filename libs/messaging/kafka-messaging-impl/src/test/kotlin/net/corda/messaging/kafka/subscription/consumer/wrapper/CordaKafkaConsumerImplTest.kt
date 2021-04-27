package net.corda.messaging.kafka.subscription.net.corda.messaging.kafka.subscription.consumer.wrapper

import com.nhaarman.mockito_kotlin.*
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory
import net.corda.messaging.api.exception.CordaMessageAPIFatalException
import net.corda.messaging.api.subscription.factory.config.SubscriptionConfig
import net.corda.messaging.kafka.properties.KafkaProperties
import net.corda.messaging.kafka.subscription.consumer.wrapper.CordaKafkaConsumer
import net.corda.messaging.kafka.subscription.consumer.wrapper.impl.CordaKafkaConsumerImpl
import net.corda.messaging.kafka.subscription.createMockConsumerAndAddRecords
import net.corda.messaging.kafka.subscription.generateMockConsumerRecordsList
import org.apache.kafka.clients.consumer.*
import org.apache.kafka.common.KafkaException
import org.apache.kafka.common.TopicPartition
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatExceptionOfType
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import java.time.Duration

class CordaKafkaConsumerImplTest {
    private lateinit var cordaKafkaConsumer : CordaKafkaConsumer<String, ByteArray>
    private lateinit var kafkaConfig : Config
    private lateinit var subscriptionConfig : SubscriptionConfig
    private val listener : ConsumerRebalanceListener = mock()
    private val eventTopic = "eventTopic1"
    private val numberOfRecords = 10L
    private lateinit var consumer: MockConsumer<String, ByteArray>
    private lateinit var partition: TopicPartition

    @BeforeEach
    fun beforeEach() {
        subscriptionConfig = SubscriptionConfig("groupName1", eventTopic )
        kafkaConfig = ConfigFactory.empty()
            .withValue(KafkaProperties.CONSUMER_POLL_TIMEOUT, ConfigValueFactory.fromAnyRef(1))
            .withValue(KafkaProperties.CONSUMER_CLOSE_TIMEOUT, ConfigValueFactory.fromAnyRef(1))
            .withValue(KafkaProperties.CONSUMER_CREATE_MAX_RETRIES, ConfigValueFactory.fromAnyRef(3))
            .withValue(KafkaProperties.KAFKA_TOPIC_PREFIX, ConfigValueFactory.fromAnyRef("prefix"))

        val (mockConsumer, mockTopicPartition) = createMockConsumerAndAddRecords(eventTopic,  numberOfRecords, OffsetResetStrategy.EARLIEST)
        consumer = mockConsumer
        partition = mockTopicPartition
        cordaKafkaConsumer = CordaKafkaConsumerImpl(kafkaConfig, subscriptionConfig, consumer, listener)
    }

    @Test
    fun testPollInvoked() {
        val consumerRecords = generateMockConsumerRecordsList(2, eventTopic, 1)

        consumer = mock()
        doReturn(consumerRecords).whenever(consumer).poll(Mockito.any(Duration::class.java))
        cordaKafkaConsumer = CordaKafkaConsumerImpl(kafkaConfig, subscriptionConfig, consumer, listener)

        cordaKafkaConsumer.poll()
        verify(consumer, times(1)).poll(Mockito.any(Duration::class.java))
    }

    @Test
    fun testGetRecord() {
        val consumerRecord = ConsumerRecord("prefixtopic", 1, 1, "key", "value".toByteArray())
        val record = cordaKafkaConsumer.getRecord(consumerRecord)
        assertThat(record.topic).isEqualTo("topic")
        assertThat(record.key).isEqualTo(consumerRecord.key())
        assertThat(record.value).isEqualTo(consumerRecord.value())
    }

    @Test
    fun testCommitOffsets() {
        val record = ConsumerRecord<String, ByteArray>(eventTopic, 1, 5L, null, "value".toByteArray())
        assertThat(consumer.committed(setOf(partition))).isEmpty()

        cordaKafkaConsumer.commitSyncOffsets(record, "meta data")

        val committedPositionAfterCommit = consumer.committed(setOf(partition))
        assertThat(committedPositionAfterCommit.values.first().offset()).isEqualTo(6)
    }


    @Test
    fun testSubscribeToTopic() {
        consumer = mock()
        cordaKafkaConsumer = CordaKafkaConsumerImpl(kafkaConfig, subscriptionConfig, consumer, listener)
        cordaKafkaConsumer.subscribeToTopic()
        verify(consumer, times(1)).subscribe(Mockito.anyList(), Mockito.any(ConsumerRebalanceListener::class.java))
    }

    @Test
    fun testSubscribeToTopicRetries() {
        consumer = mock()
        cordaKafkaConsumer = CordaKafkaConsumerImpl(kafkaConfig, subscriptionConfig, consumer, listener)
        doThrow(KafkaException()).whenever(consumer).subscribe(Mockito.anyList(), Mockito.any(ConsumerRebalanceListener::class.java))
        assertThatExceptionOfType(CordaMessageAPIFatalException::class.java).isThrownBy {
            cordaKafkaConsumer.subscribeToTopic()
        }
        verify(consumer, times(3)).subscribe(Mockito.anyList(), Mockito.any(ConsumerRebalanceListener::class.java))
    }

    @Test
    fun testResetToLastCommittedPositions() {
        val positionBeforePoll = consumer.position(partition)
        assertThat(positionBeforePoll).isEqualTo(0)
        consumer.poll(Duration.ZERO)

        //get current position after poll for this partition/topic
        val positionAfterPoll = consumer.position(partition)
        assertThat(positionAfterPoll).isEqualTo(numberOfRecords)

        //Commit offset for half the records = offset of 5.
        val currentOffsets = mutableMapOf<TopicPartition, OffsetAndMetadata>()
        val offsetCommit = 5L
        currentOffsets[partition] = OffsetAndMetadata(offsetCommit, "metaData")
        consumer.commitSync(currentOffsets)
        val positionAfterCommit = consumer.position(partition)
        assertThat(positionAfterCommit).isEqualTo(numberOfRecords)

        //Reset fetch position to the last committed record
        cordaKafkaConsumer.resetToLastCommittedPositions(OffsetResetStrategy.NONE)

        val positionAfterReset = consumer.position(partition)
        assertThat(positionAfterReset).isEqualTo(offsetCommit)
    }
}