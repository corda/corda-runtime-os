package net.corda.messaging.kafka.subscription.consumer.wrapper

import com.typesafe.config.Config
import net.corda.messaging.api.exception.CordaMessageAPIFatalException
import net.corda.messaging.api.exception.CordaMessageAPIIntermittentException
import net.corda.messaging.api.subscription.factory.config.SubscriptionConfig
import net.corda.messaging.kafka.properties.ConfigProperties.Companion.KAFKA_CONSUMER
import net.corda.messaging.kafka.properties.ConfigProperties.Companion.PATTERN_PUBSUB
import net.corda.messaging.kafka.subscription.consumer.wrapper.impl.CordaKafkaConsumerImpl
import net.corda.messaging.kafka.subscription.createMockConsumerAndAddRecords
import net.corda.messaging.kafka.subscription.generateMockConsumerRecords
import net.corda.messaging.kafka.subscription.net.corda.messaging.kafka.createStandardTestConfig
import net.corda.schema.registry.AvroSchemaRegistry
import org.apache.kafka.clients.consumer.CommitFailedException
import org.apache.kafka.clients.consumer.ConsumerRebalanceListener
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.consumer.MockConsumer
import org.apache.kafka.clients.consumer.OffsetAndMetadata
import org.apache.kafka.clients.consumer.OffsetResetStrategy
import org.apache.kafka.common.KafkaException
import org.apache.kafka.common.TopicPartition
import org.apache.kafka.common.errors.FencedInstanceIdException
import org.apache.kafka.common.errors.TimeoutException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatExceptionOfType
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyMap
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Duration

class CordaKafkaConsumerImplTest {
    private lateinit var cordaKafkaConsumer: CordaKafkaConsumer<String, String>
    private lateinit var kafkaConfig: Config
    private lateinit var subscriptionConfig: SubscriptionConfig
    private val listener: ConsumerRebalanceListener = mock()
    private val eventTopic = "eventTopic1"
    private val numberOfRecords = 10L
    private lateinit var consumer: MockConsumer<String, String>
    private lateinit var partition: TopicPartition
    private val avroSchemaRegistry: AvroSchemaRegistry = mock()
    private val consumerRecord = ConsumerRecord("prefixtopic", 1, 1, "key", "value")

    @BeforeEach
    fun beforeEach() {
        doReturn(String::class.java).whenever(avroSchemaRegistry).getClassType(any())
        doReturn(consumerRecord.value()).whenever(avroSchemaRegistry).deserialize(any(), any(), anyOrNull())
        subscriptionConfig = SubscriptionConfig("groupName1", eventTopic)

        kafkaConfig = createStandardTestConfig().getConfig(PATTERN_PUBSUB).getConfig(KAFKA_CONSUMER)

        val (mockConsumer, mockTopicPartition) = createMockConsumerAndAddRecords(
            eventTopic,
            numberOfRecords,
            OffsetResetStrategy.EARLIEST
        )
        consumer = mockConsumer
        partition = mockTopicPartition
        cordaKafkaConsumer = CordaKafkaConsumerImpl(
            kafkaConfig,
            consumer,
            listener
        )
    }

    @Test
    fun testPollInvoked() {
        val consumerRecords = generateMockConsumerRecords(2, eventTopic, 1)

        consumer = mock()
        doReturn(consumerRecords).whenever(consumer).poll(Mockito.any(Duration::class.java))
        cordaKafkaConsumer = CordaKafkaConsumerImpl(
            kafkaConfig,
            consumer,
            listener
        )

        cordaKafkaConsumer.poll()
        verify(consumer, times(1)).poll(Mockito.any(Duration::class.java))
    }

    @Test
    fun testPollWithDurationInvoked() {
        val consumerRecords = generateMockConsumerRecords(2, eventTopic, 1)

        consumer = mock()
        doReturn(consumerRecords).whenever(consumer).poll(Mockito.any(Duration::class.java))
        cordaKafkaConsumer = CordaKafkaConsumerImpl(
            kafkaConfig,
            consumer,
            listener
        )

        cordaKafkaConsumer.poll(Duration.ZERO)
        verify(consumer, times(1)).poll(Duration.ZERO)
    }

    @Test
    fun testPollFatal() {
        consumer = mock()
        cordaKafkaConsumer = CordaKafkaConsumerImpl(
            kafkaConfig,
            consumer,
            listener
        )

        doThrow(FencedInstanceIdException("")).whenever(consumer).poll(Mockito.any(Duration::class.java))
        assertThatExceptionOfType(CordaMessageAPIFatalException::class.java).isThrownBy {
            cordaKafkaConsumer.poll()
        }
        verify(consumer, times(1)).poll(Mockito.any(Duration::class.java))
    }

    @Test
    fun testPollIntermittent() {
        consumer = mock()
        cordaKafkaConsumer = CordaKafkaConsumerImpl(
            kafkaConfig,
            consumer,
            listener
        )

        doThrow(KafkaException()).whenever(consumer).poll(Mockito.any(Duration::class.java))
        assertThatExceptionOfType(CordaMessageAPIIntermittentException::class.java).isThrownBy {
            cordaKafkaConsumer.poll()
        }
        verify(consumer, times(1)).poll(Mockito.any(Duration::class.java))
    }

    @Test
    fun testCloseInvoked() {
        consumer = mock()
        cordaKafkaConsumer = CordaKafkaConsumerImpl(
            kafkaConfig,
            consumer,
            listener
        )

        cordaKafkaConsumer.close()
        verify(consumer, times(1)).close(Mockito.any(Duration::class.java))
    }

    @Test
    fun testCloseWithDurationInvoked() {
        consumer = mock()
        cordaKafkaConsumer = CordaKafkaConsumerImpl(
            kafkaConfig,
            consumer,
            listener
        )

        cordaKafkaConsumer.close(Duration.ZERO)
        verify(consumer, times(1)).close(Duration.ZERO)
    }

    @Test
    fun testCloseFailNoException() {
        consumer = mock()
        doThrow(KafkaException()).whenever(consumer).close(any())
        cordaKafkaConsumer = CordaKafkaConsumerImpl(
            kafkaConfig,
            consumer,
            listener
        )
        cordaKafkaConsumer.close()
        verify(consumer, times(1)).close(Mockito.any(Duration::class.java))
    }

    @Test
    fun testCommitOffsets() {
        val consumerRecord = ConsumerRecord(eventTopic, 1, 5L, "", "value")
        assertThat(consumer.committed(setOf(partition))).isEmpty()

        cordaKafkaConsumer.commitSyncOffsets(consumerRecord, "meta data")

        val committedPositionAfterCommit = consumer.committed(setOf(partition))
        assertThat(committedPositionAfterCommit.values.first().offset()).isEqualTo(6)
    }

    @Test
    fun testCommitOffsetsRetries() {
        consumer = mock()
        val cordaKafkaConsumer = CordaKafkaConsumerImpl<String, String>(
            kafkaConfig,
            consumer,
            listener
        )

        val consumerRecord = ConsumerRecord(eventTopic, 1, 5L, "", "value")
        doThrow(TimeoutException()).whenever(consumer).commitSync(anyMap())
        assertThatExceptionOfType(CordaMessageAPIFatalException::class.java).isThrownBy {
            cordaKafkaConsumer.commitSyncOffsets(consumerRecord, "meta data")
        }
        verify(consumer, times(3)).commitSync(anyMap())
    }

    @Test
    fun testCommitOffsetsFatal() {
        consumer = mock()
        cordaKafkaConsumer = CordaKafkaConsumerImpl(
            kafkaConfig,
            consumer,
            listener
        )

        val consumerRecord = ConsumerRecord(eventTopic, 1, 5L, "", "value")
        doThrow(CommitFailedException()).whenever(consumer).commitSync(anyMap())
        assertThatExceptionOfType(CordaMessageAPIFatalException::class.java).isThrownBy {
            cordaKafkaConsumer.commitSyncOffsets(consumerRecord, "meta data")
        }
        verify(consumer, times(1)).commitSync(anyMap())
    }


    @Test
    fun testSubscribeToTopic() {
        consumer = mock()
        cordaKafkaConsumer = CordaKafkaConsumerImpl(
            kafkaConfig,
            consumer,
            listener
        )
        cordaKafkaConsumer.subscribeToTopic()
        verify(consumer, times(1)).subscribe(Mockito.anyList(), Mockito.any(ConsumerRebalanceListener::class.java))
    }

    @Test
    fun testSubscribe() {
        consumer = mock()
        cordaKafkaConsumer = CordaKafkaConsumerImpl(
            kafkaConfig,
            consumer,
            listener
        )
        cordaKafkaConsumer.subscribe(listOf("test"), null)
        verify(consumer, times(1)).subscribe(Mockito.anyList(), Mockito.any(ConsumerRebalanceListener::class.java))
    }

    @Test
    fun testSubscribeNullListener() {
        consumer = mock()
        cordaKafkaConsumer = CordaKafkaConsumerImpl(
            kafkaConfig,
            consumer,
            null
        )
        cordaKafkaConsumer.subscribe(listOf("test"), null)
        verify(consumer, times(1)).subscribe(Mockito.anyList())
    }

    @Test
    fun testSubscribeToTopicRetries() {
        consumer = mock()
        cordaKafkaConsumer = CordaKafkaConsumerImpl(
            kafkaConfig,
            consumer,
            listener
        )
        doThrow(KafkaException()).whenever(consumer)
            .subscribe(Mockito.anyList(), Mockito.any(ConsumerRebalanceListener::class.java))
        assertThatExceptionOfType(CordaMessageAPIFatalException::class.java).isThrownBy {
            cordaKafkaConsumer.subscribeToTopic()
        }
        verify(consumer, times(3)).subscribe(Mockito.anyList(), Mockito.any(ConsumerRebalanceListener::class.java))
    }

    @Test
    fun testSubscribeToTopicFatal() {
        consumer = mock()
        cordaKafkaConsumer = CordaKafkaConsumerImpl(
            kafkaConfig,
            consumer,
            listener
        )
        doThrow(IllegalArgumentException())
            .whenever(consumer).subscribe(
                Mockito.anyList(), Mockito.any(ConsumerRebalanceListener::class.java)
            )
        assertThatExceptionOfType(CordaMessageAPIFatalException::class.java).isThrownBy {
            cordaKafkaConsumer.subscribeToTopic()
        }
        verify(consumer, times(1)).subscribe(Mockito.anyList(), Mockito.any(ConsumerRebalanceListener::class.java))
    }

    @Test
    fun testResetToLastCommittedPositionsOffsetIsSet() {
        val offsetCommit = 5L
        commitOffsetForConsumer(offsetCommit)

        //Reset fetch position to the last committed record
        //Does not matter what reset strategy is passed here
        cordaKafkaConsumer.resetToLastCommittedPositions(OffsetResetStrategy.NONE)

        val positionAfterReset = consumer.position(partition)
        assertThat(positionAfterReset).isEqualTo(offsetCommit)
    }

    @Test
    fun testResetToLastCommittedPositionsStrategyLatest() {
        //Reset fetch position to the last committed record
        cordaKafkaConsumer.resetToLastCommittedPositions(OffsetResetStrategy.LATEST)

        val positionAfterReset = consumer.position(partition)
        assertThat(positionAfterReset).isEqualTo(numberOfRecords)
    }

    @Test
    fun testResetToLastCommittedPositionsStrategyEarliest() {
        //Reset fetch position to the last committed record
        cordaKafkaConsumer.resetToLastCommittedPositions(OffsetResetStrategy.EARLIEST)

        val positionAfterReset = consumer.position(partition)
        assertThat(positionAfterReset).isEqualTo(0L)
    }


    @Test
    fun testGetPartitionsNullPointerException() {
        assertThatExceptionOfType(CordaMessageAPIIntermittentException::class.java).isThrownBy {
            cordaKafkaConsumer.getPartitions("topic", Duration.ZERO)
        }.withMessageContaining("Partitions for topic topic are null. Kafka may not have completed startup.")
    }

    @Test
    fun testAssignInvokedFatal() {
        consumer = mock()
        cordaKafkaConsumer = CordaKafkaConsumerImpl(
            kafkaConfig,
            consumer,
            listener
        )
        doThrow(IllegalArgumentException()).whenever(consumer).assign(Mockito.anyList())
        assertThatExceptionOfType(CordaMessageAPIFatalException::class.java).isThrownBy {
            cordaKafkaConsumer.assign(listOf())
        }
        verify(consumer, times(1)).assign(Mockito.anyList())
    }

    @Test
    fun testAssignmentInvokedFatal() {
        consumer = mock()
        cordaKafkaConsumer = CordaKafkaConsumerImpl(
            kafkaConfig,
            consumer,
            listener
        )
        doThrow(IllegalStateException()).whenever(consumer).assignment()
        assertThatExceptionOfType(CordaMessageAPIFatalException::class.java).isThrownBy {
            cordaKafkaConsumer.assignment()
        }
        verify(consumer, times(1)).assignment()
    }

    @Test
    fun testPositionInvokedFatal() {
        consumer = mock()
        cordaKafkaConsumer = CordaKafkaConsumerImpl(
            kafkaConfig,
            consumer,
            listener
        )
        doThrow(IllegalStateException()).whenever(consumer).position(TopicPartition(null, 0))
        assertThatExceptionOfType(CordaMessageAPIFatalException::class.java).isThrownBy {
            cordaKafkaConsumer.position(TopicPartition(null, 0))
        }
        verify(consumer, times(1)).position(TopicPartition(null, 0))
    }

    @Test
    fun testSeekInvokedFatal() {
        consumer = mock()
        cordaKafkaConsumer = CordaKafkaConsumerImpl(
            kafkaConfig,
            consumer,
            listener
        )
        doThrow(IllegalStateException()).whenever(consumer).seek(TopicPartition(null, 0), 0)
        assertThatExceptionOfType(CordaMessageAPIFatalException::class.java).isThrownBy {
            cordaKafkaConsumer.seek(TopicPartition(null, 0), 0)
        }
        verify(consumer, times(1)).seek(TopicPartition(null, 0), 0)
    }

    @Test
    fun testSeekToBeginningInvokedFatal() {
        consumer = mock()
        cordaKafkaConsumer = CordaKafkaConsumerImpl(
            kafkaConfig,
            consumer,
            listener
        )
        doThrow(IllegalStateException()).whenever(consumer).seekToBeginning(mutableListOf(TopicPartition(null, 0)))
        assertThatExceptionOfType(CordaMessageAPIFatalException::class.java).isThrownBy {
            cordaKafkaConsumer.seekToBeginning(mutableListOf(TopicPartition(null, 0)))
        }
        verify(consumer, times(1)).seekToBeginning(mutableListOf(TopicPartition(null, 0)))
    }

    @Test
    fun testBeginningOffsetsInvokedFatal() {
        consumer = mock()
        cordaKafkaConsumer = CordaKafkaConsumerImpl(
            kafkaConfig,
            consumer,
            listener
        )
        doThrow(IllegalStateException()).whenever(consumer).beginningOffsets(mutableListOf(TopicPartition(null, 0)))
        assertThatExceptionOfType(CordaMessageAPIFatalException::class.java).isThrownBy {
            cordaKafkaConsumer.beginningOffsets(mutableListOf(TopicPartition(null, 0)))
        }
        verify(consumer, times(1)).beginningOffsets(mutableListOf(TopicPartition(null, 0)))
    }

    @Test
    fun testEndOffsetsInvokedFatal() {
        consumer = mock()
        cordaKafkaConsumer = CordaKafkaConsumerImpl(
            kafkaConfig,
            consumer,
            listener
        )
        doThrow(IllegalStateException()).whenever(consumer).endOffsets(mutableListOf(TopicPartition(null, 0)))
        assertThatExceptionOfType(CordaMessageAPIFatalException::class.java).isThrownBy {
            cordaKafkaConsumer.endOffsets(mutableListOf(TopicPartition(null, 0)))
        }
        verify(consumer, times(1)).endOffsets(mutableListOf(TopicPartition(null, 0)))
    }

    @Test
    fun testResumeInvokedFatal() {
        consumer = mock()
        cordaKafkaConsumer = CordaKafkaConsumerImpl(
            kafkaConfig,
            consumer,
            listener
        )
        doThrow(IllegalStateException()).whenever(consumer).resume(mutableListOf(TopicPartition(null, 0)))
        assertThatExceptionOfType(CordaMessageAPIFatalException::class.java).isThrownBy {
            cordaKafkaConsumer.resume(mutableListOf(TopicPartition(null, 0)))
        }
        verify(consumer, times(1)).resume(mutableListOf(TopicPartition(null, 0)))
    }

    @Test
    fun testPauseInvokedFatal() {
        consumer = mock()
        cordaKafkaConsumer = CordaKafkaConsumerImpl(
            kafkaConfig,
            consumer,
            listener
        )
        doThrow(IllegalStateException()).whenever(consumer).pause(mutableListOf(TopicPartition(null, 0)))
        assertThatExceptionOfType(CordaMessageAPIFatalException::class.java).isThrownBy {
            cordaKafkaConsumer.pause(mutableListOf(TopicPartition(null, 0)))
        }
        verify(consumer, times(1)).pause(mutableListOf(TopicPartition(null, 0)))
    }

    @Test
    fun testPausedInvokedFatal() {
        consumer = mock()
        cordaKafkaConsumer = CordaKafkaConsumerImpl(
            kafkaConfig,
            consumer,
            listener
        )
        doThrow(IllegalStateException()).whenever(consumer).paused()
        assertThatExceptionOfType(CordaMessageAPIFatalException::class.java).isThrownBy {
            cordaKafkaConsumer.paused()
        }
        verify(consumer, times(1)).paused()
    }

    @Test
    fun testGroupMetadataInvokedFatal() {
        consumer = mock()
        cordaKafkaConsumer = CordaKafkaConsumerImpl(
            kafkaConfig,
            consumer,
            listener
        )
        doThrow(IllegalStateException()).whenever(consumer).groupMetadata()
        assertThatExceptionOfType(CordaMessageAPIFatalException::class.java).isThrownBy {
            cordaKafkaConsumer.groupMetadata()
        }
        verify(consumer, times(1)).groupMetadata()
    }


    private fun commitOffsetForConsumer(offsetCommit: Long) {
        val positionBeforePoll = consumer.position(partition)
        assertThat(positionBeforePoll).isEqualTo(0)
        consumer.poll(Duration.ZERO)

        //get current position after poll for this partition/topic
        val positionAfterPoll = consumer.position(partition)
        assertThat(positionAfterPoll).isEqualTo(numberOfRecords)

        //Commit offset for half the records = offset of 5.
        val currentOffsets = mutableMapOf<TopicPartition, OffsetAndMetadata>()
        currentOffsets[partition] = OffsetAndMetadata(offsetCommit, "metaData")
        consumer.commitSync(currentOffsets)
        val positionAfterCommit = consumer.position(partition)
        assertThat(positionAfterCommit).isEqualTo(numberOfRecords)
    }
}
