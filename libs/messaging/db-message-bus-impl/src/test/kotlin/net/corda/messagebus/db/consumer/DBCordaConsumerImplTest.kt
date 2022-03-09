package net.corda.messagebus.db.consumer

import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory
import net.corda.data.CordaAvroDeserializer
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.SmartConfigImpl
import net.corda.messagebus.api.CordaTopicPartition
import net.corda.messagebus.api.consumer.CordaConsumer
import net.corda.messagebus.api.consumer.CordaConsumerRecord
import net.corda.messagebus.api.consumer.CordaOffsetResetStrategy
import net.corda.messagebus.db.consumer.DBCordaConsumerImpl.Companion.AUTO_OFFSET_RESET
import net.corda.messagebus.db.datamodel.TopicRecordEntry
import net.corda.messagebus.db.datamodel.TransactionRecordEntry
import net.corda.messagebus.db.datamodel.TransactionState
import net.corda.messagebus.db.persistence.DBAccess
import net.corda.messaging.api.exception.CordaMessageAPIFatalException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatExceptionOfType
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mock
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.time.Duration
import java.time.Instant

internal class DBCordaConsumerImplTest {

    companion object {
        private const val topic = "topic"
        private val defaultConfig = SmartConfigImpl(ConfigFactory.parseString(
            """
        max.poll.records = 10
        max.poll.interval.ms = 100000
        group.id = group
        client.id = client
        auto.offset.reset = earliest
        """
        ), mock(), mock())

        private val serializedKey = "key".toByteArray()
        private val serializedValue = "value".toByteArray()

        private val partition0 = CordaTopicPartition(topic, 0)
        private val partition1 = CordaTopicPartition(topic, 1)
        private val partition2 = CordaTopicPartition(topic, 2)
    }

    @Mock
    val consumerGroup = mock<ConsumerGroup>()

    @Mock
    val dbAccess = mock<DBAccess>()

    private fun makeConsumer(config: SmartConfig = defaultConfig): DBCordaConsumerImpl<String, String> {
        val keyDeserializer = mock<CordaAvroDeserializer<String>>()
        val valueDeserializer = mock<CordaAvroDeserializer<String>>()
        whenever(keyDeserializer.deserialize(eq(serializedKey))).thenAnswer { "key" }
        whenever(valueDeserializer.deserialize(eq(serializedValue))).thenAnswer { "value" }
        return DBCordaConsumerImpl(
            config,
            dbAccess,
            consumerGroup,
            keyDeserializer,
            valueDeserializer,
            null
        )
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
            consumer.subscribe(topic, null)
        }
    }

    @Test
    fun `consumer doesn't allow assignment after subscription`() {
        val consumer = makeConsumer()
        val topicPartitions = setOf(partition0, partition1, partition2)

        consumer.subscribe(topic, null)

        assertThatExceptionOfType(CordaMessageAPIFatalException::class.java).isThrownBy {
            consumer.assign(topicPartitions)
        }
    }

    @Test
    fun `consumer poll returns the correct record`() {
        val fromOffset = ArgumentCaptor.forClass(Long::class.java)
        val timestamp = Instant.parse("2022-01-01T00:00:00.00Z")
        val pollResult = listOf(
            TopicRecordEntry(
                topic,
                0,
                0,
                serializedKey,
                serializedValue,
                TransactionRecordEntry("id", TransactionState.COMMITTED),
                timestamp
            )
        )
        val expectedRecord = CordaConsumerRecord(
            topic,
            0,
            0,
            "key",
            "value",
            timestamp.toEpochMilli()
        )

        whenever(dbAccess.getMaxCommittedPositions(any(), any())).thenAnswer { mapOf(partition0 to 0L) }
        whenever(dbAccess.readRecords(fromOffset.capture(), any(), any())).thenAnswer { pollResult }
        whenever(consumerGroup.getTopicPartitionsFor(any())).thenAnswer { setOf(partition0) }

        val consumer = makeConsumer()
        val test = consumer.poll(Duration.ZERO)
        consumer.poll(Duration.ZERO)
        assertThat(test.size).isEqualTo(1)
        assertThat(test.single()).isEqualToComparingFieldByField(expectedRecord)
    }

    @Test
    fun `consumer poll correctly increases offset for multiple records`() {
        val fromOffset = ArgumentCaptor.forClass(Long::class.java)
        val timestamp = Instant.parse("2022-01-01T00:00:00.00Z")
        val transactionRecord1 = TransactionRecordEntry("id", TransactionState.COMMITTED)

        val pollResult = listOf(
            TopicRecordEntry(topic, 0, 0, serializedKey, serializedValue, transactionRecord1, timestamp),
            TopicRecordEntry(topic, 0, 2, serializedKey, serializedValue, transactionRecord1, timestamp),
            TopicRecordEntry(topic, 0, 5, serializedKey, serializedValue, transactionRecord1, timestamp),
            TopicRecordEntry(topic, 0, 7, serializedKey, serializedValue, transactionRecord1, timestamp),
        )

        whenever(dbAccess.getMaxCommittedPositions(any(), any())).thenAnswer { mapOf(partition0 to 0L) }
        whenever(dbAccess.readRecords(fromOffset.capture(), any(), any())).thenAnswer { pollResult }
        whenever(consumerGroup.getTopicPartitionsFor(any())).thenAnswer { setOf(partition0) }

        val consumer = makeConsumer()
        consumer.poll(Duration.ZERO)
        assertThat(consumer.position(partition0)).isEqualTo(8)
    }

    @Test
    fun `consumer poll correctly increases offset`() {
        val timestamp = Instant.parse("2022-01-01T00:00:00.00Z")

        var partition0Offset = 0L
        var partition1Offset = 5L
        var partition2Offset = 10L

        fun nextRecord(partition: CordaTopicPartition, offset: Long) = listOf(
            TopicRecordEntry(
                topic,
                partition.partition,
                offset,
                serializedKey,
                serializedValue,
                TransactionRecordEntry("id", TransactionState.COMMITTED),
                timestamp
            )
        )

        whenever(consumerGroup.getTopicPartitionsFor(any())).thenAnswer { setOf(partition0, partition1, partition2) }
        whenever(dbAccess.readRecords(any(), eq(partition0), any())).thenAnswer { nextRecord(partition0, partition0Offset++) }
        whenever(dbAccess.readRecords(any(), eq(partition1), any())).thenAnswer { nextRecord(partition1, partition1Offset++) }
        whenever(dbAccess.readRecords(any(), eq(partition2), any())).thenAnswer { nextRecord(partition2, partition2Offset++) }
        whenever(dbAccess.getMaxCommittedPositions(any(), any())).thenAnswer {
            mapOf(
                partition0 to 0L,
                partition1 to 0L,
                partition2 to 0L,
            )
        }
        whenever(dbAccess.getLatestRecordOffset(any())).thenAnswer {
            mapOf(
                partition0 to partition0Offset,
                partition1 to partition1Offset,
                partition2 to partition2Offset,
            )
        }

        val consumer = makeConsumer()
        consumer.poll(Duration.ZERO)
        consumer.poll(Duration.ZERO)
        consumer.poll(Duration.ZERO)

        assertThat(consumer.position(partition0)).isEqualTo(1)
        assertThat(consumer.position(partition1)).isEqualTo(6)
        assertThat(consumer.position(partition2)).isEqualTo(11)

        consumer.poll(Duration.ZERO)
        consumer.poll(Duration.ZERO)
        consumer.poll(Duration.ZERO)

        assertThat(consumer.position(partition0)).isEqualTo(2)
        assertThat(consumer.position(partition1)).isEqualTo(7)
        assertThat(consumer.position(partition2)).isEqualTo(12)
    }

    @Test
    fun `consumer poll stops at uncommitted records`() {
        val fromOffset = ArgumentCaptor.forClass(Long::class.java)
        val timestamp = Instant.parse("2022-01-01T00:00:00.00Z")

        val transactionRecord1 = TransactionRecordEntry("id", TransactionState.COMMITTED)
        val transactionRecord2 = TransactionRecordEntry("id2", TransactionState.PENDING)

        val pollResult = listOf(
            TopicRecordEntry(topic, 0, 0, serializedKey, serializedValue, transactionRecord1, timestamp),
            TopicRecordEntry(topic, 0, 2, serializedKey, serializedValue, transactionRecord1, timestamp),
            TopicRecordEntry(topic, 0, 5, serializedKey, serializedValue, transactionRecord2, timestamp),
            TopicRecordEntry(topic, 0, 7, serializedKey, serializedValue, transactionRecord2, timestamp),
        )
        val expectedRecords = listOf(
            CordaConsumerRecord(topic, 0, 0, "key", "value", timestamp.toEpochMilli()),
            CordaConsumerRecord(topic, 0, 2, "key", "value", timestamp.toEpochMilli()),
        )

        whenever(dbAccess.getMaxCommittedPositions(any(), any())).thenAnswer { mapOf(partition0 to 0L) }
        whenever(dbAccess.readRecords(fromOffset.capture(), any(), any())).thenAnswer { pollResult }
        whenever(consumerGroup.getTopicPartitionsFor(any())).thenAnswer { setOf(partition0) }

        val consumer = makeConsumer()
        val test = consumer.poll(Duration.ZERO)
        assertThat(test.size).isEqualTo(2)
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
    fun `consumer without group id cannot subscribe`() {
        val keyDeserializer = mock<CordaAvroDeserializer<String>>()
        val valueDeserializer = mock<CordaAvroDeserializer<String>>()
        whenever(keyDeserializer.deserialize(eq(serializedKey))).thenAnswer { "key" }
        whenever(valueDeserializer.deserialize(eq(serializedValue))).thenAnswer { "value" }
        val consumer = DBCordaConsumerImpl(
            defaultConfig,
            dbAccess,
            null,
            keyDeserializer,
            valueDeserializer,
            null
        )

        assertThatExceptionOfType(CordaMessageAPIFatalException::class.java).isThrownBy {
            consumer.subscribe(topic)
        }
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
            val config = defaultConfig.withValue(AUTO_OFFSET_RESET, ConfigValueFactory.fromAnyRef(strategy.name))
            return DBCordaConsumerImpl(
                config,
                dbAccess,
                consumerGroup,
                keyDeserializer,
                valueDeserializer,
                null
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
        val earliestPositions = mapOf(partition0 to 1, partition1 to 3, partition2 to 6)
        val latestPositions = mapOf(partition0 to 10, partition1 to 30, partition2 to 60)
        whenever(dbAccess.getEarliestRecordOffset(any())).thenAnswer { earliestPositions }
        whenever(dbAccess.getLatestRecordOffset(any())).thenAnswer { latestPositions }

        val consumer = makeConsumer()

        assertThat(consumer.beginningOffsets(setOf(partition0, partition1, partition2))).isEqualTo(earliestPositions)
        assertThat(consumer.endOffsets(setOf(partition0, partition1, partition2))).isEqualTo(latestPositions)
    }
}
