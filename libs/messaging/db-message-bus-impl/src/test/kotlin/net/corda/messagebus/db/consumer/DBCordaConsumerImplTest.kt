package net.corda.messagebus.db.consumer

import com.typesafe.config.ConfigFactory
import net.corda.data.CordaAvroDeserializer
import net.corda.messagebus.api.CordaTopicPartition
import net.corda.messagebus.api.consumer.CordaConsumerRecord
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
import java.time.Instant

internal class DBCordaConsumerImplTest {

    companion object {
        private const val topic = "topic"
        private val config = ConfigFactory.parseString(
            """
        max.poll.records = 10
        max.poll.interval.ms = 100000
        group.id = group
        client.id = client
        auto.offset.reset = earliest
        """
        )

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

    private fun makeConsumer(): DBCordaConsumerImpl<String, String> {
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

        whenever(dbAccess.getMaxOffsetsPerTopicPartition()).thenAnswer { mapOf(partition0 to 0L) }
        whenever(dbAccess.readRecords(fromOffset.capture(), any(), any())).thenAnswer { pollResult }
        whenever(consumerGroup.getTopicPartitionsFor(any())).thenAnswer { setOf(partition0) }

        val consumer = makeConsumer()
        val test = consumer.poll()
        consumer.poll()
        assertThat(test.size).isEqualTo(1)
        assertThat(test.single()).isEqualToComparingFieldByField(expectedRecord)
    }

    @Test
    fun `consumer poll correctly increases offset`() {
        val fromOffset = ArgumentCaptor.forClass(Long::class.java)
        val timestamp = Instant.parse("2022-01-01T00:00:00.00Z")
        var recordOffsetCounter = 0L

        whenever(consumerGroup.getTopicPartitionsFor(any())).thenAnswer { setOf(partition0, partition1, partition2) }
        whenever(dbAccess.readRecords(fromOffset.capture(), any(), any())).thenAnswer {
            listOf(
                TopicRecordEntry(
                    topic,
                    0,
                    recordOffsetCounter++,
                    serializedKey,
                    serializedValue,
                    TransactionRecordEntry("id", TransactionState.COMMITTED),
                    timestamp
                )
            )
        }
        whenever(dbAccess.getMaxOffsetsPerTopicPartition()).thenAnswer {
            mapOf(
                partition0 to 0L,
                partition1 to 5L,
                partition2 to 10,
            )
        }

        val consumer = makeConsumer()
        consumer.poll()
        consumer.poll()
        consumer.poll()

        assertThat(fromOffset.allValues[0]).isEqualTo(0)
        assertThat(fromOffset.allValues[1]).isEqualTo(5)
        assertThat(fromOffset.allValues[2]).isEqualTo(10)

        consumer.poll()
        consumer.poll()
        consumer.poll()

        assertThat(fromOffset.allValues[3]).isEqualTo(1)
        assertThat(fromOffset.allValues[4]).isEqualTo(6)
        assertThat(fromOffset.allValues[5]).isEqualTo(11)

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

        whenever(dbAccess.getMaxOffsetsPerTopicPartition()).thenAnswer { mapOf(partition0 to 0L) }
        whenever(dbAccess.readRecords(fromOffset.capture(), any(), any())).thenAnswer { pollResult }
        whenever(consumerGroup.getTopicPartitionsFor(any())).thenAnswer { setOf(partition0) }

        val consumer = makeConsumer()
        val test = consumer.poll()
        consumer.poll()
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
            config,
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

}
