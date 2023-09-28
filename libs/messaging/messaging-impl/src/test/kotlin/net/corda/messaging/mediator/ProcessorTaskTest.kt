package net.corda.messaging.mediator

import kotlinx.coroutines.runBlocking
import net.corda.libs.statemanager.api.State
import net.corda.messagebus.api.consumer.CordaConsumerRecord
import net.corda.messagebus.api.producer.CordaProducer
import net.corda.messagebus.api.producer.CordaProducerRecord
import net.corda.messaging.api.mediator.MediatorMessage
 import net.corda.messaging.api.processor.StateAndEventProcessor
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
import java.time.Instant

class ProcessorTaskTest {
    companion object {
        private const val TOPIC = "topic"
    }

    private lateinit var cordaProducer: CordaProducer
    private lateinit var mediatorProducer: MessageBusClient

    private val defaultHeaders: List<Pair<String, String>> = emptyList()
    private val messageProps: MutableMap<String, Any> = mutableMapOf(
        "key" to "key",
        "headers" to defaultHeaders
    )
    private val message: MediatorMessage<Any> = MediatorMessage("value", messageProps)


    @BeforeEach
    fun setup() {
        cordaProducer = mock()
        mediatorProducer = MessageBusClient("client-id", cordaProducer)
    }

    @Test
    fun `successfully processes messages without initial state`() {
        val key = "key"
        val persistedSate: State? = null
        val events = listOf("event1", "event2", "event3")
            .map {
                CordaConsumerRecord(
                    topic = "",
                    partition = -1,
                    offset = -1,
                    key = key,
                    value = it,
                    timestamp = Instant.now().toEpochMilli()
                )
            }
        val processor = mock<StateAndEventProcessor<String, String, String>>()
        val stateManagerHelper = mock<StateManagerHelper<String, String, String>>()
        val task = ProcessorTask(
            key,
            persistedSate,
            events,
            processor,
            stateManagerHelper
        )

        val result = task.call()

        val expected = CordaProducerRecord(
            TOPIC,
            message.getProperty("key"),
            message.payload
        )

        verify(cordaProducer).send(eq(expected), any())
    }

    @Test
    fun testSendWithError() {
        val record = CordaProducerRecord(
            TOPIC,
            message.getProperty("key"),
            message.payload
        )

        doThrow(CordaRuntimeException("")).whenever(cordaProducer).send(eq(record), any())
        assertThrows<CordaRuntimeException> {
            runBlocking {
                mediatorProducer.send(message, TOPIC).await()
            }
        }
    }

    @Test
    fun testClose() {
        mediatorProducer.close()
        verify(cordaProducer, times(1)).close()
    }
}