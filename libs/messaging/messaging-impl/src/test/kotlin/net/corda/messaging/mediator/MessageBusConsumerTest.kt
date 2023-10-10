package net.corda.messaging.mediator

import kotlinx.coroutines.runBlocking
import net.corda.messagebus.api.consumer.CordaConsumer
import net.corda.v5.base.exceptions.CordaRuntimeException
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito.doThrow
import org.mockito.Mockito.times
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Duration

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

    @Test
    fun testSubscribe() {
        mediatorConsumer.subscribe()

        verify(cordaConsumer).subscribe(eq(TOPIC), anyOrNull())
    }

    @Test
    fun testPoll() {
        val timeout = Duration.ofMillis(123)
        mediatorConsumer.poll(timeout)

        verify(cordaConsumer).poll(eq(timeout))
    }

    @Test
    fun testPollWithError() {
        val timeout = Duration.ofMillis(123)
        doThrow(CordaRuntimeException("")).whenever(cordaConsumer).poll(any())

        assertThrows<CordaRuntimeException> {
            runBlocking {
                mediatorConsumer.poll(timeout).await()
            }
        }
    }

    @Test
    fun testCommitAsyncOffsets() {
        mediatorConsumer.asyncCommitOffsets()

        verify(cordaConsumer).asyncCommitOffsets(any())
    }

    @Test
    fun testCommitAsyncOffsetsWithError() {
        whenever(cordaConsumer.asyncCommitOffsets(any<CordaConsumer.Callback>())).thenAnswer { invocation ->
            val callback = invocation.getArgument<CordaConsumer.Callback>(0)
            callback.onCompletion(mock(), CordaRuntimeException(""))
        }

        assertThrows<CordaRuntimeException> {
            runBlocking {
                mediatorConsumer.asyncCommitOffsets().await()
            }
        }
    }

    @Test
    fun testClose() {
        mediatorConsumer.close()
        verify(cordaConsumer, times(1)).close()
    }
}