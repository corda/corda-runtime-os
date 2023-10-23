package net.corda.messaging.mediator

import net.corda.messagebus.api.producer.CordaProducer
import net.corda.messagebus.api.producer.CordaProducerRecord
import net.corda.messaging.api.mediator.MediatorMessage
import net.corda.messaging.api.mediator.MessagingClient.Companion.MSG_PROP_ENDPOINT
import net.corda.v5.base.exceptions.CordaRuntimeException
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito
import org.mockito.Mockito.times
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class MessageBusClientTest {
    private companion object {
        const val MSG_PROP_KEY = "key"
        const val TEST_ENDPOINT = "topic"
        const val TEST_KEY = "key"
    }

    private lateinit var cordaProducer: CordaProducer
    private lateinit var messageBusClient: MessageBusClient

    private val messageProps: MutableMap<String, Any> = mutableMapOf(
        MSG_PROP_ENDPOINT to TEST_ENDPOINT,
        MSG_PROP_KEY to TEST_KEY,
    )
    private val message: MediatorMessage<Any> = MediatorMessage("value", messageProps)


    @BeforeEach
    fun setup() {
        cordaProducer = mock()
        messageBusClient = MessageBusClient("client-id", cordaProducer)
    }

    @Test
    fun testSend() {
        messageBusClient.send(message)

        val expected = CordaProducerRecord(
            TEST_ENDPOINT,
            TEST_KEY,
            message.payload,
            messageProps.toHeaders(),
        )

        verify(cordaProducer).send(eq(expected), isNull())
    }

    @Test
    fun testSendWithError() {
        val record = CordaProducerRecord(
            TEST_ENDPOINT,
            TEST_KEY,
            message.payload,
            messageProps.toHeaders(),
        )

        Mockito.doThrow(CordaRuntimeException("")).whenever(cordaProducer).send(eq(record), isNull())
        assertThrows<CordaRuntimeException> {
            messageBusClient.send(message)
        }
    }

    @Test
    fun testClose() {
        messageBusClient.close()
        verify(cordaProducer, times(1)).close()
    }

    private fun Map<String, Any>.toHeaders() =
        map { (key, value) -> (key to value.toString()) }
}