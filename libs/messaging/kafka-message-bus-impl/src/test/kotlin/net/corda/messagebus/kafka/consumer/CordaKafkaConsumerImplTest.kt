package net.corda.messagebus.kafka.consumer

import java.time.Duration
import net.corda.data.chunking.Chunk
import net.corda.data.chunking.ChunkKey
import net.corda.messagebus.api.CordaTopicPartition
import net.corda.messagebus.api.consumer.CordaConsumerRebalanceListener
import net.corda.messagebus.api.consumer.CordaConsumerRecord
import net.corda.messagebus.api.consumer.CordaOffsetResetStrategy
import net.corda.messagebus.kafka.config.ResolvedConsumerConfig
import net.corda.messagebus.kafka.utils.toKafka
import net.corda.messaging.api.chunking.ConsumerChunkDeserializerService
import net.corda.messaging.api.exception.CordaMessageAPIFatalException
import net.corda.messaging.api.exception.CordaMessageAPIIntermittentException
import net.corda.messaging.api.subscription.config.SubscriptionConfig
import net.corda.messaging.kafka.subscription.createMockConsumerAndAddRecords
import net.corda.messaging.kafka.subscription.generateChunkedCleanupRecords
import net.corda.messaging.kafka.subscription.generateConsumerRecords
import net.corda.messaging.kafka.subscription.generateMockChunkedConsumerRecordsList
import net.corda.messaging.kafka.subscription.generateMockConsumerRecords
import net.corda.schema.registry.AvroSchemaRegistry
import org.apache.kafka.clients.consumer.CommitFailedException
import org.apache.kafka.clients.consumer.Consumer
import org.apache.kafka.clients.consumer.ConsumerRebalanceListener
import org.apache.kafka.clients.consumer.MockConsumer
import org.apache.kafka.clients.consumer.OffsetAndMetadata
import org.apache.kafka.common.KafkaException
import org.apache.kafka.common.TopicPartition
import org.apache.kafka.common.errors.AuthenticationException
import org.apache.kafka.common.errors.AuthorizationException
import org.apache.kafka.common.errors.FencedInstanceIdException
import org.apache.kafka.common.errors.InconsistentGroupProtocolException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatExceptionOfType
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.ArgumentMatchers.anyMap
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

class CordaKafkaConsumerImplTest {
    private lateinit var cordaKafkaConsumer: CordaKafkaConsumerImpl<String, String>
    private lateinit var subscriptionConfig: SubscriptionConfig
    private val listener: CordaConsumerRebalanceListener = mock()
    private val eventTopic = "eventTopic1"
    private val numberOfRecords = 10L
    private lateinit var consumer: MockConsumer<Any, Any>
    private lateinit var partition: TopicPartition
    private val avroSchemaRegistry: AvroSchemaRegistry = mock()
    private val chunkDeserializerService: ConsumerChunkDeserializerService<String, String> = mock()
    private val consumerRecord = CordaConsumerRecord("prefixtopic", 1, 1, "key", "value", 0)
    private val consumerConfig = ResolvedConsumerConfig("group", "clientId", "prefix")

    @BeforeEach
    fun beforeEach() {
        doReturn(String::class.java).whenever(avroSchemaRegistry).getClassType(any())
        doReturn(consumerRecord.value).whenever(avroSchemaRegistry).deserialize(any(), any(), anyOrNull())
        subscriptionConfig = SubscriptionConfig("groupName1", eventTopic)

        val (mockConsumer, mockTopicPartition) = createMockConsumerAndAddRecords(
            consumerConfig.topicPrefix + eventTopic,
            numberOfRecords,
            CordaOffsetResetStrategy.EARLIEST.toKafka()
        )

        whenever(chunkDeserializerService.assembleChunks(any<Map<ChunkKey, Chunk>>())).thenReturn(Pair("key", "value"))
        consumer = mockConsumer
        partition = mockTopicPartition
        cordaKafkaConsumer = createConsumer(consumer)
    }

    private fun createConsumer(consumerParam: Consumer<Any, Any>, listenerParam: CordaConsumerRebalanceListener? = listener):
            CordaKafkaConsumerImpl<String,
            String> {
        return CordaKafkaConsumerImpl(
            consumerConfig,
            consumerParam,
            listenerParam,
            chunkDeserializerService,
        )
    }

    @Test
    fun testPollInvoked() {
        val consumerRecords = generateMockConsumerRecords(2, eventTopic, 1)

        consumer = mock()
        doReturn(consumerRecords).whenever(consumer).poll(Mockito.any(Duration::class.java))
        cordaKafkaConsumer = createConsumer(consumer)

        cordaKafkaConsumer.poll(Duration.ofMillis(100L))
        verify(consumer, times(1)).assignment()
        verify(consumer, times(1)).poll(Mockito.any(Duration::class.java))
    }

    @Test
    fun testPollWithDurationInvoked() {
        val consumerRecords = generateMockConsumerRecords(2, eventTopic, 1)

        consumer = mock()
        doReturn(consumerRecords).whenever(consumer).poll(Mockito.any(Duration::class.java))
        cordaKafkaConsumer = createConsumer(consumer)

        cordaKafkaConsumer.poll(Duration.ZERO)
        verify(consumer, times(1)).poll(Duration.ZERO)
    }

    @ParameterizedTest(name = "[{index}]: {0}")
    @ValueSource(classes = [
        ArithmeticException::class, AuthorizationException::class, AuthenticationException::class,
        IllegalArgumentException::class, IllegalStateException::class,
        FencedInstanceIdException::class, InconsistentGroupProtocolException::class,
    ])
    fun testPollFatal(exceptionClass: Class<out Throwable>) {
        consumer = mock()
        cordaKafkaConsumer = createConsumer(consumer)

        doThrow(exceptionClass.getDeclaredConstructor(String::class.java).newInstance("mock"))
            .whenever(consumer)
            .poll(Mockito.any(Duration::class.java))
        assertThatExceptionOfType(CordaMessageAPIFatalException::class.java).isThrownBy {
            cordaKafkaConsumer.poll(Duration.ofMillis(100L))
        }
        verify(consumer, times(1)).poll(Mockito.any(Duration::class.java))
    }

    @Test
    fun testPollIntermittent() {
        consumer = mock()
        cordaKafkaConsumer = createConsumer(consumer)

        doThrow(KafkaException()).whenever(consumer).poll(Mockito.any(Duration::class.java))
        assertThatExceptionOfType(CordaMessageAPIIntermittentException::class.java).isThrownBy {
            cordaKafkaConsumer.poll(Duration.ofMillis(100L))
        }
        verify(consumer, times(1)).poll(Mockito.any(Duration::class.java))
    }

    @Test
    fun testCloseInvoked() {
        consumer = mock()
        cordaKafkaConsumer = createConsumer(consumer)

        cordaKafkaConsumer.close()
        verify(consumer, times(1)).close()
    }

    @Test
    fun testCloseFailNoException() {
        consumer = mock()
        doThrow(KafkaException()).whenever(consumer).close()
        cordaKafkaConsumer = createConsumer(consumer)

        cordaKafkaConsumer.close()
        verify(consumer, times(1)).close()
    }

    @Test
    fun testCommitOffsets() {
        val consumerRecord = CordaConsumerRecord(eventTopic, 1, 5L, "", "value", 0)
        assertThat(consumer.committed(setOf(partition))).isEmpty()

        cordaKafkaConsumer.commitSyncOffsets(consumerRecord, "meta data")

        val committedPositionAfterCommit = consumer.committed(setOf(partition))
        assertThat(committedPositionAfterCommit.values.first().offset()).isEqualTo(6)
    }

    @Test
    fun testCommitOffsetsFatal() {
        consumer = mock()
        cordaKafkaConsumer = createConsumer(consumer)

        val consumerRecord = CordaConsumerRecord(eventTopic, 1, 5L, "", "value", 0)
        doThrow(CommitFailedException()).whenever(consumer).commitSync(anyMap())
        assertThatExceptionOfType(CordaMessageAPIFatalException::class.java).isThrownBy {
            cordaKafkaConsumer.commitSyncOffsets(consumerRecord, "meta data")
        }
        verify(consumer, times(1)).commitSync(anyMap())
    }


    @Test
    fun testSubscribe() {
        consumer = mock()
        cordaKafkaConsumer = createConsumer(consumer)

        cordaKafkaConsumer.subscribe(listOf("test"), null)
        verify(consumer, times(1)).subscribe(Mockito.anyList(), Mockito.any(ConsumerRebalanceListener::class.java))
    }

    @Test
    fun testSubscribeNullListener() {
        consumer = mock()
        cordaKafkaConsumer = createConsumer(consumer, null)

        cordaKafkaConsumer.subscribe(listOf("test"), null)
        verify(consumer, times(1)).subscribe(Mockito.anyList())
    }

    @Test
    fun testSubscribeFatal() {
        consumer = mock()
        cordaKafkaConsumer = createConsumer(consumer)

        doThrow(IllegalArgumentException())
            .whenever(consumer).subscribe(
                Mockito.anyList(), Mockito.any(ConsumerRebalanceListener::class.java)
            )
        assertThatExceptionOfType(CordaMessageAPIFatalException::class.java).isThrownBy {
            cordaKafkaConsumer.subscribe(eventTopic)
        }
        verify(consumer, times(1)).subscribe(Mockito.anyList(), Mockito.any(ConsumerRebalanceListener::class.java))
    }

    @Test
    fun testResetToLastCommittedPositionsOffsetIsSet() {
        val offsetCommit = 5L
        commitOffsetForConsumer(offsetCommit)

        //Reset fetch position to the last committed record
        //Does not matter what reset strategy is passed here
        cordaKafkaConsumer.resetToLastCommittedPositions(CordaOffsetResetStrategy.NONE)

        val positionAfterReset = consumer.position(partition)
        assertThat(positionAfterReset).isEqualTo(offsetCommit)
    }

    @Test
    fun testResetToLastCommittedPositionsStrategyLatest() {
        //Reset fetch position to the last committed record
        cordaKafkaConsumer.resetToLastCommittedPositions(CordaOffsetResetStrategy.LATEST)

        val positionAfterReset = consumer.position(partition)
        assertThat(positionAfterReset).isEqualTo(numberOfRecords)
    }

    @Test
    fun testResetToLastCommittedPositionsStrategyEarliest() {
        //Reset fetch position to the last committed record
        cordaKafkaConsumer.resetToLastCommittedPositions(CordaOffsetResetStrategy.EARLIEST)

        val positionAfterReset = consumer.position(partition)
        assertThat(positionAfterReset).isEqualTo(0L)
    }

    @Test
    fun testGetPartitionsNullPointerException() {
        consumer = mock()
        cordaKafkaConsumer = createConsumer(consumer)

        doReturn(null).`when`(consumer).partitionsFor(any())
        assertThatExceptionOfType(CordaMessageAPIIntermittentException::class.java).isThrownBy {
            cordaKafkaConsumer.getPartitions("topic")
        }.withMessageContaining("Partitions for topic topic are null. Kafka may not have completed startup.")
    }

    @Test
    fun testAssignInvokedFatal() {
        consumer = mock()
        cordaKafkaConsumer = createConsumer(consumer)

        doThrow(IllegalArgumentException()).whenever(consumer).assign(Mockito.anyList())
        assertThatExceptionOfType(CordaMessageAPIFatalException::class.java).isThrownBy {
            cordaKafkaConsumer.assign(listOf())
        }
        verify(consumer, times(1)).assign(Mockito.anyList())
    }

    @Test
    fun testAssignmentInvokedFatal() {
        consumer = mock()
        cordaKafkaConsumer = createConsumer(consumer)

        doThrow(IllegalStateException()).whenever(consumer).assignment()
        assertThatExceptionOfType(CordaMessageAPIFatalException::class.java).isThrownBy {
            cordaKafkaConsumer.assignment()
        }
        verify(consumer, times(1)).assignment()
    }

    @Test
    fun testPositionInvokedFatal() {
        consumer = mock()
        cordaKafkaConsumer = createConsumer(consumer)

        doThrow(IllegalStateException()).whenever(consumer).position(
            TopicPartition(consumerConfig.topicPrefix + "null", 0)
        )
        assertThatExceptionOfType(CordaMessageAPIFatalException::class.java).isThrownBy {
            cordaKafkaConsumer.position(CordaTopicPartition("null", 0))
        }
        verify(consumer, times(1)).position(
            TopicPartition(consumerConfig.topicPrefix + "null", 0)
        )
    }

    @Test
    fun testSeekInvokedFatal() {
        consumer = mock()
        cordaKafkaConsumer = createConsumer(consumer)

        doThrow(IllegalStateException()).whenever(consumer).seek(
            TopicPartition(consumerConfig.topicPrefix + "null", 0), 0
        )
        assertThatExceptionOfType(CordaMessageAPIFatalException::class.java).isThrownBy {
            cordaKafkaConsumer.seek(CordaTopicPartition("null", 0), 0)
        }
        verify(consumer, times(1)).seek(
            TopicPartition(consumerConfig.topicPrefix + "null", 0), 0
        )
    }

    @Test
    fun testSeekToBeginningInvokedFatal() {
        consumer = mock()
        cordaKafkaConsumer = createConsumer(consumer)

        doThrow(IllegalStateException()).whenever(consumer).seekToBeginning(
            mutableListOf(TopicPartition(consumerConfig.topicPrefix + "test", 0))
        )
        assertThatExceptionOfType(CordaMessageAPIFatalException::class.java).isThrownBy {
            cordaKafkaConsumer.seekToBeginning(mutableListOf(CordaTopicPartition("test", 0)))
        }
        verify(consumer, times(1)).seekToBeginning(
            mutableListOf(TopicPartition(consumerConfig.topicPrefix + "test", 0))
        )
    }

    @Test
    fun testBeginningOffsetsInvokedFatal() {
        consumer = mock()
        cordaKafkaConsumer = createConsumer(consumer)

        doThrow(IllegalStateException()).whenever(consumer).beginningOffsets(
            mutableListOf(TopicPartition(consumerConfig.topicPrefix + "test", 0))
        )
        assertThatExceptionOfType(CordaMessageAPIFatalException::class.java).isThrownBy {
            cordaKafkaConsumer.beginningOffsets(mutableListOf(CordaTopicPartition("test", 0)))
        }
        verify(consumer, times(1)).beginningOffsets(
            mutableListOf(TopicPartition(consumerConfig.topicPrefix + "test", 0))
        )
    }

    @Test
    fun testEndOffsetsInvokedFatal() {
        consumer = mock()
        cordaKafkaConsumer = createConsumer(consumer)

        doThrow(IllegalStateException()).whenever(consumer)
            .endOffsets(mutableListOf(TopicPartition(consumerConfig.topicPrefix + "test", 0)))
        assertThatExceptionOfType(CordaMessageAPIFatalException::class.java).isThrownBy {
            cordaKafkaConsumer.endOffsets(mutableListOf(CordaTopicPartition("test", 0)))
        }
        verify(consumer, times(1)).endOffsets(mutableListOf(TopicPartition(consumerConfig.topicPrefix + "test", 0)))
    }

    @Test
    fun testResumeInvokedFatal() {
        consumer = mock()
        cordaKafkaConsumer = createConsumer(consumer)

        doThrow(IllegalStateException()).whenever(consumer)
            .resume(mutableListOf(TopicPartition(consumerConfig.topicPrefix + "test", 0)))
        assertThatExceptionOfType(CordaMessageAPIFatalException::class.java).isThrownBy {
            cordaKafkaConsumer.resume(mutableListOf(CordaTopicPartition("test", 0)))
        }
        verify(consumer, times(1)).resume(
            mutableListOf(TopicPartition(consumerConfig.topicPrefix + "test", 0))
        )
    }

    @Test
    fun testPauseInvokedFatal() {
        consumer = mock()
        cordaKafkaConsumer = createConsumer(consumer)

        doThrow(IllegalStateException()).whenever(consumer)
            .pause(mutableListOf(TopicPartition(consumerConfig.topicPrefix + "test", 0)))
        assertThatExceptionOfType(CordaMessageAPIFatalException::class.java).isThrownBy {
            cordaKafkaConsumer.pause(mutableListOf(CordaTopicPartition("test", 0)))
        }
        verify(consumer, times(1)).pause(
            mutableListOf(TopicPartition(consumerConfig.topicPrefix + "test", 0))
        )
    }

    @Test
    fun testPausedInvokedFatal() {
        consumer = mock()
        cordaKafkaConsumer = createConsumer(consumer)

        doThrow(IllegalStateException()).whenever(consumer).paused()
        assertThatExceptionOfType(CordaMessageAPIFatalException::class.java).isThrownBy {
            cordaKafkaConsumer.paused()
        }
        verify(consumer, times(1)).paused()
    }

    @Test
    fun testGroupMetadataInvokedFatal() {
        consumer = mock()
        cordaKafkaConsumer = createConsumer(consumer)

        doThrow(IllegalStateException()).whenever(consumer).groupMetadata()
        assertThatExceptionOfType(CordaMessageAPIFatalException::class.java).isThrownBy {
            cordaKafkaConsumer.groupMetadata()
        }
        verify(consumer, times(1)).groupMetadata()
    }

    @Test
    fun `Received all chunks within a single poll with normal records before and after`() {
        val beforeChunkedRecords = generateMockConsumerRecords(2, eventTopic, 1, 0)
        val chunkedRecords = generateMockChunkedConsumerRecordsList(3, eventTopic, 1, 2)
        val afterChunkedRecords = generateMockConsumerRecords(2, eventTopic, 1, 5)
        val consumerRecords = generateConsumerRecords(beforeChunkedRecords.plus(chunkedRecords).plus(afterChunkedRecords),
            eventTopic, 1)

        consumer = mock()
        doReturn(consumerRecords).whenever(consumer).poll(Mockito.any(Duration::class.java))
        cordaKafkaConsumer = createConsumer(consumer)

        val result = cordaKafkaConsumer.poll(Duration.ofMillis(100L))
        assertThat(result.size).isEqualTo(5)
        verify(consumer, times(1)).poll(Mockito.any(Duration::class.java))
        verify(chunkDeserializerService, times(1)).assembleChunks(any<Map<ChunkKey, Chunk>>())
    }

    @Test
    fun `Received all chunks within a single poll with normal records before and after but failed to deserialize`() {
        val beforeChunkedRecords = generateMockConsumerRecords(2, eventTopic, 1, 0)
        val chunkedRecords = generateMockChunkedConsumerRecordsList(3, eventTopic, 1, 2)
        val afterChunkedRecords = generateMockConsumerRecords(2, eventTopic, 1, 5)
        val consumerRecords = generateConsumerRecords(beforeChunkedRecords.plus(chunkedRecords).plus(afterChunkedRecords),
            eventTopic, 1)

        consumer = mock()
        whenever(chunkDeserializerService.assembleChunks(any<Map<ChunkKey, Chunk>>())).thenReturn(null)

        doReturn(consumerRecords).whenever(consumer).poll(Mockito.any(Duration::class.java))
        cordaKafkaConsumer = createConsumer(consumer)

        val result = cordaKafkaConsumer.poll(Duration.ofMillis(100L))
        assertThat(result.size).isEqualTo(4)
        verify(consumer, times(1)).poll(Mockito.any(Duration::class.java))
        verify(chunkDeserializerService, times(1)).assembleChunks(any<Map<ChunkKey, Chunk>>())
    }

    @Test
    fun `Received incomplete chunks within a single poll with normal records before and after, only returns records before the chunks`() {
        val beforeChunkedRecords = generateMockConsumerRecords(2, eventTopic, 1, 0)
        val chunkedRecords = generateMockChunkedConsumerRecordsList(3, eventTopic, 1, 2, false)
        val afterChunkedRecords = generateMockConsumerRecords(2, eventTopic, 1, 5)
        val consumerRecords = generateConsumerRecords(beforeChunkedRecords.plus(chunkedRecords).plus(afterChunkedRecords),
            eventTopic, 1)

        consumer = mock()
        doReturn(consumerRecords).whenever(consumer).poll(Mockito.any(Duration::class.java))
        cordaKafkaConsumer = createConsumer(consumer)

        val result = cordaKafkaConsumer.poll(Duration.ofMillis(100L))
        assertThat(result.size).isEqualTo(2)
        verify(consumer, times(1)).poll(Mockito.any(Duration::class.java))
        verifyNoInteractions(chunkDeserializerService)
    }

    @Test
    fun `Received chunks across two polls with normal records before and after`() {
        val beforeChunkedRecords = generateMockConsumerRecords(2, eventTopic, 1, 0)
        val chunkedRecords = generateMockChunkedConsumerRecordsList(3, eventTopic, 1, 2)
        val afterChunkedRecords = generateMockConsumerRecords(2, eventTopic, 1, 5)
        val firstConsumerRecords = generateConsumerRecords(beforeChunkedRecords.plus(chunkedRecords.first()),
            eventTopic, 1)
        val secondConsumerRecords = generateConsumerRecords(chunkedRecords.minus(chunkedRecords.first()).plus(afterChunkedRecords),
            eventTopic, 1)

        consumer = mock()

        var firstPollCalled = false
        doAnswer {
            if (!firstPollCalled) {
                firstPollCalled = true
                firstConsumerRecords
            } else {
                secondConsumerRecords
            }
        }.whenever(consumer).poll(Mockito.any(Duration::class.java))

        cordaKafkaConsumer = createConsumer(consumer)

        val firstResult = cordaKafkaConsumer.poll(Duration.ofMillis(100L))
        val secondResult = cordaKafkaConsumer.poll(Duration.ofMillis(100L))
        assertThat(firstResult.size).isEqualTo(2)
        assertThat(secondResult.size).isEqualTo(3)
        verify(consumer, times(2)).poll(Mockito.any(Duration::class.java))
        verify(chunkDeserializerService, times(1)).assembleChunks(any<Map<ChunkKey, Chunk>>())
    }

    @Test
    fun `Receiving ChunkKey cleanup records has no effect`() {
        consumer = mock()

        val cleanupRecords = generateChunkedCleanupRecords(10, eventTopic, 0)
        whenever(consumer.poll(Mockito.any(Duration::class.java))).thenReturn(cleanupRecords)

        cordaKafkaConsumer = createConsumer(consumer)

        val firstResult = cordaKafkaConsumer.poll(Duration.ofMillis(100L))
        val secondResult = cordaKafkaConsumer.poll(Duration.ofMillis(100L))
        assertThat(firstResult.size).isEqualTo(0)
        assertThat(secondResult.size).isEqualTo(0)
        verify(consumer, times(2)).poll(Mockito.any(Duration::class.java))
        verify(chunkDeserializerService, times(0)).assembleChunks(any<Map<ChunkKey, Chunk>>())
    }

    @Test
    fun `Received chunks across two polls with normal records interleaved`() {
        val beforeChunkedRecords = generateMockConsumerRecords(2, eventTopic, 1, 0)
        val chunkedRecords = generateMockChunkedConsumerRecordsList(3, eventTopic, 1, 2)
        val afterChunkedRecords = generateMockConsumerRecords(2, eventTopic, 1, 5)
        val firstConsumerRecords = generateConsumerRecords(beforeChunkedRecords.plus(chunkedRecords.first()),
            eventTopic, 1)
        val secondConsumerRecords = generateConsumerRecords(chunkedRecords.minus(chunkedRecords.first()).plus(afterChunkedRecords),
            eventTopic, 1)

        consumer = mock()

        var firstPollCalled = false
        doAnswer {
            if (!firstPollCalled) {
                firstPollCalled = true
                firstConsumerRecords
            } else {
                secondConsumerRecords
            }
        }.whenever(consumer).poll(Mockito.any(Duration::class.java))

        cordaKafkaConsumer = createConsumer(consumer)

        val firstResult = cordaKafkaConsumer.poll(Duration.ofMillis(100L))
        val secondResult = cordaKafkaConsumer.poll(Duration.ofMillis(100L))
        assertThat(firstResult.size).isEqualTo(2)
        assertThat(secondResult.size).isEqualTo(3)
        verify(consumer, times(2)).poll(Mockito.any(Duration::class.java))
        verify(chunkDeserializerService, times(1)).assembleChunks(any<Map<ChunkKey, Chunk>>())
    }

    @Test
    fun `Received chunks across two polls with normal records before, interleaved and after on multiple partitions`() {
        val beforeChunkedRecordsPartition1 = generateMockConsumerRecords(2, eventTopic, 1, 0)
        val chunkedRecordsPartition1 = generateMockChunkedConsumerRecordsList(3, eventTopic, 1, 2)
        val afterChunkedRecordsPartition1 = generateMockConsumerRecords(2, eventTopic, 1, 5)
        val interLeavedChunkedRecordsPartition1 = generateMockConsumerRecords(2, eventTopic, 1, 7)

        val beforeChunkedRecordsPartition2 = generateMockConsumerRecords(2, eventTopic, 2, 0)
        val chunkedRecordsPartition2 = generateMockChunkedConsumerRecordsList(3, eventTopic, 2, 2)
        val afterChunkedRecordsPartition2 = generateMockConsumerRecords(2, eventTopic, 2, 5)
        val interLeavedChunkedRecordsPartition2 = generateMockConsumerRecords(2, eventTopic, 2, 7)

        val firstConsumerRecordsList = beforeChunkedRecordsPartition1
            .plus(beforeChunkedRecordsPartition2)
            .plus(chunkedRecordsPartition1.first())
            .plus(interLeavedChunkedRecordsPartition1)
            .plus(chunkedRecordsPartition2.first())
            .plus(interLeavedChunkedRecordsPartition2)

        val secondConsumerRecordsList = chunkedRecordsPartition1.minus(chunkedRecordsPartition1.first())
            .plus(chunkedRecordsPartition2.minus(chunkedRecordsPartition2.first()))
            .plus(afterChunkedRecordsPartition1)
            .plus(afterChunkedRecordsPartition2)

        val firstConsumerRecords = generateConsumerRecords(firstConsumerRecordsList, eventTopic, 1)
        val secondConsumerRecords = generateConsumerRecords(secondConsumerRecordsList,eventTopic, 1)

        consumer = mock()

        var firstPollCalled = false
        doAnswer {
            if (!firstPollCalled) {
                firstPollCalled = true
                firstConsumerRecords
            } else {
                secondConsumerRecords
            }
        }.whenever(consumer).poll(Mockito.any(Duration::class.java))

        cordaKafkaConsumer = createConsumer(consumer)

        val firstResult = cordaKafkaConsumer.poll(Duration.ofMillis(100L))
        val secondResult = cordaKafkaConsumer.poll(Duration.ofMillis(100L))
        assertThat(firstResult.size).isEqualTo(4)
        assertThat(secondResult.size).isEqualTo(10)
        verify(consumer, times(2)).poll(Mockito.any(Duration::class.java))
        verify(chunkDeserializerService, times(2)).assembleChunks(any<Map<ChunkKey, Chunk>>())
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
