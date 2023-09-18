package net.corda.messaging.mediator

import kotlinx.coroutines.runBlocking
import net.corda.messagebus.api.producer.CordaProducer
import net.corda.messagebus.api.producer.CordaProducerRecord
import net.corda.messaging.api.mediator.MediatorMessage
import net.corda.v5.base.exceptions.CordaRuntimeException
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

class MessageBusProducerTest {
    private lateinit var cordaProducer: CordaProducer
    private lateinit var mediatorProducer: MessageBusProducer

    private val defaultHeaders: List<Pair<String, String>> = emptyList()
    private val messageProps: MutableMap<String, Any> = mutableMapOf(
        "topic" to "topic",
        "key" to "key",
        "headers" to defaultHeaders
    )
    private val message: MediatorMessage<Any> = MediatorMessage("value", messageProps)


    @BeforeEach
    fun setup() {
        cordaProducer = mock()
        mediatorProducer = MessageBusProducer("client-id", cordaProducer)
    }

    @Test
    fun testSend() {
        mediatorProducer.send(message)

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

        doThrow(CordaRuntimeException("")).whenever(cordaProducer).send(eq(record), any())
        assertThrows<CordaRuntimeException> {
            runBlocking {
                mediatorProducer.send(message).await()
            }
        }
    }

    @Test
    fun testClose() {
        mediatorProducer.close()
        verify(cordaProducer, times(1)).close()
    }
}