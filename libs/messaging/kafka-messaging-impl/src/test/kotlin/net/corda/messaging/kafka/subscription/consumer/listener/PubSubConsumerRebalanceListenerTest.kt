package net.corda.messaging.kafka.subscription.consumer.listener

import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.apache.kafka.clients.consumer.Consumer
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.nio.ByteBuffer

class PubSubConsumerRebalanceListenerTest {

    private val consumer : Consumer<String, ByteBuffer> = mock()
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
