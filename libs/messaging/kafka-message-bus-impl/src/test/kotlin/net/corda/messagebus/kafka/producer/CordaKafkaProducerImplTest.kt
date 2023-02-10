package net.corda.messagebus.kafka.producer

import net.corda.messagebus.api.consumer.CordaConsumerRecord
import net.corda.messagebus.api.producer.CordaProducer
import net.corda.messagebus.api.producer.CordaProducerRecord
import net.corda.messagebus.kafka.config.ResolvedConsumerConfig
import net.corda.messagebus.kafka.config.ResolvedProducerConfig
import net.corda.messagebus.kafka.consumer.CordaKafkaConsumerImpl
import net.corda.messaging.api.chunking.ChunkSerializerService
import net.corda.messaging.api.chunking.ConsumerChunkDeserializerService
import net.corda.messaging.api.exception.CordaMessageAPIFatalException
import net.corda.messaging.api.exception.CordaMessageAPIIntermittentException
import net.corda.messaging.api.exception.CordaMessageAPIProducerRequiresReset
import net.corda.messaging.kafka.subscription.generateMockConsumerRecordList
import org.apache.kafka.clients.consumer.CommitFailedException
import org.apache.kafka.clients.consumer.Consumer
import org.apache.kafka.clients.consumer.ConsumerGroupMetadata
import org.apache.kafka.clients.producer.Producer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.KafkaException
import org.apache.kafka.common.errors.InterruptException
import org.apache.kafka.common.errors.InvalidProducerEpochException
import org.apache.kafka.common.errors.ProducerFencedException
import org.apache.kafka.common.errors.TimeoutException
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito
import org.mockito.Mockito.atLeastOnce
import org.mockito.Mockito.doThrow
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class CordaKafkaProducerImplTest {

    private val transactionalConfig = ResolvedProducerConfig("clientId", true, "prefix")
    private val asyncConfig = ResolvedProducerConfig("clientIdAsync", false, "prefix")
    private val consumerConfig = ResolvedConsumerConfig("group", "clientId", "prefix")
    private val producer: Producer<Any, Any> = mock()
    private val consumer: Consumer<Any, Any> = mock()
    private val chunkSerializerService: ChunkSerializerService = mock()
    private val consumerChunkDeserializerService: ConsumerChunkDeserializerService<Any, Any> = mock()
    private val mockedCallback: CordaProducer.Callback = mock()
    private val cordaConsumer =
        CordaKafkaConsumerImpl(consumerConfig, consumer, null, consumerChunkDeserializerService, Any::class.java, { })
    private lateinit var cordaKafkaProducer: CordaKafkaProducerImpl

    private val record: CordaProducerRecord<Any, Any> = CordaProducerRecord("topic", "key", "value")

    @BeforeEach
    fun setup() {
        doReturn(ConsumerGroupMetadata("")).whenever(consumer).groupMetadata()
        cordaKafkaProducer = CordaKafkaProducerImpl(transactionalConfig, producer, chunkSerializerService)
    }

    @Test
    fun testSend() {
        val callback = mock<CordaProducer.Callback>()
        cordaKafkaProducer.send(record, callback)
        verify(producer).send(eq(ProducerRecord(transactionalConfig.topicPrefix + record.topic, record.key, record.value)), any())
    }

    @Test
    fun testSendWithError() {
        val callback = mock<CordaProducer.Callback>()
        doThrow(KafkaException("")).whenever(producer)
            .send(eq(ProducerRecord(transactionalConfig.topicPrefix + record.topic, record.key, record.value)), any())
        assertThrows<CordaMessageAPIIntermittentException> { cordaKafkaProducer.send(record, callback) }
        verify(producer).abortTransaction()
    }

    @Test
    fun testSendWithPartition() {
        val callback = mock<CordaProducer.Callback>()
        cordaKafkaProducer.send(record, 13, callback)
        verify(producer).send(
            eq(ProducerRecord(transactionalConfig.topicPrefix + record.topic, 13, record.key, record.value)),
            any()
        )
    }

    @Test
    fun testSendWithPartitionWithError() {
        val callback = mock<CordaProducer.Callback>()
        doThrow(KafkaException("")).whenever(producer)
            .send(eq(ProducerRecord(transactionalConfig.topicPrefix + record.topic, 13, record.key, record.value)), any())
        assertThrows<CordaMessageAPIIntermittentException> { cordaKafkaProducer.send(record, 13, callback) }
        verify(producer).abortTransaction()
    }

    @Test
    fun testSendRecords() {
        val records = listOf(record, record, record)
        val expectedPublishedRecords =
            records.map { ProducerRecord(transactionalConfig.topicPrefix + it.topic, null, it.key, it.value) }
        cordaKafkaProducer.sendRecords(listOf(record, record, record))
        verify(producer, times(3)).send(any(), anyOrNull())
        expectedPublishedRecords.forEach { verify(producer, atLeastOnce()).send(it, null) }
    }

    @Test
    fun testSendRecordsWithError() {
        doThrow(KafkaException("")).whenever(producer).send(any(), anyOrNull())
        assertThrows<CordaMessageAPIIntermittentException> {
            cordaKafkaProducer.sendRecords(
                listOf(
                    record,
                    record,
                    record
                )
            )
        }
        verify(producer).abortTransaction()
    }

    @Test
    fun testSendRecordsToPartitions() {
        val recordsWithPartitions = listOf((1 to record), (2 to record), (3 to record))
        val expectedPublishedRecords = recordsWithPartitions.map { (partition, record) ->
            ProducerRecord(transactionalConfig.topicPrefix + record.topic, partition, record.key, record.value)
        }

        cordaKafkaProducer.sendRecordsToPartitions(recordsWithPartitions)
        verify(producer, times(3)).send(any(), anyOrNull())
        expectedPublishedRecords.forEach { verify(producer, atLeastOnce()).send(it, null) }
    }

    @Test
    fun testSendRecordsToPartitionsWithError() {
        val recordsWithPartitions = listOf((1 to record), (2 to record), (3 to record))
        doThrow(KafkaException("")).whenever(producer).send(any(), anyOrNull())
        assertThrows<CordaMessageAPIIntermittentException> {
            cordaKafkaProducer.sendRecordsToPartitions(
                recordsWithPartitions
            )
        }
        verify(producer).abortTransaction()
    }

    @Test
    fun testBeginTransaction() {
        cordaKafkaProducer.beginTransaction()
        verify(producer, times(1)).beginTransaction()
    }

    @Test
    fun testBeginTransactionFatal() {
        doThrow(ProducerFencedException("")).whenever(producer).beginTransaction()
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
    fun testBeginTransactionIllegalState() {
        doThrow(IllegalStateException("")).whenever(producer).beginTransaction()
        assertThrows<CordaMessageAPIProducerRequiresReset> { cordaKafkaProducer.beginTransaction() }
        verify(producer, times(1)).beginTransaction()
    }

    @Test
    fun testBeginTransactionZombieProducerThrowsFatalException() {
        doThrow(ProducerFencedException("")).whenever(producer).beginTransaction()
        assertThrows<CordaMessageAPIFatalException> { cordaKafkaProducer.beginTransaction() }
        verify(producer, times(1)).beginTransaction()
    }

    @Test
    fun testBeginTransactionTimedOutProducerThrowsIntermittentException() {
        doThrow(InvalidProducerEpochException("")).whenever(producer).beginTransaction()
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
    fun testTryCommitTransactionRetry() {
        whenever(producer.commitTransaction()).thenThrow(TimeoutException()).thenThrow(InterruptException(""))
        val exception = assertThrows<CordaMessageAPIProducerRequiresReset> { cordaKafkaProducer.commitTransaction() }
        verify(producer, times(2)).commitTransaction()
        assertNotNull(exception.cause)
        assertInstanceOf(InterruptException::class.java, exception.cause)
    }

    @Test
    fun testTryCommitTransactionIntermittent() {
        doThrow(KafkaException()).whenever(producer).commitTransaction()
        assertThrows<CordaMessageAPIIntermittentException> { cordaKafkaProducer.commitTransaction() }
        verify(producer, times(1)).abortTransaction()
        verify(producer, times(1)).commitTransaction()
    }

    @Test
    fun testCommitTransactionZombieProducerThrowsFatalException() {
        doThrow(ProducerFencedException("")).whenever(producer).commitTransaction()
        assertThrows<CordaMessageAPIFatalException> { cordaKafkaProducer.commitTransaction() }
        verify(producer, times(1)).commitTransaction()
    }

    @Test
    fun testCommitTransactionTimedOutProducerThrowsIntermittentException() {
        doThrow(InvalidProducerEpochException("")).whenever(producer).commitTransaction()
        assertThrows<CordaMessageAPIIntermittentException> { cordaKafkaProducer.commitTransaction() }
        verify(producer, times(1)).commitTransaction()
    }

    @Test
    fun testAbortTransaction() {
        cordaKafkaProducer.abortTransaction()
        verify(producer, times(1)).abortTransaction()
    }

    @Test
    fun testAbortTransactionIntermittent() {
        doThrow(KafkaException()).whenever(producer).abortTransaction()
        assertThrows<CordaMessageAPIIntermittentException> { cordaKafkaProducer.abortTransaction() }
        verify(producer, times(1)).abortTransaction()
    }

    @Test
    fun testAbortTransactionZombieProducerThrowsFatalException() {
        doThrow(ProducerFencedException("")).whenever(producer).abortTransaction()
        assertThrows<CordaMessageAPIFatalException> { cordaKafkaProducer.abortTransaction() }
        verify(producer, times(1)).abortTransaction()
    }

    @Test
    fun testAbortTransactionTimedOutProducerThrowsIntermittentException() {
        doThrow(InvalidProducerEpochException("")).whenever(producer).abortTransaction()
        assertThrows<CordaMessageAPIIntermittentException> { cordaKafkaProducer.abortTransaction() }
        verify(producer, times(1)).abortTransaction()
    }

    @Test
    fun testSendAllOffsetsToTransactions() {
        cordaKafkaProducer.sendAllOffsetsToTransaction(cordaConsumer)
        verify(producer, times(1)).sendOffsetsToTransaction(any(), Mockito.any(ConsumerGroupMetadata::class.java))
    }

    @Test
    fun testSendAllOffsetsToTransactionsZombieProducerThrowsFatalException() {
        doThrow(ProducerFencedException("")).whenever(producer)
            .sendOffsetsToTransaction(any(), Mockito.any(ConsumerGroupMetadata::class.java))
        assertThrows<CordaMessageAPIFatalException> { cordaKafkaProducer.sendAllOffsetsToTransaction(cordaConsumer) }
        verify(producer, times(1)).sendOffsetsToTransaction(any(), Mockito.any(ConsumerGroupMetadata::class.java))
    }

    @Test
    fun testSendAllOffsetsToTransactionsIntermittent() {
        doThrow(CommitFailedException()).whenever(producer)
            .sendOffsetsToTransaction(any(), Mockito.any(ConsumerGroupMetadata::class.java))
        assertThrows<CordaMessageAPIIntermittentException> {
            cordaKafkaProducer.sendAllOffsetsToTransaction(
                cordaConsumer
            )
        }
        verify(producer, times(1)).sendOffsetsToTransaction(any(), Mockito.any(ConsumerGroupMetadata::class.java))
    }

    @Test
    fun testSendRecordOffsetsToTransaction() {
        val mockConsumerRecords = generateMockConsumerRecordList(3, "TOPIC1", 0) +
                generateMockConsumerRecordList(4, "TOPIC", 1) + generateMockConsumerRecordList(2, "TOPIC2", 0)
        val mockCordaConsumerRecords = mockConsumerRecords.map {
            CordaConsumerRecord(
                it.topic(),
                it.partition(),
                it.offset(),
                it.key(),
                it.value(),
                it.timestamp()
            )
        }

        cordaKafkaProducer.sendRecordOffsetsToTransaction(cordaConsumer, mockCordaConsumerRecords)
        verify(producer, times(1)).sendOffsetsToTransaction(any(), Mockito.any(ConsumerGroupMetadata::class.java))
    }

    @Test
    fun testSendRecordOffsetsToTransactionsZombieProducerThrowsFatalException() {
        doThrow(ProducerFencedException("")).whenever(producer)
            .sendOffsetsToTransaction(any(), Mockito.any(ConsumerGroupMetadata::class.java))
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
        doThrow(CommitFailedException()).whenever(producer)
            .sendOffsetsToTransaction(any(), Mockito.any(ConsumerGroupMetadata::class.java))
        val mockConsumerRecords = generateMockConsumerRecordList(3, "TOPIC1", 0)
        val mockCordaConsumerRecords = mockConsumerRecords.map {
            CordaConsumerRecord(
                it.topic(),
                it.partition(),
                it.offset(),
                it.key(),
                it.value(),
                it.timestamp()
            )
        }

        assertThrows<CordaMessageAPIIntermittentException> {
            cordaKafkaProducer.sendRecordOffsetsToTransaction(cordaConsumer, mockCordaConsumerRecords)
        }
        verify(producer, times(1)).sendOffsetsToTransaction(any(), Mockito.any(ConsumerGroupMetadata::class.java))
    }

    @Test
    fun testSendOffsetsZombieProducerThrowsFatalException() {
        doThrow(ProducerFencedException("")).whenever(producer)
            .sendOffsetsToTransaction(any(), Mockito.any(ConsumerGroupMetadata::class.java))
        assertThrows<CordaMessageAPIFatalException> { cordaKafkaProducer.sendAllOffsetsToTransaction(cordaConsumer) }
        verify(producer, times(1)).sendOffsetsToTransaction(any(), Mockito.any(ConsumerGroupMetadata::class.java))
    }

    @Test
    fun testSendOffsetsTimedOutProducerThrowsIntermittentException() {
        doThrow(InvalidProducerEpochException("")).whenever(producer)
            .sendOffsetsToTransaction(any(), Mockito.any(ConsumerGroupMetadata::class.java))
        assertThrows<CordaMessageAPIIntermittentException> {
            cordaKafkaProducer.sendAllOffsetsToTransaction(
                cordaConsumer
            )
        }
        verify(producer, times(1)).sendOffsetsToTransaction(any(), Mockito.any(ConsumerGroupMetadata::class.java))
    }

    @Test
    fun `Trying to send chunks with an async producer throws a fatal exception and executes callback`() {
        cordaKafkaProducer = CordaKafkaProducerImpl(asyncConfig, producer, chunkSerializerService)

        whenever(chunkSerializerService.generateChunkedRecords(any())).thenReturn(listOf(record, record))
        assertThrows<CordaMessageAPIFatalException> {
            cordaKafkaProducer.send(record, mockedCallback)
        }
        verify(mockedCallback, times(1)).onCompletion(any())
    }

    @Test
    fun `Trying to send chunks to partition with an async producer throws a fatal exception and executes callback`() {
        cordaKafkaProducer = CordaKafkaProducerImpl(asyncConfig, producer, chunkSerializerService)
        whenever(chunkSerializerService.generateChunkedRecords(any())).thenReturn(listOf(record, record))
        assertThrows<CordaMessageAPIFatalException> {
            cordaKafkaProducer.send(record, 1, mockedCallback)
        }
        verify(mockedCallback, times(1)).onCompletion(any())
    }

    @Test
    fun `Send large records chunks to partition with a transactional producer sends chunks`() {
        cordaKafkaProducer = CordaKafkaProducerImpl(transactionalConfig, producer, chunkSerializerService)
        whenever(chunkSerializerService.generateChunkedRecords(any())).thenReturn(listOf(record, record))
        cordaKafkaProducer.sendRecords(listOf(record))
        verify(producer, times(2)).send(any())
    }

    @Test
    fun `Send large obj to partition with a transactional producer sends chunks`() {
        whenever(chunkSerializerService.generateChunkedRecords(any())).thenReturn(listOf(record, record))
        cordaKafkaProducer.sendRecordsToPartitions(listOf(Pair(1, record)))
        verify(producer, times(2)).send(any())
    }

    @Test
    fun `Send large obj with a transactional producer and no callback sends chunks`() {
        whenever(chunkSerializerService.generateChunkedRecords(any())).thenReturn(listOf(record, record))
        cordaKafkaProducer.send(record, null)
        verify(producer, times(2)).send(any())
    }

    @Test
    fun testCatchinggIntermittentExceptionsCatchesProducerRestartErrors() {
        // This test ensures that clients of producers who wish to restart their producers on any intermittent error
        // need only catch the more generic exception. If this relationship ever changes, this test would fail.
        // Corda Subscriptions rely on this behaviour.
        try {
            throw CordaMessageAPIProducerRequiresReset("")
        } catch (ex: CordaMessageAPIIntermittentException) {
            // do nothing
        } catch (t: Throwable) {
            fail("Catching CordaMessageAPIIntermittentException does not include producer restart exceptions")
        }
    }

    @Test
    fun testClose() {
        cordaKafkaProducer.close()
        verify(producer, times(1)).close()
    }
}
