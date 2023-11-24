package net.corda.messaging.mediator

import net.corda.messagebus.api.producer.CordaProducer
import net.corda.messagebus.api.producer.CordaProducerRecord
import net.corda.messaging.api.exception.CordaMessageAPIFatalException
import net.corda.messaging.api.mediator.MediatorMessage
import net.corda.messaging.api.mediator.MessagingClient.Companion.MSG_PROP_ENDPOINT
import net.corda.v5.base.exceptions.CordaRuntimeException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito
import org.mockito.Mockito.any
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.times
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.concurrent.CompletableFuture

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
    private val record: CordaProducerRecord<*, *> = CordaProducerRecord(
        TEST_ENDPOINT,
        TEST_KEY,
        message.payload,
        messageProps.toHeaders(),
    )


    @BeforeEach
    fun setup() {
        cordaProducer = mock()
        messageBusClient = MessageBusClient("client-id", cordaProducer)
    }

    @Suppress("UNCHECKED_CAST")
    @Test
    fun `test send`() {
        doAnswer {
            val callback = it.getArgument<CordaProducer.Callback>(1)
            callback.onCompletion(null)
        }.whenever(cordaProducer).send(eq(record), any())

        val result = messageBusClient.send(message) as MediatorMessage<CompletableFuture<Unit>>

        verify(cordaProducer).send(eq(record), any())
        assertNotNull(result.payload)
        result.payload?.let {
            assertTrue(it.isDone)
            assertFalse(it.isCompletedExceptionally)
        }
    }

    @Test
    fun `send should handle synchronous error`() {
        val record = CordaProducerRecord(
            TEST_ENDPOINT,
            TEST_KEY,
            message.payload,
            messageProps.toHeaders(),
        )

        Mockito.doThrow(CordaRuntimeException("")).whenever(cordaProducer).send(eq(record), any())
        assertThrows<CordaRuntimeException> {
            messageBusClient.send(message)
        }
    }

    @Suppress("UNCHECKED_CAST")
    @Test
    fun `send should handle asynchronous CordaMessageAPIFatalException`() {
        doAnswer {
            val callback = it.getArgument<CordaProducer.Callback>(1)
            callback.onCompletion(CordaMessageAPIFatalException("test"))
        }.whenever(cordaProducer).send(eq(record), any())

        val result = messageBusClient.send(message) as MediatorMessage<CompletableFuture<Unit>>

        verify(cordaProducer).send(eq(record), any())
        assertNotNull(result.payload)

        result.payload?.isCompletedExceptionally?.let { assertTrue(it) }

        result.payload?.handle { _, exception ->
            assertTrue(exception is CordaMessageAPIFatalException)
            assertEquals("Producer clientId client-id for topic topic failed to send.", exception.message)
        }?.get()
    }

    @Suppress("UNCHECKED_CAST")
    @Test
    fun `send should wrap unknown exceptions`() {
        doAnswer {
            val callback = it.getArgument<CordaProducer.Callback>(1)
            callback.onCompletion(CordaRuntimeException("test"))
        }.whenever(cordaProducer).send(eq(record), any())

        val result = messageBusClient.send(message) as MediatorMessage<CompletableFuture<Unit>>

        verify(cordaProducer).send(eq(record), any())
        assertNotNull(result.payload)

        result.payload?.isCompletedExceptionally?.let { assertTrue(it) }

        result.payload?.handle { _, exception ->
            assertTrue(exception is CordaMessageAPIFatalException)
            assertEquals("Producer clientId client-id for topic topic failed to send.", exception.message)
        }?.get()
    }

    @Test
    fun `test close`() {
        messageBusClient.close()
        verify(cordaProducer, times(1)).close()
    }

    private fun Map<String, Any>.toHeaders() =
        map { (key, value) -> (key to value.toString()) }
}