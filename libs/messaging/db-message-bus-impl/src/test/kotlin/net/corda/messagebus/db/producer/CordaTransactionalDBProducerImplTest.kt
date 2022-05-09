package net.corda.messagebus.db.producer

import net.corda.data.CordaAvroSerializer
import net.corda.messagebus.api.CordaTopicPartition
import net.corda.messagebus.api.consumer.CordaConsumerRecord
import net.corda.messagebus.api.producer.CordaProducer
import net.corda.messagebus.api.producer.CordaProducerRecord
import net.corda.messagebus.db.datamodel.TopicRecordEntry
import net.corda.messagebus.db.datamodel.TransactionRecordEntry
import net.corda.messagebus.db.datamodel.TransactionState
import net.corda.messagebus.db.persistence.DBAccess
import net.corda.messagebus.db.persistence.DBAccess.Companion.ATOMIC_TRANSACTION
import net.corda.messagebus.db.util.WriteOffsets
import net.corda.messaging.api.exception.CordaMessageAPIFatalException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatExceptionOfType
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

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
            dbAccess,
            mock()
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
        whenever(dbAccess.getTopicPartitionMapFor(any())).thenReturn(setOf(CordaTopicPartition(topic, 1)))
        val serializer = mock<CordaAvroSerializer<Any>>()
        whenever(serializer.serialize(eq(key))).thenReturn(serializedKey)
        whenever(serializer.serialize(eq(value))).thenReturn(serializedValue)
        val callback: CordaProducer.Callback = mock()

        val producer = CordaTransactionalDBProducerImpl(serializer, dbAccess, mock())
        val cordaRecord = CordaProducerRecord(topic, key, value)

        producer.beginTransaction()
        producer.send(cordaRecord, callback)
        producer.abortTransaction()

        val dbTransaction = argumentCaptor<TransactionRecordEntry>()
        verify(dbAccess).writeTransactionRecord(dbTransaction.capture())

        val initialTransactionRecord = dbTransaction.allValues.single()
        verify(dbAccess).setTransactionRecordState(
            eq(initialTransactionRecord.transactionId),
            eq(TransactionState.ABORTED)
        )
    }

    @Test
    fun `transactional producer sends correct entry to database`() {
        val dbAccess: DBAccess = mock()
        whenever(dbAccess.getTopicPartitionMapFor(any())).thenReturn(setOf(CordaTopicPartition(topic, 1)))
        val writeOffsets = mock<WriteOffsets>() {
            on{ getNextOffsetFor(any()) }.thenReturn(6)
        }
        val serializer = mock<CordaAvroSerializer<Any>>()
        whenever(serializer.serialize(eq(key))).thenReturn(serializedKey)
        whenever(serializer.serialize(eq(value))).thenReturn(serializedValue)
        val callback: CordaProducer.Callback = mock()

        val producer = CordaTransactionalDBProducerImpl(serializer, dbAccess, writeOffsets)
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

        verify(dbAccess).setTransactionRecordState(
            eq(initialTransactionRecord.transactionId),
            eq(TransactionState.COMMITTED)
        )
    }

    @Test
    fun `producer correctly closes down dbAccess when closed`() {
        val dbAccess: DBAccess = mock()
        val producer = CordaTransactionalDBProducerImpl(mock(), dbAccess, mock())
        producer.close()
        verify(dbAccess).close()
    }
}
