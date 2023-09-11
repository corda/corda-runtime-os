package net.corda.messaging.kafka.subscription.net.corda.messagebus.kafka.producer

import io.micrometer.core.instrument.binder.kafka.KafkaClientMetrics
import net.corda.messagebus.api.producer.CordaMessage
import net.corda.messagebus.api.producer.CordaProducerRecord
import net.corda.messagebus.api.producer.MessageProducer
import net.corda.messagebus.kafka.config.ResolvedProducerConfig
import net.corda.messagebus.kafka.producer.KafkaMessageProducerImpl
import net.corda.messaging.api.chunking.ChunkSerializerService
import net.corda.messaging.api.exception.CordaMessageAPIFatalException
import net.corda.messaging.api.exception.CordaMessageAPIIntermittentException
import org.apache.kafka.clients.producer.MockProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.KafkaException
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito.doThrow
import org.mockito.Mockito.times
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.atLeastOnce
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class KafkaMessageProducerImplTest {
    private lateinit var kafkaProducer: MockProducer<Any, Any>
    private lateinit var messageProducer: KafkaMessageProducerImpl

    private val config = ResolvedProducerConfig("clientId", true, "prefix", false)
    private val chunkSerializerService: ChunkSerializerService = mock()
    private val metricsBinder: KafkaClientMetrics = mock()
    private val defaultHeaders: List<Pair<String, String>> = emptyList()
    private val messageProps: MutableMap<String, Any> = mutableMapOf(
        "topic" to "topic",
        "key" to "key",
        "headers" to defaultHeaders
    )
    private val message: CordaMessage<Any> = CordaMessage("value", messageProps)


    @BeforeEach
    fun setup() {
        kafkaProducer = mock()
        messageProducer = KafkaMessageProducerImpl(config, kafkaProducer, chunkSerializerService, metricsBinder)
    }

    @Test
    fun testSend() {
        val callback = mock<MessageProducer.Callback>()
        messageProducer.send(message, callback)

        val expected = ProducerRecord(
            config.topicPrefix + message.getProperty("topic"),
            message.getProperty("key"),
            message.payload
        )

        verify(kafkaProducer).send(eq(expected), any())
    }

    @Test
    fun testSendWithPartition() {
        val callback = mock<MessageProducer.Callback>()
        val messageWithPartition = message.copy().apply {
            this.addProperty(Pair("partition", 1))
        }
        messageProducer.send(messageWithPartition, callback)

        val expected = ProducerRecord(
            config.topicPrefix + messageWithPartition.getProperty("topic"),
            1,
            messageWithPartition.getProperty("key"),
            messageWithPartition.payload
        )

        verify(kafkaProducer).send(eq(expected), any())
    }

    @Test
    fun testSendWithError() {
        val callback = mock<MessageProducer.Callback>()
        val record = ProducerRecord(
            config.topicPrefix + message.getProperty("topic"),
            message.getProperty("key"),
            message.payload
        )

        doThrow(KafkaException("")).whenever(kafkaProducer).send(eq(record), any())
        assertThrows<CordaMessageAPIIntermittentException> { messageProducer.send(message, callback) }
    }

    @Test
    fun testSendMessages() {
        val messages = listOf(message, message, message)
        val expectedPublishedMessages =
            messages.map {
                ProducerRecord(
                    config.topicPrefix + it.getProperty("topic"),
                    it.getProperty("key"),
                    it.payload
                )
            }

        messageProducer.sendMessages(messages)
        verify(kafkaProducer, times(3)).send(any(), anyOrNull())
        expectedPublishedMessages.forEach { verify(kafkaProducer, atLeastOnce()).send(eq(it), any()) }
    }

    @Test
    fun testSendMessagesWithError() {
        val record = ProducerRecord(
            config.topicPrefix + message.getProperty("topic"),
            message.getProperty("key"),
            message.payload
        )

        doThrow(KafkaException("")).whenever(kafkaProducer).send(eq(record), any())
        assertThrows<CordaMessageAPIIntermittentException> {
            messageProducer.sendMessages(listOf(message, message, message))
        }
    }

    // TODO: Producer-level chunking is not yet implemented for non-transactional kafka producers
    @Test
    fun `Trying to send chunked message throws a fatal exception and executes callback`() {
        val callback = mock<MessageProducer.Callback>()
        val record = CordaProducerRecord(
            config.topicPrefix + message.getProperty("topic"),
            message.getProperty("key"),
            message.payload
        )

        whenever(chunkSerializerService.generateChunkedRecords(any())).thenReturn(listOf(record, record))
        assertThrows<CordaMessageAPIFatalException> {
            messageProducer.send(message, callback)
        }

        verify(callback, org.mockito.kotlin.times(1)).onCompletion(any())
    }

    @Test
    fun testClose() {
        messageProducer.close()
        verify(kafkaProducer, times(1)).close()
        verify(metricsBinder, times(1)).close()
    }
}