package net.corda.messagebus.db.producer

import net.corda.messagebus.api.consumer.CordaConsumerRecord
import net.corda.messagebus.api.producer.CordaProducer
import net.corda.messagebus.api.producer.CordaProducerRecord
import net.corda.messagebus.db.persistence.DBWriter
import net.corda.messaging.api.exception.CordaMessageAPIFatalException
import net.corda.messaging.emulation.topic.service.TopicService
import org.assertj.core.api.Assertions.assertThatExceptionOfType
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verifyNoInteractions

internal class CordaTransactionalDBProducerImplTest {

    private val topic = "topic"
    private val key = "key"
    private val value = "value"
    private val serializedKey = key.toByteArray()
    private val serializedValue = value.toByteArray()

    @Test
    fun `transactional producer doesn't allow sends when not transaction`() {
        val dbWriter: DBWriter = mock()
        val topicService: TopicService = mock()

        val producer = CordaTransactionalDBProducerImpl(
            mock(),
            topicService,
            dbWriter
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
}
