package net.corda.virtualnode.rpcops.common

import net.corda.data.virtualnode.VirtualNodeManagementRequest
import net.corda.data.virtualnode.VirtualNodeManagementResponse
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.messaging.api.publisher.RPCSender
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.virtualnode.rpcops.common.impl.RPCSenderFactory
import net.corda.virtualnode.rpcops.common.impl.RPCSenderWrapperImpl
import net.corda.virtualnode.rpcops.common.impl.VirtualNodeSenderServiceImpl
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Duration
import java.util.concurrent.CompletableFuture

class VirtualNodeSenderServiceTest {

    private val mockCoordinator = mock<LifecycleCoordinator>().apply {
        whenever(isRunning) doReturn true
    }
    private val mockCoordinatorFactory = mock<LifecycleCoordinatorFactory>().apply {
        whenever(createCoordinator(any(), any())) doReturn mockCoordinator
    }

    @Nested
    inner class LifecycleTests {
        private val mockDownCoordinator = mock<LifecycleCoordinator>().apply {
            whenever(isRunning) doReturn false
        }
        private val mockDownCoordinatorFactory = mock<LifecycleCoordinatorFactory>().apply {
            whenever(createCoordinator(any(), any())) doReturn mockDownCoordinator
        }

        @Test
        fun `verify coordinator is started on start`() {
            val vnodeRpcOps = VirtualNodeSenderServiceImpl(mockCoordinatorFactory, mock(), mock())
            vnodeRpcOps.start()

            verify(mockCoordinator).start()
        }

        @Test
        fun `verify coordinator is stopped on stop`() {
            val vnodeRpcOps = VirtualNodeSenderServiceImpl(mockCoordinatorFactory, mock(), mock())
            vnodeRpcOps.stop()

            verify(mockCoordinator).stop()
        }

        @Test
        fun `verify coordinator isRunning defers to the coordinator`() {
            val vnodeRpcOps = VirtualNodeSenderServiceImpl(mockCoordinatorFactory, mock(), mock())
            vnodeRpcOps.isRunning

            verify(mockCoordinator).isRunning
        }

        @Test
        fun `verify exception throw if sendAndReceive is performed while coordinator is not running`() {
            val vnodeMaintenanceRpcOps = VirtualNodeSenderServiceImpl(mockDownCoordinatorFactory, mock(), mock())
            assertThrows<IllegalStateException> {
                vnodeMaintenanceRpcOps.sendAndReceive(mock())
            }

            verify(mockDownCoordinator).isRunning
        }
    }

    @Disabled("TODO: Move to new tests within the new services")
    @Nested
    inner class ServiceApiTests {
        @Test
        fun `sendAndReceive throws CordaRuntimeException if sender is null`() {
            val service = VirtualNodeSenderServiceImpl(mockCoordinatorFactory, mock(), mock())
            val exception = assertThrows<CordaRuntimeException> {
                service.sendAndReceive(mock())
            }

            assertThat(exception.cause).isInstanceOf(NullPointerException::class.java)
        }

        @Test
        fun `sendAndReceive throws CordaRuntimeException if timeout is null`() {
            val service = VirtualNodeSenderServiceImpl(mockCoordinatorFactory, mock(), mock())
            val exception = assertThrows<CordaRuntimeException> {
                service.sendAndReceive(mock())
            }

            assertThat(exception.cause).isInstanceOf(NullPointerException::class.java)
        }

        @Test
        fun `Verify a sender can send a request when it has a non null timeout`() {
            val mockRequest = mock<VirtualNodeManagementRequest>()
            val mockResponse = mock<VirtualNodeManagementResponse>()
            val mockFuture = CompletableFuture<VirtualNodeManagementResponse>().apply {
                complete(mockResponse)
            }
            val sender = mock<RPCSender<VirtualNodeManagementRequest, VirtualNodeManagementResponse>>().apply {
                whenever(sendRequest(any())) doReturn mockFuture
            }
            val senderWrapper = RPCSenderWrapperImpl(Duration.ofMillis(1000), sender)
            val rpcSenderFactory = mock<RPCSenderFactory>().apply {
                whenever(createSender(any(), any())) doReturn senderWrapper
            }
            val service = VirtualNodeSenderServiceImpl(mockCoordinatorFactory, mock(), rpcSenderFactory)

            service.sendAndReceive(mockRequest)

            verify(sender).sendRequest(any())
        }
    }
}
