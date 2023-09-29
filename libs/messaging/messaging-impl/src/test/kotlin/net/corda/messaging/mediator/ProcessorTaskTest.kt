package net.corda.messaging.mediator

import net.corda.messagebus.api.producer.CordaProducer
import net.corda.messaging.api.mediator.MediatorMessage
import org.junit.jupiter.api.BeforeEach
import org.mockito.kotlin.mock

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
/*
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
                mediatorProducer.send(message).await()
            }
        }
    }

    @Test
    fun testClose() {
        mediatorProducer.close()
        verify(cordaProducer, times(1)).close()
    }

 */
}