package net.corda.messaging.subscription.consumer.listener

import net.corda.messagebus.api.consumer.CordaConsumer
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import java.nio.ByteBuffer

class PubSubConsumerRebalanceListenerTest {

    private val consumer : CordaConsumer<String, ByteBuffer> = mock()
    private lateinit var listener : PubSubConsumerRebalanceListener<String, ByteBuffer>

    @BeforeEach
    fun beforeEach() {
        listener = PubSubConsumerRebalanceListener("", "", consumer)
    }

    @Test
    fun testAssignedPartitions() {
        listener.onPartitionsAssigned(mutableListOf())
        verify(consumer, times(1)).seekToEnd(any())
    }
}
