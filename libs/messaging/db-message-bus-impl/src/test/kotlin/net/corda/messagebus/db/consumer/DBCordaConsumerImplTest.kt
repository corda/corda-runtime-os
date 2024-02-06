package net.corda.messagebus.db.consumer

import net.corda.avro.serialization.CordaAvroDeserializer
import net.corda.messagebus.api.CordaTopicPartition
import net.corda.messagebus.api.consumer.CordaConsumer
import net.corda.messagebus.api.consumer.CordaConsumerRecord
import net.corda.messagebus.api.consumer.CordaOffsetResetStrategy
import net.corda.messagebus.db.configuration.ResolvedConsumerConfig
import net.corda.messagebus.db.datamodel.CommittedPositionEntry
import net.corda.messagebus.db.datamodel.TopicRecordEntry
import net.corda.messagebus.db.datamodel.TransactionRecordEntry
import net.corda.messagebus.db.datamodel.TransactionState
import net.corda.messagebus.db.persistence.DBAccess
import net.corda.messagebus.db.persistence.DBAccess.Companion.ATOMIC_TRANSACTION
import net.corda.messagebus.db.serialization.MessageHeaderSerializerImpl
import net.corda.messaging.api.exception.CordaMessageAPIFatalException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatExceptionOfType
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mock
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Duration
import java.time.Instant

internal class DBCordaConsumerImplTest {

    companion object {
        private const val defaultTopic = "topic"
        private val defaultConfig = ResolvedConsumerConfig(
            "group",
            "client",
            10,
            CordaOffsetResetStrategy.EARLIEST,
            null,
            "",
            ""
        )

        private val serializedKey = "key".toByteArray()
        private val serializedValue = "value".toByteArray()
        private const val serializedHeader = "{}"
        private val invalidSerializedValue = "invalid_value".toByteArray()
        // Null inputs to deserializer are converted to "" by the @NotNull decorator
        private val nullValue = "".toByteArray()

        private val partition0 = CordaTopicPartition(defaultTopic, 0)
        private val partition1 = CordaTopicPartition(defaultTopic, 1)
        private val partition2 = CordaTopicPartition(defaultTopic, 2)
    }

    @Mock
    val consumerGroup = mock<ConsumerGroup>()

    @Mock
    val dbAccess = mock<DBAccess>()

    private fun makeConsumer(config: ResolvedConsumerConfig = defaultConfig): DBCordaConsumerImpl<String, String> {
        val keyDeserializer = mock<CordaAvroDeserializer<String>>()
        val valueDeserializer = mock<CordaAvroDeserializer<String>>()
        whenever(keyDeserializer.deserialize(eq(serializedKey))).thenAnswer { "key" }
        whenever(valueDeserializer.deserialize(eq(serializedValue))).thenAnswer { "value" }
        whenever(valueDeserializer.deserialize(eq(invalidSerializedValue))).thenAnswer { null }
        whenever(valueDeserializer.deserialize(eq(nullValue))).thenAnswer { null }
        return DBCordaConsumerImpl(
            config,
            dbAccess,
            consumerGroup,
            keyDeserializer,
            valueDeserializer,
            null,
            MessageHeaderSerializerImpl()
        )
    }

    private fun getTopicRecords(
        partition: CordaTopicPartition = partition0,
        startOffset: Long = 0L,
        value: ByteArray? = serializedValue,
        txState: TransactionState = TransactionState.COMMITTED,
        count: Int = 1): List<TopicRecordEntry>
    {
        val timestamp = Instant.parse("2022-01-01T00:00:00.00Z")

        return (0 until count).mapIndexed { index, _ ->
            TopicRecordEntry(
                defaultTopic,
                partition.partition,
                startOffset + index,
                serializedKey,
                value,
                serializedHeader,
                TransactionRecordEntry("id", txState),
                timestamp
            )
        }
    }

    @Test
    fun `consumer correctly assigns topic partitions`() {
        val consumer = makeConsumer()
        val topicPartitions = setOf(partition0, partition1, partition2)

        consumer.assign(topicPartitions)
        assertThat(consumer.assignment()).isEqualTo(topicPartitions)
    }

    @Test
    fun `consumer doesn't allow subscription after assignment`() {
        val consumer = makeConsumer()
        val topicPartitions = setOf(partition0, partition1, partition2)

        consumer.assign(topicPartitions)

        assertThatExceptionOfType(CordaMessageAPIFatalException::class.java).isThrownBy {
            consumer.subscribe(defaultTopic, null)
        }
    }

    @Test
    fun `consumer doesn't allow assignment after subscription`() {
        val consumer = makeConsumer()
        val topicPartitions = setOf(partition0, partition1, partition2)

        consumer.subscribe(defaultTopic, null)

        assertThatExceptionOfType(CordaMessageAPIFatalException::class.java).isThrownBy {
            consumer.assign(topicPartitions)
        }
    }

    @Test
    fun `consumer poll returns the correct record`() {
        val fromOffset = ArgumentCaptor.forClass(Long::class.java)
        val timestamp = Instant.parse("2022-01-01T00:00:00.00Z")
        val pollResult = getTopicRecords(partition = partition0, startOffset = 0L)

        val expectedRecord = CordaConsumerRecord(
            defaultTopic,
            0,
            0,
            "key",
            "value",
            timestamp.toEpochMilli()
        )

        whenever(dbAccess.getMaxCommittedPositions(any(), any())).thenAnswer { mapOf(partition0 to 0L) }
        whenever(dbAccess.getLatestRecordOffsets()).thenAnswer { mapOf(partition0 to 0L) }
        whenever(dbAccess.readRecords(fromOffset.capture(), any(), any())).thenAnswer { pollResult }
        whenever(consumerGroup.getTopicPartitionsFor(any())).thenAnswer { setOf(partition0) }

        val consumer = makeConsumer()
        val test = consumer.poll(Duration.ZERO)
        consumer.poll(Duration.ZERO)
        assertThat(test.size).isEqualTo(1)
        assertThat(test.single()).usingRecursiveComparison().isEqualTo(expectedRecord)
    }

    @Test
    fun `consumer poll correctly increases offset for multiple records`() {
        val pollResult = getTopicRecords(partition = partition0, startOffset = 0, count = 4)

        val fromOffset = ArgumentCaptor.forClass(Long::class.java)
        whenever(dbAccess.getMaxCommittedPositions(any(), any())).thenAnswer { mapOf(partition0 to 0L) }
        whenever(dbAccess.getLatestRecordOffsets()).thenAnswer { mapOf(partition0 to 4L) }
        whenever(dbAccess.readRecords(fromOffset.capture(), any(), any())).thenAnswer { pollResult }
        whenever(consumerGroup.getTopicPartitionsFor(any())).thenAnswer { setOf(partition0) }

        val consumer = makeConsumer()
        consumer.poll(Duration.ZERO)
        assertThat(consumer.position(partition0)).isEqualTo(4)
    }

    @Test
    fun `consumer poll correctly increases offset`() {
        val partitions = listOf(partition0, partition1, partition2)
        val offsets = mutableListOf(0L, 5L, 10L)

        whenever(consumerGroup.getTopicPartitionsFor((any()))).thenAnswer { partitions.toSet() }

        partitions.forEachIndexed { index, partition ->
            whenever(dbAccess.readRecords(any(), eq(partition), any())).thenAnswer {
                getTopicRecords(partition = partition, startOffset = offsets[index]).also {
                    offsets[index] = offsets[index] + 1 // Increase offset on successive calls to dbAccess.readRecords()
                }
            }
        }

        whenever(dbAccess.getMaxCommittedPositions(any(), any())).thenAnswer { partitions.zip(offsets).toMap() }
        whenever(dbAccess.getLatestRecordOffset(any())).thenAnswer { partitions.zip(offsets).toMap() }
        whenever(dbAccess.getLatestRecordOffsets()).thenAnswer { partitions.associateWith { Long.MAX_VALUE } }

        val consumer = makeConsumer()

        repeat(3) { consumer.poll(Duration.ZERO) }

        assertThat(consumer.position(partition0)).isEqualTo(1)
        assertThat(consumer.position(partition1)).isEqualTo(6)
        assertThat(consumer.position(partition2)).isEqualTo(11)

        repeat(3) { consumer.poll(Duration.ZERO) }

        assertThat(consumer.position(partition0)).isEqualTo(2)
        assertThat(consumer.position(partition1)).isEqualTo(7)
        assertThat(consumer.position(partition2)).isEqualTo(12)
    }

    @Test
    fun `consumer poll stops at uncommitted records`() {
        val fromOffset = ArgumentCaptor.forClass(Long::class.java)
        val timestamp = Instant.parse("2022-01-01T00:00:00.00Z")

        val committedRecords = getTopicRecords(
            startOffset = 0,
            txState = TransactionState.COMMITTED,
            count = 2
        )

        val pendingRecords = getTopicRecords(
            startOffset = 2,
            txState = TransactionState.PENDING,
            count = 2
        )

        val expectedRecords = listOf(
            CordaConsumerRecord(defaultTopic, 0, 0, "key", "value", timestamp.toEpochMilli()),
            CordaConsumerRecord(defaultTopic, 0, 1, "key", "value", timestamp.toEpochMilli()),
        )

        whenever(dbAccess.getMaxCommittedPositions(any(), any())).thenAnswer { mapOf(partition0 to 0L) }
        whenever(dbAccess.getLatestRecordOffsets()).thenAnswer { mapOf(partition0 to 3L) }
        whenever(dbAccess.readRecords(fromOffset.capture(), any(), any())).thenAnswer { committedRecords + pendingRecords }
        whenever(consumerGroup.getTopicPartitionsFor(any())).thenAnswer { setOf(partition0) }

        val consumer = makeConsumer()
        val test = consumer.poll(Duration.ZERO)
        assertThat(test.size).isEqualTo(2)
        assertThat(test).isEqualTo(expectedRecords)
    }

    @Test
    fun `consumer poll does not skip over uncommitted records`() {
        val fromOffset = ArgumentCaptor.forClass(Long::class.java)
        val timestamp = Instant.parse("2022-01-01T00:00:00.00Z")

        val startCommittedRecords = getTopicRecords(
            startOffset = 0,
            txState = TransactionState.COMMITTED,
            count = 2
        )

        val pendingRecords = getTopicRecords(
            startOffset = 2,
            txState = TransactionState.PENDING,
            count = 2
        )

        val finalCommittedRecords = getTopicRecords(
            startOffset = 4,
            txState = TransactionState.COMMITTED,
            count = 2
        )

        val expectedRecords = listOf(
            CordaConsumerRecord(defaultTopic, 0, 0, "key", "value", timestamp.toEpochMilli()),
            CordaConsumerRecord(defaultTopic, 0, 1, "key", "value", timestamp.toEpochMilli()),
        )

        whenever(dbAccess.getMaxCommittedPositions(any(), any())).thenAnswer { mapOf(partition0 to 0L) }
        whenever(dbAccess.getLatestRecordOffsets()).thenAnswer { mapOf(partition0 to 5L) }
        whenever(consumerGroup.getTopicPartitionsFor(any())).thenAnswer { setOf(partition0) }
        whenever(dbAccess.readRecords(fromOffset.capture(), any(), any())).thenAnswer {
            startCommittedRecords + pendingRecords + finalCommittedRecords
        }

        val consumer = makeConsumer()
        val test = consumer.poll(Duration.ZERO)
        assertThat(test.size).isEqualTo(2)
        assertThat(test).isEqualTo(expectedRecords)
    }

    @Test
    fun `consumer poll discards records which were nullified by deserializer`() {
        val fromOffset = ArgumentCaptor.forClass(Long::class.java)
        val timestamp = Instant.parse("2022-01-01T00:00:00.00Z")

        val validRecords = getTopicRecords(
            startOffset = 0,
            count = 2
        )

        val invalidRecords = getTopicRecords(
            startOffset = 2,
            value = invalidSerializedValue,
            count = 2
        )

        val expectedRecords = listOf(
            CordaConsumerRecord(defaultTopic, 0, 0, "key", "value", timestamp.toEpochMilli()),
            CordaConsumerRecord(defaultTopic, 0, 1, "key", "value", timestamp.toEpochMilli()),
        )

        whenever(dbAccess.getMaxCommittedPositions(any(), any())).thenAnswer { mapOf(partition0 to 0L) }
        whenever(dbAccess.getLatestRecordOffsets()).thenAnswer { mapOf(partition0 to 3L) }
        whenever(dbAccess.readRecords(fromOffset.capture(), any(), any())).thenAnswer { validRecords + invalidRecords }
        whenever(consumerGroup.getTopicPartitionsFor(any())).thenAnswer { setOf(partition0) }

        val consumer = makeConsumer()
        val test = consumer.poll(Duration.ZERO)
        assertThat(test.size).isEqualTo(2)
        assertThat(test).isEqualTo(expectedRecords)
    }

    @Test
    fun `consumer poll does not discard null values if they were null before deserialization`() {
        val fromOffset = ArgumentCaptor.forClass(Long::class.java)
        val timestamp = Instant.parse("2022-01-01T00:00:00.00Z")

        val validRecords = getTopicRecords(startOffset = 0, count = 2)
        val nullRecords = getTopicRecords(startOffset = 2, value = null, count = 2)

        val expectedRecords = listOf(
            CordaConsumerRecord(defaultTopic, 0, 0, "key", "value", timestamp.toEpochMilli()),
            CordaConsumerRecord(defaultTopic, 0, 1, "key", "value", timestamp.toEpochMilli()),
            CordaConsumerRecord(defaultTopic, 0, 2, "key", value=null, timestamp.toEpochMilli()),
            CordaConsumerRecord(defaultTopic, 0, 3, "key", value=null, timestamp.toEpochMilli()),
        )

        whenever(dbAccess.getMaxCommittedPositions(any(), any())).thenAnswer { mapOf(partition0 to 0L) }
        whenever(dbAccess.getLatestRecordOffsets()).thenAnswer { mapOf(partition0 to 3L) }
        whenever(dbAccess.readRecords(fromOffset.capture(), any(), any())).thenAnswer { validRecords + nullRecords }
        whenever(consumerGroup.getTopicPartitionsFor(any())).thenAnswer { setOf(partition0) }

        val consumer = makeConsumer()
        val test = consumer.poll(Duration.ZERO)
        assertThat(test.size).isEqualTo(4)
        assertThat(test).isEqualTo(expectedRecords)
    }

    @Test
    fun `consumer poll skips over transactions which have been marked as aborted`() {
        val fromOffset = ArgumentCaptor.forClass(Long::class.java)
        val timestamp = Instant.parse("2022-01-01T00:00:00.00Z")

        val recordsToReturn = getTopicRecords(startOffset = 0, count = 2) +
                            getTopicRecords(startOffset = 2, txState = TransactionState.ABORTED, count = 2) +
                            getTopicRecords(startOffset = 4, count = 2)

        val expectedRecords = listOf(
            CordaConsumerRecord(defaultTopic, 0, 0, "key", "value", timestamp.toEpochMilli()),
            CordaConsumerRecord(defaultTopic, 0, 1, "key", "value", timestamp.toEpochMilli()),
            CordaConsumerRecord(defaultTopic, 0, 4, "key", "value", timestamp.toEpochMilli()),
            CordaConsumerRecord(defaultTopic, 0, 5, "key", "value", timestamp.toEpochMilli())
        )

        whenever(dbAccess.getMaxCommittedPositions(any(), any())).thenAnswer { mapOf(partition0 to 0L) }
        whenever(dbAccess.getLatestRecordOffsets()).thenAnswer { mapOf(partition0 to 5L) }
        whenever(dbAccess.readRecords(fromOffset.capture(), any(), any())).thenAnswer { recordsToReturn }
        whenever(consumerGroup.getTopicPartitionsFor(any())).thenAnswer { setOf(partition0) }

        val consumer = makeConsumer()
        val test = consumer.poll(Duration.ZERO)
        assertThat(test.size).isEqualTo(4)
        assertThat(test).isEqualTo(expectedRecords)
    }

    @Test
    fun `consumer correctly cycles through topic partitions`() {
        whenever(consumerGroup.getTopicPartitionsFor(any())).thenAnswer { setOf(partition0, partition1) }

        val consumer = makeConsumer()
        assertThat(consumer.getNextTopicPartition()).isEqualTo(partition0)
        assertThat(consumer.getNextTopicPartition()).isEqualTo(partition1)
        assertThat(consumer.getNextTopicPartition()).isEqualTo(partition0)
        assertThat(consumer.getNextTopicPartition()).isEqualTo(partition1)
        assertThat(consumer.getNextTopicPartition()).isEqualTo(partition0)
        assertThat(consumer.getNextTopicPartition()).isEqualTo(partition1)
    }

    @Test
    fun `consumer correctly pauses, resumes and ignores topic partitions`() {
        whenever(consumerGroup.getTopicPartitionsFor(any())).thenAnswer { setOf(partition0, partition1, partition2) }

        val consumer = makeConsumer()
        consumer.pause(setOf(partition1))

        assertThat(consumer.getNextTopicPartition()).isEqualTo(partition0)
        assertThat(consumer.getNextTopicPartition()).isEqualTo(partition2)
        assertThat(consumer.getNextTopicPartition()).isEqualTo(partition0)
        assertThat(consumer.getNextTopicPartition()).isEqualTo(partition2)
        assertThat(consumer.getNextTopicPartition()).isEqualTo(partition0)

        consumer.resume(setOf(partition1))

        assertThat(consumer.getNextTopicPartition()).isEqualTo(partition1)
        assertThat(consumer.getNextTopicPartition()).isEqualTo(partition2)
    }

    @Test
    fun `consumer correctly returns paused partitions`() {
        whenever(consumerGroup.getTopicPartitionsFor(any())).thenAnswer { setOf(partition0, partition1, partition2) }

        val consumer = makeConsumer()
        val partitionsToPause = setOf(partition1, partition2)
        consumer.pause(partitionsToPause)

        assertThat(consumer.paused()).isEqualTo(partitionsToPause)
    }

    @Test
    fun `consumer returns empty list when no partitions are given`() {
        // Something to return.  But we don't expect to actually see it.
        val pollResult = getTopicRecords()

        whenever(dbAccess.getMaxCommittedPositions(any(), any())).thenAnswer { mapOf(partition0 to 0L) }
        whenever(dbAccess.readRecords(any(), any(), any())).thenAnswer { pollResult }
        whenever(consumerGroup.getTopicPartitionsFor(any())).thenAnswer { emptySet<CordaTopicPartition>() }

        val consumer = makeConsumer()
        assertThat(consumer.poll(Duration.ZERO)).isEmpty()
    }

    @Test
    fun `consumer returns correct position when not available based on auto_offset_reset`() {
        fun createAutoResetConsumer(strategy: CordaOffsetResetStrategy): CordaConsumer<String, String> {
            val keyDeserializer = mock<CordaAvroDeserializer<String>>()
            val valueDeserializer = mock<CordaAvroDeserializer<String>>()
            whenever(keyDeserializer.deserialize(eq(serializedKey))).thenAnswer { "key" }
            whenever(valueDeserializer.deserialize(eq(serializedValue))).thenAnswer { "value" }
            whenever(dbAccess.getEarliestRecordOffset(any())).thenAnswer { mapOf(partition0 to 0) }
            whenever(dbAccess.getLatestRecordOffset(any())).thenAnswer { mapOf(partition0 to 5) }
            val config = defaultConfig.copy(offsetResetStrategy = strategy)
            return DBCordaConsumerImpl(
                config,
                dbAccess,
                consumerGroup,
                keyDeserializer,
                valueDeserializer,
                null,
                MessageHeaderSerializerImpl()
            )
        }

        assertThat(createAutoResetConsumer(CordaOffsetResetStrategy.EARLIEST).position(partition0)).isEqualTo(0)
        assertThat(createAutoResetConsumer(CordaOffsetResetStrategy.LATEST).position(partition0)).isEqualTo(5)
        assertThatExceptionOfType(CordaMessageAPIFatalException::class.java).isThrownBy {
            createAutoResetConsumer(CordaOffsetResetStrategy.NONE).position(partition0)
        }
    }

    @Test
    fun `consumer returns correct values for beginning and end offsets`() {
        val earliestPositions = mapOf(partition0 to 1L, partition1 to 3L, partition2 to 6L)
        val latestPositions = mapOf(partition0 to 10L, partition1 to 30L, partition2 to 60L)
        whenever(dbAccess.getEarliestRecordOffset(any())).thenAnswer { earliestPositions }
        whenever(dbAccess.getLatestRecordOffset(any())).thenAnswer { latestPositions }

        val consumer = makeConsumer()

        assertThat(consumer.beginningOffsets(setOf(partition0, partition1, partition2))).isEqualTo(earliestPositions)
        assertThat(consumer.endOffsets(setOf(partition0, partition1, partition2))).isEqualTo(latestPositions)
    }

    @Test
    fun `consumer provides correct offsets to sync commit offsets when given records`() {
        val records = (getTopicRecords(count = 2) + getTopicRecords(partition1, count = 2)).map {
            CordaConsumerRecord(
                it.topic,
                it.partition,
                it.recordOffset,
                "foo",
                "bar",
                0
            )
        }
        val consumer = makeConsumer()
        consumer.syncCommitOffsets(records)
        verify(dbAccess).writeOffsets(argThat {
            val entry1 = this.find { it.topic == partition0.topic && it.partition == partition0.partition }
            val entry2 = this.find { it.topic == partition1.topic && it.partition == partition1.partition }
            entry1?.recordPosition == 1L && entry2?.recordPosition ==1L
        })
    }

    @Test
    fun `consumer correctly closes down dbAccess when closed`() {
        val consumer = makeConsumer()
        consumer.close()
        verify(dbAccess).close()
    }
}
