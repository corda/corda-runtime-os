package net.corda.messagebus.db.producer

import net.corda.messagebus.api.CordaTopicPartition
import net.corda.messagebus.api.consumer.CordaConsumerRecord
import net.corda.messagebus.api.producer.CordaProducer
import net.corda.messagebus.api.producer.CordaProducerRecord
import net.corda.messagebus.db.datamodel.TopicRecordEntry
import net.corda.messagebus.db.datamodel.TransactionRecordEntry
import net.corda.messagebus.db.datamodel.TransactionState
import net.corda.messagebus.db.persistence.DBAccess
import net.corda.messagebus.db.producer.CordaAtomicDBProducerImpl.Companion.ATOMIC_TRANSACTION
import net.corda.messaging.api.exception.CordaMessageAPIFatalException
import net.corda.schema.registry.AvroSchemaRegistry
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatExceptionOfType
import org.junit.jupiter.api.Test
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
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

        val producer = CordaTransactionalDBProducerImpl(
            mock(),
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
    }

    @Test
    fun `transactional producer correctly handles abort`() {
        val dbAccess: DBAccess = mock()
        whenever(dbAccess.getTopicPartitionMap()).thenReturn(mapOf(topic to 1))
        val schemaRegistry: AvroSchemaRegistry = mock()
        whenever(schemaRegistry.serialize(eq(key))).thenReturn(ByteBuffer.wrap(serializedKey))
        whenever(schemaRegistry.serialize(eq(value))).thenReturn(ByteBuffer.wrap(serializedValue))
        val callback: CordaProducer.Callback = mock()

        val producer = CordaTransactionalDBProducerImpl(schemaRegistry, dbAccess)
        val cordaRecord = CordaProducerRecord(topic, key, value)

        producer.beginTransaction()
        producer.send(cordaRecord, callback)
        producer.abortTransaction()

        val dbTransaction = argumentCaptor<TransactionRecordEntry>()
        verify(dbAccess).writeTransactionRecord(dbTransaction.capture())

        val initialTransactionRecord = dbTransaction.allValues.single()
        verify(dbAccess).setTransactionRecordState(eq(initialTransactionRecord.transactionId), eq(TransactionState.ABORTED))
    }

    @Test
    fun `transactional producer sends correct entry to database`() {
        val dbAccess: DBAccess = mock()
        whenever(dbAccess.getTopicPartitionMap()).thenReturn(mapOf(topic to 1))
        whenever(dbAccess.getMaxOffsetsPerTopicPartition()).thenReturn(mapOf(CordaTopicPartition(topic, 0) to 5))
        val schemaRegistry: AvroSchemaRegistry = mock()
        whenever(schemaRegistry.serialize(eq(key))).thenReturn(ByteBuffer.wrap(serializedKey))
        whenever(schemaRegistry.serialize(eq(value))).thenReturn(ByteBuffer.wrap(serializedValue))
        val callback: CordaProducer.Callback = mock()

        val producer = CordaTransactionalDBProducerImpl(schemaRegistry, dbAccess)
        val cordaRecord = CordaProducerRecord(topic, key, value)

        producer.beginTransaction()
        producer.send(cordaRecord, callback)
        producer.commitTransaction()

        val dbRecordList = argumentCaptor<List<TopicRecordEntry>>()
        val dbTransaction = argumentCaptor<TransactionRecordEntry>()
        // For transactions the records must *not* be immediately visible
        verify(dbAccess).writeRecords(dbRecordList.capture())
        verify(dbAccess).writeTransactionRecord(dbTransaction.capture())
        verify(callback).onCompletion(null)
        val record = dbRecordList.firstValue.single()
        assertThat(record.topic).isEqualTo(topic)
        assertThat(record.key).isEqualTo(serializedKey)
        assertThat(record.value).isEqualTo(serializedValue)
        assertThat(record.recordOffset).isEqualTo(6)
        assertThat(record.partition).isEqualTo(0)
        assertThat(record.transactionId).isNotEqualTo(ATOMIC_TRANSACTION)

        val initialTransactionRecord = dbTransaction.allValues.single()
        assertThat(record.transactionId).isEqualTo(initialTransactionRecord)
        assertThat(initialTransactionRecord.state).isEqualTo(TransactionState.PENDING)

        verify(dbAccess).setTransactionRecordState(eq(initialTransactionRecord.transactionId), eq(TransactionState.COMMITTED))
    }
}
