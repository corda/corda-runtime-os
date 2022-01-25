package net.corda.messagebus.db.producer

import net.corda.messagebus.api.producer.CordaProducer
import net.corda.messagebus.api.producer.CordaProducerRecord
import net.corda.messagebus.db.persistence.DBWriter
import net.corda.messagebus.db.persistence.RecordDbEntry
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
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever
import java.nio.ByteBuffer

internal class CordaAtomicDBProducerImplTest {

    private val topic = "topic"
    private val key = "key"
    private val value = "value"
    private val serializedKey = key.toByteArray()
    private val serializedValue = value.toByteArray()

    @Test
    fun `atomic producer sends correct entry to database`() {
        val dbWriter: DBWriter = mock()
        whenever(dbWriter.getTopicPartitionMap()).thenReturn(mapOf(topic to 1))
        val topicService: TopicService = mock()
        whenever(topicService.getLatestOffsets(eq(topic))).thenReturn(mapOf(1 to 5))
        val schemaRegistry: AvroSchemaRegistry = mock()
        whenever(schemaRegistry.serialize(eq(key))).thenReturn(ByteBuffer.wrap(serializedKey))
        whenever(schemaRegistry.serialize(eq(value))).thenReturn(ByteBuffer.wrap(serializedValue))
        val callback: CordaProducer.Callback = mock()

        val producer = CordaAtomicDBProducerImpl(schemaRegistry, topicService, dbWriter)
        val cordaRecord = CordaProducerRecord(topic, key, value)

        producer.send(cordaRecord, callback)

        val dbRecordList = argumentCaptor<List<RecordDbEntry>>()
        // For atomic transactions the records must be immediately visible
        verify(dbWriter).writeRecords(dbRecordList.capture(), eq(true))
        verify(callback).onCompletion(null)
        val record = dbRecordList.firstValue.single()
        assertThat(record.topic).isEqualTo(topic)
        assertThat(record.key).isEqualTo(serializedKey)
        assertThat(record.value).isEqualTo(serializedValue)
        assertThat(record.offset).isEqualTo(5)
        assertThat(record.partition).isEqualTo(1)

        val topicRecordList = argumentCaptor<List<Record<*, *>>>()
        verify(topicService).addRecordsToPartition(topicRecordList.capture() , eq(1))
        val topicRecord = topicRecordList.firstValue.single()
        assertThat(topicRecord.topic).isEqualTo(topic)
        assertThat(topicRecord.key).isEqualTo(key)
        assertThat(topicRecord.value).isEqualTo(value)
    }

    @Test
    fun `atomic producer sends correct entry to database when partition is specified`() {
        val dbWriter: DBWriter = mock()
        whenever(dbWriter.getTopicPartitionMap()).thenReturn(mapOf(topic to 2))
        val topicService: TopicService = mock()
        whenever(topicService.getLatestOffsets(eq(topic))).thenReturn(mapOf(0 to 2, 1 to 5))
        val schemaRegistry: AvroSchemaRegistry = mock()
        whenever(schemaRegistry.serialize(eq(key))).thenReturn(ByteBuffer.wrap(serializedKey))
        whenever(schemaRegistry.serialize(eq(value))).thenReturn(ByteBuffer.wrap(serializedValue))
        val argumentCaptor = argumentCaptor<List<RecordDbEntry>>()
        val callback: CordaProducer.Callback = mock()

        val producer = CordaAtomicDBProducerImpl(schemaRegistry, topicService, dbWriter)
        val cordaRecord = CordaProducerRecord(topic, key, value)

        producer.send(cordaRecord, 0, callback)

        // For atomic transactions the records must be immediately visible
        verify(dbWriter).writeRecords(argumentCaptor.capture(), eq(true))
        verify(callback).onCompletion(null)
        val record = argumentCaptor.firstValue.first()
        assertThat(record.topic).isEqualTo(topic)
        assertThat(record.key).isEqualTo(serializedKey)
        assertThat(record.value).isEqualTo(serializedValue)
        assertThat(record.offset).isEqualTo(2)
        assertThat(record.partition).isEqualTo(0)

        val topicRecordList = argumentCaptor<List<Record<*, *>>>()
        verify(topicService).addRecordsToPartition(topicRecordList.capture() , eq(0))
        val topicRecord = topicRecordList.firstValue.single()
        assertThat(topicRecord.topic).isEqualTo(topic)
        assertThat(topicRecord.key).isEqualTo(key)
        assertThat(topicRecord.value).isEqualTo(value)
    }

    @Test
    fun `atomic producer does not allow transactional calls`() {
        val dbWriter: DBWriter = mock()
        val topicService: TopicService = mock()

        val producer = CordaAtomicDBProducerImpl(
            mock(),
            topicService,
            dbWriter
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
        verifyNoInteractions(topicService)
        verify(dbWriter).getTopicPartitionMap()
        verifyNoMoreInteractions(dbWriter)
    }
}
