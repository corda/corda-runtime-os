package net.corda.messagebus.db.producer

import net.corda.messagebus.db.persistence.DBWriter
import net.corda.messagebus.db.util.CordaProducerRecord
import net.corda.messaging.api.exception.CordaMessageAPIFatalException
import net.corda.messaging.emulation.topic.service.TopicService
import org.assertj.core.api.Assertions.assertThatExceptionOfType
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet

internal class CordaAtomicDBProducerImplTest {

    @Test
    fun `atomic producer does not allow transactional calls`() {
        val results: ResultSet = mock()
        whenever(results.next()).thenReturn(false)
        val statement: PreparedStatement = mock()
        whenever(statement.executeQuery()).thenReturn(results)
        val connection: Connection = mock()
        whenever(connection.prepareStatement(any())).thenReturn(statement)
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
        verifyNoInteractions(dbWriter)
    }

    @Test
    fun `transactional producer does allow sends when in transaction`() {
        val results: ResultSet = mock()
        whenever(results.next()).thenReturn()
        val statement: PreparedStatement = mock()
        whenever(statement.executeQuery()).thenReturn(results)
        val connection: Connection = mock()
        whenever(connection.prepareStatement(any())).thenReturn(statement)
        val dbWriter: DBWriter = mock()
        val topicService: TopicService = mock()

        val producer = CordaAtomicDBProducerImpl(
            mock(),
            topicService,
            dbWriter
        )

        val record = CordaProducerRecord<String, String>("myTopic", "key", "value")

        producer.beginTransaction()
        producer.send(record, null)
        // If we didn't commit we shouldn't send to the cache either
        verifyNoInteractions(topicService)

    }
}
