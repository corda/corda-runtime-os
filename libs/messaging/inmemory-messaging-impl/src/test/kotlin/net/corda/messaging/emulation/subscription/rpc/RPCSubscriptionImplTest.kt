package net.corda.messaging.emulation.subscription.rpc

import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.messaging.api.processor.RPCResponderProcessor
import net.corda.messaging.api.subscription.config.RPCConfig
import net.corda.messaging.emulation.rpc.RPCTopicService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import java.util.concurrent.atomic.AtomicInteger

class RPCSubscriptionImplTest {
    private val rpcTopicService: RPCTopicService = mock()
    private val responseProcessor: RPCResponderProcessor<String, String> = mock()
    private val lifecycleCoordinator: LifecycleCoordinator = mock()
    private val lifecycleCoordinatorFactory: LifecycleCoordinatorFactory = mock {
        on { createCoordinator(any(), any()) } doReturn lifecycleCoordinator
    }
    private val clientIdCounter = AtomicInteger()

    @Test
    fun `Start subscribes the processor to the topic`() {
        val rpcSubscription =
            RPCSubscriptionImpl(
                getConfig(),
                rpcTopicService,
                responseProcessor,
                lifecycleCoordinatorFactory,
                clientIdCounter.getAndIncrement().toString()
            )

        rpcSubscription.start()
        assertThat(rpcSubscription.isRunning).isTrue
        verify(rpcTopicService, times(1)).subscribe("test", responseProcessor)
    }

    @Test
    fun `Stop unsubscribes the processor from the topic`() {
        val rpcSubscription =
            RPCSubscriptionImpl(
                getConfig(),
                rpcTopicService,
                responseProcessor,
                lifecycleCoordinatorFactory,
                clientIdCounter.getAndIncrement().toString()
            )

        rpcSubscription.stop()
        assertThat(rpcSubscription.isRunning).isFalse
        verify(rpcTopicService, times(1)).unsubscribe("test", responseProcessor)
    }

    private fun getConfig(): RPCConfig<String, String> {
        return RPCConfig(
            "testGroupName",
            "testClientName",
            "test",
            String::class.java,
            String::class.java
        )
    }
}
