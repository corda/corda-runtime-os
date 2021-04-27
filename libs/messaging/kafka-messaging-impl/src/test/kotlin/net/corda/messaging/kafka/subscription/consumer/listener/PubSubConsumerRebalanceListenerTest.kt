package net.corda.messaging.kafka.subscription.consumer.listener

import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.times
import com.nhaarman.mockito_kotlin.verify
import org.apache.kafka.clients.consumer.Consumer
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class PubSubConsumerRebalanceListenerTest {

    private lateinit var consumer : Consumer<String, ByteArray>
    private lateinit var listener : PubSubConsumerRebalanceListener<String, ByteArray>

    @BeforeEach
    fun beforeEach() {
        consumer = mock()
        listener = PubSubConsumerRebalanceListener(consumer)
    }

    @Test
    fun testAssignedParititons() {
        listener.onPartitionsAssigned(mutableListOf())
        verify(consumer, times(1)).seekToEnd(any())
    }
}