package net.corda.messaging.kafka.producer.wrapper.impl

import com.typesafe.config.Config
import net.corda.messaging.api.exception.CordaMessageAPIFatalException
import net.corda.messaging.api.exception.CordaMessageAPIIntermittentException
import net.corda.messaging.api.records.Record
import org.apache.kafka.clients.consumer.CommitFailedException
import org.apache.kafka.clients.consumer.Consumer
import org.apache.kafka.clients.consumer.ConsumerGroupMetadata
import org.apache.kafka.clients.producer.Producer
import org.apache.kafka.common.KafkaException
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito
import org.mockito.kotlin.*
import java.time.Duration

class CordaKafkaProducerImplTest {

    private val config : Config = mock()
    private val producer : Producer<Any, Any> = mock()
    private val consumer: Consumer<*, *> = mock()
    private lateinit var cordaKafkaProducer : CordaKafkaProducerImpl

    private val record: Record<Any, Any> = Record("topic", "key", "value")


    @BeforeEach
    fun setup () {
        doReturn(ConsumerGroupMetadata("")).whenever(consumer).groupMetadata()
        cordaKafkaProducer = CordaKafkaProducerImpl(config, producer)
    }

    @Test
    fun testSendRecord() {
        cordaKafkaProducer.sendRecords(listOf(record, record, record))
        verify(producer, times(3)).send(any())
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
        cordaKafkaProducer.tryCommitTransaction()
        verify(producer, times(1)).commitTransaction()
        verify(producer, times(0)).abortTransaction()
    }

    @Test
    fun testTryCommitTransactionFatal() {
        doThrow(IllegalStateException()).whenever(producer).commitTransaction()
        assertThrows<CordaMessageAPIFatalException> { cordaKafkaProducer.tryCommitTransaction() }
        verify(producer, times(1)).commitTransaction()
    }

    @Test
    fun testTryCommitTransactionIntermittent() {
        doThrow(KafkaException()).whenever(producer).commitTransaction()
        assertThrows<CordaMessageAPIIntermittentException> { cordaKafkaProducer.tryCommitTransaction() }
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
    fun testSendOffsetsToTransactions() {
        cordaKafkaProducer.sendAllOffsetsToTransaction(consumer)
        verify(producer, times(1)).sendOffsetsToTransaction(any(), Mockito.any(ConsumerGroupMetadata::class.java))
    }

    @Test
    fun testSendOffsetsToTransactionsFatal() {
        doThrow(IllegalStateException()).whenever(producer).sendOffsetsToTransaction(any(), Mockito.any(ConsumerGroupMetadata::class.java))
        assertThrows<CordaMessageAPIFatalException> { cordaKafkaProducer.sendAllOffsetsToTransaction(consumer) }
        verify(producer, times(1)).sendOffsetsToTransaction(any(), Mockito.any(ConsumerGroupMetadata::class.java))
    }

    @Test
    fun testSendOffsetsToTransactionsIntermittent() {
        doThrow(CommitFailedException()).whenever(producer).sendOffsetsToTransaction(any(), Mockito.any(ConsumerGroupMetadata::class.java))
        assertThrows<CordaMessageAPIIntermittentException> { cordaKafkaProducer.sendAllOffsetsToTransaction(consumer) }
        verify(producer, times(1)).sendOffsetsToTransaction(any(), Mockito.any(ConsumerGroupMetadata::class.java))
    }

    @Test
    fun testSendRecordOffsetToTransactions() {
        cordaKafkaProducer.sendRecordOffsetToTransaction(consumer, mock())
        verify(producer, times(1)).sendOffsetsToTransaction(any(), Mockito.any(ConsumerGroupMetadata::class.java))
    }

    @Test
    fun testSendRecordOffsetToTransactionsFatal() {
        doThrow(IllegalStateException()).whenever(producer).sendOffsetsToTransaction(any(), Mockito.any(ConsumerGroupMetadata::class.java))
        assertThrows<CordaMessageAPIFatalException> { cordaKafkaProducer.sendRecordOffsetToTransaction(consumer, mock()) }
        verify(producer, times(1)).sendOffsetsToTransaction(any(), Mockito.any(ConsumerGroupMetadata::class.java))
    }

    @Test
    fun testSendRecordOffsetToTransactionsIntermittent() {
        doThrow(CommitFailedException()).whenever(producer).sendOffsetsToTransaction(any(), Mockito.any(ConsumerGroupMetadata::class.java))
        assertThrows<CordaMessageAPIIntermittentException> { cordaKafkaProducer.sendRecordOffsetToTransaction(consumer, mock()) }
        verify(producer, times(1)).sendOffsetsToTransaction(any(), Mockito.any(ConsumerGroupMetadata::class.java))
    }

    @Test
    fun testClose() {
        cordaKafkaProducer.close()
        verify(producer, times(1)).close(Mockito.any(Duration::class.java))
    }
}