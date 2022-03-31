package net.corda.messagebus.db.producer

import net.corda.data.CordaAvroSerializer
import net.corda.messagebus.api.CordaTopicPartition
import net.corda.messagebus.api.producer.CordaProducer
import net.corda.messagebus.api.producer.CordaProducerRecord
import net.corda.messagebus.db.datamodel.TopicRecordEntry
import net.corda.messagebus.db.persistence.DBAccess
import net.corda.messagebus.db.persistence.DBAccess.Companion.ATOMIC_TRANSACTION
import net.corda.messaging.api.exception.CordaMessageAPIFatalException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatExceptionOfType
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever
import javax.persistence.RollbackException

internal class CordaAtomicDBProducerImplTest {

    private val topic = "topic"
    private val key = "key"
    private val value = "value"
    private val serializedKey = key.toByteArray()
    private val serializedValue = value.toByteArray()


    @Test
    fun `atomic producer inserts atomic transaction record on initialization`() {
        val dbAccess: DBAccess = mock()
        CordaAtomicDBProducerImpl(mock(), dbAccess)
        verify(dbAccess).writeAtomicTransactionRecord()
    }

    @Test
    fun `atomic producer is okay when db already has atomic transaction`() {
        val dbAccess: DBAccess = mock()
        whenever(dbAccess.writeTransactionRecord(any())).thenAnswer {
            throw RollbackException("I already have this!")
        }
        CordaAtomicDBProducerImpl(mock(), dbAccess)
    }

    @Test
    fun `atomic producer sends correct entry to database and topic`() {
        val dbAccess: DBAccess = mock()
        whenever(dbAccess.getTopicPartitionMapFor(any())).thenReturn(setOf(CordaTopicPartition(topic, 1)))
        whenever(dbAccess.getMaxOffsetsPerTopicPartition()).thenReturn(mapOf(CordaTopicPartition(topic, 0) to 5))
        val serializer = mock<CordaAvroSerializer<Any>>()
        whenever(serializer.serialize(eq(key))).thenReturn(serializedKey)
        whenever(serializer.serialize(eq(value))).thenReturn(serializedValue)
        val callback: CordaProducer.Callback = mock()

        val producer = CordaAtomicDBProducerImpl(serializer, dbAccess)
        val cordaRecord = CordaProducerRecord(topic, key, value)

        producer.send(cordaRecord, callback)

        val dbRecordList = argumentCaptor<List<TopicRecordEntry>>()
        verify(dbAccess).writeRecords(dbRecordList.capture())
        verify(callback).onCompletion(null)
        val record = dbRecordList.firstValue.single()
        assertThat(record.topic).isEqualTo(topic)
        assertThat(record.key).isEqualTo(serializedKey)
        assertThat(record.value).isEqualTo(serializedValue)
        assertThat(record.recordOffset).isEqualTo(6)
        assertThat(record.partition).isEqualTo(0)
        assertThat(record.transactionId).isEqualTo(ATOMIC_TRANSACTION)
    }

    @Test
    fun `atomic producer sends correct entry to database when partition is specified`() {
        val dbAccess: DBAccess = mock()
        whenever(dbAccess.getMaxOffsetsPerTopicPartition()).thenReturn(mapOf(CordaTopicPartition(topic, 0) to 2))
        val serializer = mock<CordaAvroSerializer<Any>>()
        whenever(serializer.serialize(eq(key))).thenReturn(serializedKey)
        whenever(serializer.serialize(eq(value))).thenReturn(serializedValue)
        val callback: CordaProducer.Callback = mock()

        val producer = CordaAtomicDBProducerImpl(serializer, dbAccess)
        val cordaRecord = CordaProducerRecord(topic, key, value)

        producer.send(cordaRecord, 0, callback)

        val dbRecordList = argumentCaptor<List<TopicRecordEntry>>()
        verify(dbAccess).writeRecords(dbRecordList.capture())
        verify(callback).onCompletion(null)
        val record = dbRecordList.firstValue.first()
        assertThat(record.topic).isEqualTo(topic)
        assertThat(record.key).isEqualTo(serializedKey)
        assertThat(record.value).isEqualTo(serializedValue)
        assertThat(record.recordOffset).isEqualTo(3)
        assertThat(record.partition).isEqualTo(0)
        assertThat(record.transactionId).isEqualTo(ATOMIC_TRANSACTION)
    }

    @Test
    fun `atomic producer does not allow transactional calls`() {
        val dbAccess: DBAccess = mock()

        val producer = CordaAtomicDBProducerImpl(
            mock(),
            dbAccess
        )

        assertThatExceptionOfType(CordaMessageAPIFatalException::class.java).isThrownBy {
            producer.beginTransaction()
        }
        assertThatExceptionOfType(CordaMessageAPIFatalException::class.java).isThrownBy {
            producer.abortTransaction()
        }
        assertThatExceptionOfType(CordaMessageAPIFatalException::class.java).isThrownBy {
            producer.commitTransaction()
        }
        assertThatExceptionOfType(CordaMessageAPIFatalException::class.java).isThrownBy {
            producer.sendAllOffsetsToTransaction(mock())
        }
        assertThatExceptionOfType(CordaMessageAPIFatalException::class.java).isThrownBy {
            producer.sendRecordOffsetsToTransaction(mock(), mock())
        }
        verify(dbAccess).getMaxOffsetsPerTopicPartition()
        verify(dbAccess).writeAtomicTransactionRecord()
        verifyNoMoreInteractions(dbAccess)
    }
}
