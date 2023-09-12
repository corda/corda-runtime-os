package net.corda.messaging.kafka.subscription.net.corda.messagebus.kafka.producer

import io.micrometer.core.instrument.binder.kafka.KafkaClientMetrics
import kotlinx.coroutines.runBlocking
import net.corda.messagebus.api.producer.CordaMessage
import net.corda.messagebus.api.producer.CordaProducer
import net.corda.messagebus.api.producer.CordaProducerRecord
import net.corda.messagebus.kafka.producer.MessageProducerImpl
import org.apache.kafka.common.KafkaException
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito.doThrow
import org.mockito.Mockito.times
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class MessageProducerImplTest {
    private lateinit var cordaProducer: CordaProducer
    private lateinit var messageProducer: MessageProducerImpl

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
        cordaProducer = mock()
        messageProducer = MessageProducerImpl("client-id", cordaProducer, metricsBinder)
    }

    @Test
    fun testSend() {
        messageProducer.send(message)

        val expected = CordaProducerRecord(
            message.getProperty<String>("topic"),
            message.getProperty("key"),
            message.payload
        )

        verify(cordaProducer).send(eq(expected), any())
    }

    @Test
    fun testSendWithError() {
        val record = CordaProducerRecord(
            message.getProperty<String>("topic"),
            message.getProperty("key"),
            message.payload
        )

        doThrow(KafkaException("")).whenever(cordaProducer).send(eq(record), any())
        assertThrows<KafkaException> {
            runBlocking {
                messageProducer.send(message).await()
            }
        }
    }

    @Test
    fun testClose() {
        messageProducer.close()
        verify(cordaProducer, times(1)).close()
        verify(metricsBinder, times(1)).close()
    }
}