package net.corda.virtualnode.rpcops.common.impl

import net.corda.data.virtualnode.VirtualNodeManagementRequest
import net.corda.data.virtualnode.VirtualNodeManagementResponse
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.SmartConfigImpl
import net.corda.messaging.api.publisher.RPCSender
import net.corda.messaging.api.publisher.factory.PublisherFactory
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.spy
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Duration

internal class VirtualNodeSenderFactoryImplTest {
    private val mockRpcSender = mock<RPCSender<VirtualNodeManagementRequest, VirtualNodeManagementResponse>>()
    private val mockPublisherFactory = mock<PublisherFactory>().apply {
        whenever(createRPCSender<VirtualNodeManagementRequest, VirtualNodeManagementResponse>(any(), any())) doReturn mockRpcSender
    }
    private val senderFactory = VirtualNodeSenderFactoryImpl(mockPublisherFactory)
    @Test
    fun `Validate creation of underlying sender wrapper`() {
        val duration = Duration.ofMillis(1000)
        val config = spy<SmartConfig>().apply {
            SmartConfigImpl(mock(), mock(), mock())
        }
        val ret = senderFactory.createSender(duration, config)
        verify(mockPublisherFactory).createRPCSender<VirtualNodeManagementRequest, VirtualNodeManagementResponse>(
            any(), eq(config)
        )
        assertThat(ret.timeout).isEqualTo(duration)
    }
}
