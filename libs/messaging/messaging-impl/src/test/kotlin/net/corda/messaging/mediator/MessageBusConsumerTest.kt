package net.corda.messaging.mediator

import net.corda.messagebus.api.consumer.CordaConsumer
import net.corda.messagebus.api.consumer.CordaConsumerRecord
import net.corda.v5.base.exceptions.CordaRuntimeException
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
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
        private val testRecord = CordaConsumerRecord("topic", 0, 0, "key", "val", 0 )
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
            mediatorConsumer.poll(timeout)
        }
    }

    // TODO Fix those when the offset manager is done (or injected)
    @Disabled("Need to deal with offset manager first")
    @Test
    fun testSyncCommitOffsets() {
        mediatorConsumer.syncCommitOffsets(listOf(testRecord))

        verify(cordaConsumer).syncCommitOffsets()
    }

    @Disabled("Need to deal with offset manager first")
    @Test
    fun testSyncCommitOffsetsWithError() {
        doThrow(CordaRuntimeException("")).whenever(cordaConsumer).syncCommitOffsets()

        assertThrows<CordaRuntimeException> {
            mediatorConsumer.syncCommitOffsets(listOf(testRecord))
        }
    }

    @Test
    fun testClose() {
        mediatorConsumer.close()
        verify(cordaConsumer, times(1)).close()
    }
}