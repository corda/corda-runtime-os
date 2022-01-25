package net.corda.messagebus.kafka.producer

import com.typesafe.config.Config
import net.corda.libs.configuration.schema.messaging.TOPIC_PREFIX_PATH
import net.corda.messagebus.api.consumer.CordaConsumerRecord
import net.corda.messagebus.db.util.CordaProducerRecord
import net.corda.messagebus.kafka.consumer.CordaKafkaConsumerImpl
import net.corda.messaging.api.exception.CordaMessageAPIFatalException
import net.corda.messaging.api.exception.CordaMessageAPIIntermittentException
import net.corda.messaging.kafka.subscription.generateMockConsumerRecordList
import org.apache.kafka.clients.consumer.CommitFailedException
import org.apache.kafka.clients.consumer.Consumer
import org.apache.kafka.clients.consumer.ConsumerGroupMetadata
import org.apache.kafka.clients.producer.Producer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.KafkaException
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito
import org.mockito.Mockito.atLeastOnce
import org.mockito.Mockito.doThrow
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Duration

class CordaKafkaProducerImplTest {

    private val config: Config = mock()
    private val producer: Producer<Any, Any> = mock()
    private val consumer: Consumer<Any, Any> = mock()
    private val cordaConsumer: CordaKafkaConsumerImpl<*, *> = CordaKafkaConsumerImpl(config, consumer, null)
    private lateinit var cordaKafkaProducer: CordaKafkaProducerImpl

    private val record: CordaProducerRecord<Any, Any> = CordaProducerRecord("topic", "key", "value")

    @BeforeEach
    fun setup() {
        doReturn(ConsumerGroupMetadata("")).whenever(consumer).groupMetadata()
        doReturn("").whenever(config).getString(TOPIC_PREFIX_PATH)
        cordaKafkaProducer = CordaKafkaProducerImpl(config, producer)
    }

    @Test
    fun testSendRecord() {
        cordaKafkaProducer.sendRecords(listOf(record, record, record))
        verify(producer, times(3)).send(any())
    }

    @Test
    fun testSendRecordsToPartitions() {
        val recordsWithPartitions = listOf((1 to record), (2 to record), (3 to record))
        val expectedPublishedRecords = recordsWithPartitions.map { (partition, record) ->
            ProducerRecord(record.topic, partition, record.key, record.value)
        }

        cordaKafkaProducer.sendRecordsToPartitions(recordsWithPartitions)
        verify(producer, times(3)).send(any())
        expectedPublishedRecords.forEach { verify(producer, atLeastOnce()).send(it) }
    }

    @Test
    fun testBeginTransaction() {
        cordaKafkaProducer.beginTransaction()
        verify(producer, times(1)).beginTransaction()
    }

    @Test
    fun testBeginTransactionFatal() {
        doThrow(IllegalStateException()).whenever(producer).beginTransaction()
        assertThrows<CordaMessageAPIFatalException> { cordaKafkaProducer.beginTransaction() }
        verify(producer, times(1)).beginTransaction()
    }

    @Test
    fun testBeginTransactionIntermittent() {
        doThrow(KafkaException()).whenever(producer).beginTransaction()
        assertThrows<CordaMessageAPIIntermittentException> { cordaKafkaProducer.beginTransaction() }
        verify(producer, times(1)).beginTransaction()
    }

    @Test
    fun testTryCommitTransaction() {
        cordaKafkaProducer.commitTransaction()
        verify(producer, times(1)).commitTransaction()
        verify(producer, times(0)).abortTransaction()
    }

    @Test
    fun testTryCommitTransactionFatal() {
        doThrow(IllegalStateException()).whenever(producer).commitTransaction()
        assertThrows<CordaMessageAPIFatalException> { cordaKafkaProducer.commitTransaction() }
        verify(producer, times(1)).commitTransaction()
    }

    @Test
    fun testTryCommitTransactionIntermittent() {
        doThrow(KafkaException()).whenever(producer).commitTransaction()
        assertThrows<CordaMessageAPIIntermittentException> { cordaKafkaProducer.commitTransaction() }
        verify(producer, times(1)).abortTransaction()
        verify(producer, times(1)).commitTransaction()
    }

    @Test
    fun testAbortTransaction() {
        cordaKafkaProducer.abortTransaction()
        verify(producer, times(1)).abortTransaction()
    }

    @Test
    fun testAbortTransactionFatal() {
        doThrow(IllegalStateException()).whenever(producer).abortTransaction()
        assertThrows<CordaMessageAPIFatalException> { cordaKafkaProducer.abortTransaction() }
        verify(producer, times(1)).abortTransaction()
    }

    @Test
    fun testAbortTransactionIntermittent() {
        doThrow(KafkaException()).whenever(producer).abortTransaction()
        assertThrows<CordaMessageAPIIntermittentException> { cordaKafkaProducer.abortTransaction() }
        verify(producer, times(1)).abortTransaction()
    }

    @Test
    fun testSendAllOffsetsToTransactions() {
        cordaKafkaProducer.sendAllOffsetsToTransaction(cordaConsumer)
        verify(producer, times(1)).sendOffsetsToTransaction(any(), Mockito.any(ConsumerGroupMetadata::class.java))
    }

    @Test
    fun testSendAllOffsetsToTransactionsFatal() {
        doThrow(IllegalStateException()).whenever(producer).sendOffsetsToTransaction(any(), Mockito.any(ConsumerGroupMetadata::class.java))
        assertThrows<CordaMessageAPIFatalException> { cordaKafkaProducer.sendAllOffsetsToTransaction(cordaConsumer) }
        verify(producer, times(1)).sendOffsetsToTransaction(any(), Mockito.any(ConsumerGroupMetadata::class.java))
    }

    @Test
    fun testSendAllOffsetsToTransactionsIntermittent() {
        doThrow(CommitFailedException()).whenever(producer).sendOffsetsToTransaction(any(), Mockito.any(ConsumerGroupMetadata::class.java))
        assertThrows<CordaMessageAPIIntermittentException> { cordaKafkaProducer.sendAllOffsetsToTransaction(cordaConsumer) }
        verify(producer, times(1)).sendOffsetsToTransaction(any(), Mockito.any(ConsumerGroupMetadata::class.java))
    }

    @Test
    fun testSendRecordOffsetsToTransaction() {
        val mockConsumerRecords = generateMockConsumerRecordList(3, "TOPIC1", 0) +
            generateMockConsumerRecordList(4, "TOPIC", 1) + generateMockConsumerRecordList(2, "TOPIC2", 0)
        val mockCordaConsumerRecords = mockConsumerRecords.map { CordaConsumerRecord(
            it.topic(),
            it.partition(),
            it.offset(),
            it.key(),
            it.value(),
            it.timestamp()
        ) }

        cordaKafkaProducer.sendRecordOffsetsToTransaction(cordaConsumer, mockCordaConsumerRecords)
        verify(producer, times(1)).sendOffsetsToTransaction(any(), Mockito.any(ConsumerGroupMetadata::class.java))
    }

    @Test
    fun testSendRecordOffsetsToTransactionsFatal() {
        doThrow(IllegalStateException()).whenever(producer).sendOffsetsToTransaction(any(), Mockito.any(ConsumerGroupMetadata::class.java))
        assertThrows<CordaMessageAPIFatalException> {
            cordaKafkaProducer.sendRecordOffsetsToTransaction(
                cordaConsumer,
                generateMockConsumerRecordList(3, "TOPIC1", 0).map {
                    CordaConsumerRecord(it.topic(), it.partition(), it.offset(), it.key(), it.value(), it.timestamp())
                }
            )
        }
        verify(producer, times(1))
            .sendOffsetsToTransaction(any(), Mockito.any(ConsumerGroupMetadata::class.java))
    }

    @Test
    fun testSendRecordOffsetsToTransactionsIntermittent() {
        doThrow(CommitFailedException()).whenever(producer).sendOffsetsToTransaction(any(), Mockito.any(ConsumerGroupMetadata::class.java))
        val mockConsumerRecords = generateMockConsumerRecordList(3, "TOPIC1", 0)
        val mockCordaConsumerRecords = mockConsumerRecords.map { CordaConsumerRecord(
            it.topic(),
            it.partition(),
            it.offset(),
            it.key(),
            it.value(),
            it.timestamp()
        ) }

        assertThrows<CordaMessageAPIIntermittentException> {
            cordaKafkaProducer.sendRecordOffsetsToTransaction(cordaConsumer, mockCordaConsumerRecords)
        }
        verify(producer, times(1)).sendOffsetsToTransaction(any(), Mockito.any(ConsumerGroupMetadata::class.java))
    }

    @Test
    fun testClose() {
        cordaKafkaProducer.close()
        verify(producer, times(1)).close(Mockito.any(Duration::class.java))
    }
}
