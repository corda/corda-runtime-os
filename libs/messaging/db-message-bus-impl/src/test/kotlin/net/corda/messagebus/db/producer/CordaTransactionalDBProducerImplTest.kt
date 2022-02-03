package net.corda.messagebus.db.producer

import net.corda.messagebus.api.consumer.CordaConsumerRecord
import net.corda.messagebus.api.producer.CordaProducer
import net.corda.messagebus.api.producer.CordaProducerRecord
import net.corda.messagebus.db.datamodel.TopicRecordEntry
import net.corda.messagebus.db.datamodel.TransactionRecordEntry
import net.corda.messagebus.db.persistence.DBAccess
import net.corda.messaging.api.exception.CordaMessageAPIFatalException
import net.corda.messaging.api.records.Record
import net.corda.messaging.emulation.topic.service.TopicService
import net.corda.schema.registry.AvroSchemaRegistry
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatExceptionOfType
import org.junit.jupiter.api.Test
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import java.nio.ByteBuffer

internal class CordaTransactionalDBProducerImplTest {

    private val topic = "topic"
    private val key = "key"
    private val value = "value"
    private val serializedKey = key.toByteArray()
    private val serializedValue = value.toByteArray()

    @Test
    fun `transactional producer doesn't allow sends when not transaction`() {
        val dbAccess: DBAccess = mock()
        val topicService: TopicService = mock()

        val producer = CordaTransactionalDBProducerImpl(
            mock(),
            topicService,
            dbAccess
        ) as CordaProducer

        val record = CordaProducerRecord(topic, key, value)

        assertThatExceptionOfType(CordaMessageAPIFatalException::class.java).isThrownBy {
            producer.send(record, null)
        }
        assertThatExceptionOfType(CordaMessageAPIFatalException::class.java).isThrownBy {
            producer.send(record, 0, null)
        }
        assertThatExceptionOfType(CordaMessageAPIFatalException::class.java).isThrownBy {
            producer.sendRecords(listOf(record))
        }
        assertThatExceptionOfType(CordaMessageAPIFatalException::class.java).isThrownBy {
            producer.sendRecordsToPartitions(listOf(0 to record))
        }
        assertThatExceptionOfType(CordaMessageAPIFatalException::class.java).isThrownBy {
            producer.sendRecordOffsetsToTransaction(mock(), listOf(CordaConsumerRecord(topic, 0, 0, key, value, 0)))
        }
        assertThatExceptionOfType(CordaMessageAPIFatalException::class.java).isThrownBy {
            producer.sendAllOffsetsToTransaction(mock())
        }
        assertThatExceptionOfType(CordaMessageAPIFatalException::class.java).isThrownBy {
            producer.commitTransaction()
        }
        assertThatExceptionOfType(CordaMessageAPIFatalException::class.java).isThrownBy {
            producer.abortTransaction()
        }
        // If we didn't commit we shouldn't send to the cache either
        verifyNoInteractions(topicService)
    }

    @Test
    fun `transactional producer sends correct entry to database and topic`() {
        val dbAccess: DBAccess = mock()
        whenever(dbAccess.getTopicPartitionMap()).thenReturn(mapOf(topic to 1))
        val topicService: TopicService = mock()
        whenever(topicService.getLatestOffsets(eq(topic))).thenReturn(mapOf(1 to 5))
        val schemaRegistry: AvroSchemaRegistry = mock()
        whenever(schemaRegistry.serialize(eq(key))).thenReturn(ByteBuffer.wrap(serializedKey))
        whenever(schemaRegistry.serialize(eq(value))).thenReturn(ByteBuffer.wrap(serializedValue))
        val callback: CordaProducer.Callback = mock()

        val producer = CordaTransactionalDBProducerImpl(schemaRegistry, topicService, dbAccess)
        val cordaRecord = CordaProducerRecord(topic, key, value)

        producer.beginTransaction()
        producer.send(cordaRecord, callback)
        producer.commitTransaction()

        val dbRecordList = argumentCaptor<List<TopicRecordEntry>>()
        val dbTransaction = argumentCaptor<TransactionRecordEntry>()
        val dbTransactionId = argumentCaptor<String>()
        // For transactions the records must *not* be immediately visible
        verify(dbAccess).writeRecords(dbRecordList.capture())
        verify(dbAccess).writeTransactionRecord(dbTransaction.capture())
        verify(dbAccess).makeRecordsVisible(dbTransactionId.capture())
        verify(callback).onCompletion(null)
        val record = dbRecordList.firstValue.single()
        assertThat(record.topic).isEqualTo(topic)
        assertThat(record.key).isEqualTo(serializedKey)
        assertThat(record.value).isEqualTo(serializedValue)
        assertThat(record.offset).isEqualTo(5)
        assertThat(record.partition).isEqualTo(1)
        assertThat(record.transactionId).isNotEmpty()
        assertThat(record.transactionId).isNotEqualTo(CordaAtomicDBProducerImpl.ATOMIC_TRANSACTION)

        val initialTransactionRecord = dbTransaction.allValues.single()
        assertThat(record.transactionId).isEqualTo(initialTransactionRecord.transactionId)
        assertThat(initialTransactionRecord.visible).isFalse()

        assertThat(dbTransactionId.allValues.single()).isEqualTo(initialTransactionRecord.transactionId)

        val topicRecordList = argumentCaptor<List<Record<*, *>>>()
        verify(topicService).addRecordsToPartition(topicRecordList.capture() , eq(1))
        val topicRecord = topicRecordList.firstValue.single()
        assertThat(topicRecord.topic).isEqualTo(topic)
        assertThat(topicRecord.key).isEqualTo(key)
        assertThat(topicRecord.value).isEqualTo(value)
    }
}
