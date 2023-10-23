package net.corda.messaging.mediator

import net.corda.messagebus.api.consumer.CordaConsumer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.times
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

class MessageBusConsumerTest {
    companion object {
        private const val TOPIC = "topic"
    }

    private lateinit var cordaConsumer: CordaConsumer<String, String>
    private lateinit var mediatorConsumer: MessageBusConsumer<String, String>

    @BeforeEach
    fun setup() {
        cordaConsumer = mock()
        mediatorConsumer = MessageBusConsumer(TOPIC, cordaConsumer)
    }

    @AfterEach
    fun tearDown() {
        mediatorConsumer.close()
    }

    @Test
    fun testSubscribe() {
        mediatorConsumer.subscribe()

        verify(cordaConsumer).subscribe(eq(TOPIC), anyOrNull())
    }

    @Test
    fun testClose() {
        mediatorConsumer.close()
        verify(cordaConsumer, times(1)).close()
    }
}