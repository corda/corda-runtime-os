package net.corda.messaging.emulation.subscription.rpc

import net.corda.messaging.api.processor.RPCResponderProcessor
import net.corda.messaging.emulation.rpc.RPCTopicService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify

class RPCSubscriptionImplTest {
    private val rpcTopicService: RPCTopicService = mock()
    private val responseProcessor: RPCResponderProcessor<String, String> = mock()

    @Test
    fun `Start subscribes the processor to the topic`() {
        val topic = "t1"
        val rpcSubscription = RPCSubscriptionImpl(topic, rpcTopicService, responseProcessor)

        rpcSubscription.start()
        assertThat(rpcSubscription.isRunning).isTrue
        verify(rpcTopicService, times(1)).subscribe(topic, responseProcessor)
    }

    @Test
    fun `Stop unsubscribes the processor from the topic`() {
        val topic = "t1"
        val rpcSubscription = RPCSubscriptionImpl(topic, rpcTopicService, responseProcessor)

        rpcSubscription.stop()
        assertThat(rpcSubscription.isRunning).isFalse
        verify(rpcTopicService, times(1)).unsubscribe(topic, responseProcessor)
    }
}