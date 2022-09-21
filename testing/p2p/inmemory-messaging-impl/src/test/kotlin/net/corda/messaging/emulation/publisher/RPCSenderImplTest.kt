package net.corda.messaging.emulation.publisher

import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.messaging.api.exception.CordaRPCAPISenderException
import net.corda.messaging.api.subscription.config.RPCConfig
import net.corda.messaging.emulation.rpc.RPCTopicService
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicInteger

class RPCSenderImplTest {
    private val rpcTopicService: RPCTopicService = mock()
    private val lifecycleCoordinator: LifecycleCoordinator = mock()
    private val lifecycleCoordinatorFactory: LifecycleCoordinatorFactory = mock {
        on { createCoordinator(any(), any()) } doReturn lifecycleCoordinator
    }
    private val clientIdCounter = AtomicInteger()

    @Test
    fun `Send while sender is not running should throw`() {
        val request = "r1"
        val rpcSender = RPCSenderImpl(
            getConfig(),
            rpcTopicService,
            lifecycleCoordinatorFactory,
            clientIdCounter.getAndIncrement().toString()
        )

        assertThrows<CordaRPCAPISenderException> { rpcSender.sendRequest(request) }
    }

    @Test
    fun `Send should publish the request on the configured topic`() {
        val request = "r1"
        val rpcSender = RPCSenderImpl(
            getConfig(),
            rpcTopicService,
            lifecycleCoordinatorFactory,
            clientIdCounter.getAndIncrement().toString()
        )

        rpcSender.start()
        val requestCompletion = rpcSender.sendRequest(request)

        Assertions.assertThat(requestCompletion).isInstanceOf(CompletableFuture::class.java)

        verify(rpcTopicService, times(1)).publish("test", request, requestCompletion)
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
