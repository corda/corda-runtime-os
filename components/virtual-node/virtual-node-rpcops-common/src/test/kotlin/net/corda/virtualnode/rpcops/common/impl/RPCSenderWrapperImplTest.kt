package net.corda.virtualnode.rpcops.common.impl

import net.corda.data.virtualnode.VirtualNodeManagementRequest
import net.corda.data.virtualnode.VirtualNodeManagementResponse
import net.corda.messaging.api.publisher.RPCSender
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Duration
import java.util.concurrent.CompletableFuture

internal class RPCSenderWrapperImplTest {
    private val duration = Duration.ofMillis(1000)
    private val mockResponse = mock<VirtualNodeManagementResponse>()
    private val sender = mock<RPCSender<VirtualNodeManagementRequest, VirtualNodeManagementResponse>>().apply {
        whenever(sendRequest(any())) doReturn CompletableFuture<VirtualNodeManagementResponse>().completeAsync {
            mockResponse
        }
    }
    private val senderWrapper = RPCSenderWrapperImpl(duration, sender)

    @Test
    fun `test sendAndReceive passes request to sendRequest`() {
        val req = mock<VirtualNodeManagementRequest>()
        senderWrapper.sendAndReceive(req)
        verify(sender).sendRequest(eq(req))
    }

    @Test
    fun `test close cleans up sender`() {
        senderWrapper.close()
        verify(sender).close()
    }
}
