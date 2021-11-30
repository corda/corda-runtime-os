package net.corda.messaging.emulation.publisher

import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.messaging.api.exception.CordaRPCAPISenderException
import net.corda.messaging.api.subscription.factory.config.RPCConfig
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

class RPCSenderImplTest {
    private val rpcTopicService: RPCTopicService = mock()
    private val lifecycleCoordinator: LifecycleCoordinator = mock()
    private val lifecycleCoordinatorFactory: LifecycleCoordinatorFactory = mock {
        on { createCoordinator(any(), any()) } doReturn lifecycleCoordinator
    }


    @Test
    fun `Start sets running to true`() {
        val rpcSender = RPCSenderImpl(getConfig(), rpcTopicService, lifecycleCoordinatorFactory)

        Assertions.assertThat(rpcSender.isRunning).isFalse
        rpcSender.start()
        Assertions.assertThat(rpcSender.isRunning).isTrue
    }

    @Test
    fun `Stop sets running to true`() {
        val rpcSender = RPCSenderImpl(getConfig(), rpcTopicService, lifecycleCoordinatorFactory)

        rpcSender.start()
        Assertions.assertThat(rpcSender.isRunning).isTrue
        rpcSender.stop()
        Assertions.assertThat(rpcSender.isRunning).isFalse
    }

    @Test
    fun `Send while sender is not running should throw`() {
        val request = "r1"
        val rpcSender = RPCSenderImpl(getConfig(), rpcTopicService, lifecycleCoordinatorFactory)

        assertThrows<CordaRPCAPISenderException> { rpcSender.sendRequest(request) }
    }

    @Test
    fun `Send should publish the request on the configured topic`() {
        val request = "r1"
        val rpcSender = RPCSenderImpl(getConfig(), rpcTopicService, lifecycleCoordinatorFactory)

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
            String::class.java,
            1
        )
    }
}