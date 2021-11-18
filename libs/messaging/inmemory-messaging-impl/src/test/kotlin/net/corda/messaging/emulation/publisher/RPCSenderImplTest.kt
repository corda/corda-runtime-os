package net.corda.messaging.emulation.publisher

import net.corda.messaging.api.exception.CordaRPCAPISenderException
import net.corda.messaging.emulation.rpc.RPCTopicService
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import java.util.concurrent.CompletableFuture

class RPCSenderImplTest {
    private val rpcTopicService: RPCTopicService = mock()

    @Test
    fun `Start sets running to true`() {
        val topic = "t1"
        val rpcSender = RPCSenderImpl<String, String>(topic, rpcTopicService)

        Assertions.assertThat(rpcSender.isRunning).isFalse
        rpcSender.start()
        Assertions.assertThat(rpcSender.isRunning).isTrue
    }

    @Test
    fun `Stop sets running to true`() {
        val topic = "t1"
        val rpcSender = RPCSenderImpl<String, String>(topic, rpcTopicService)

        rpcSender.start()
        Assertions.assertThat(rpcSender.isRunning).isTrue
        rpcSender.stop()
        Assertions.assertThat(rpcSender.isRunning).isFalse
    }

    @Test
    fun `Send while sender is not running should throw`() {
        val topic = "t1"
        val request = "r1"
        val rpcSender = RPCSenderImpl<String, String>(topic, rpcTopicService)

        assertThrows<CordaRPCAPISenderException> { rpcSender.sendRequest(request)}
    }

    @Test
    fun `Send should publish the request on the configured topic`() {
        val topic = "t1"
        val request = "r1"
        val rpcSender = RPCSenderImpl<String, String>(topic, rpcTopicService)

        rpcSender.start()
        val requestCompletion = rpcSender.sendRequest(request)

        Assertions.assertThat(requestCompletion).isInstanceOf(CompletableFuture::class.java)

        verify(rpcTopicService, times(1)).publish(topic, request, requestCompletion)
    }
}