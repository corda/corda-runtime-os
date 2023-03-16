package net.corda.virtualnode.rest.common.impl

import net.corda.data.virtualnode.VirtualNodeAsynchronousRequest
import net.corda.data.virtualnode.VirtualNodeManagementRequest
import net.corda.data.virtualnode.VirtualNodeManagementResponse
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.publisher.RPCSender
import net.corda.messaging.api.records.Record
import net.corda.schema.Schemas.VirtualNode.VIRTUAL_NODE_ASYNC_REQUEST_TOPIC
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Duration
import java.util.concurrent.CompletableFuture

internal class VirtualNodeSenderImplTest {
    private val duration = Duration.ofMillis(1000)
    private val mockResponse = mock<VirtualNodeManagementResponse>()
    private val rpcSender = mock<RPCSender<VirtualNodeManagementRequest, VirtualNodeManagementResponse>> {
        whenever(it.sendRequest(any())) doReturn CompletableFuture<VirtualNodeManagementResponse>().completeAsync {
            mockResponse
        }
    }
    private val asyncOperationPublisher = mock<Publisher> {
        whenever(it.publish(any())).thenReturn(emptyList())
    }
    private val senderWrapper = VirtualNodeSenderImpl(duration, rpcSender, asyncOperationPublisher)

    @Test
    fun `test sendAndReceive passes request to sendRequest`() {
        val req = mock<VirtualNodeManagementRequest>()
        senderWrapper.sendAndReceive(req)
        verify(rpcSender).sendRequest(eq(req))
    }

    @Test
    fun `test sendAsync calls publish on async request published`() {
        val req = mock<VirtualNodeAsynchronousRequest>()
        val expectedRecord = Record(VIRTUAL_NODE_ASYNC_REQUEST_TOPIC, "k", req)
        senderWrapper.sendAsync("k", req)
        verify(asyncOperationPublisher).publish(eq(listOf(expectedRecord)))
    }

    @Test
    fun `test close cleans up sender`() {
        senderWrapper.close()
        verify(rpcSender).close()
        verify(asyncOperationPublisher).close()
    }
}
