package net.corda.messaging.kafka.subscription.consumer.listener

import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.times
import com.nhaarman.mockito_kotlin.verify
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
