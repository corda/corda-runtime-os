package net.corda.flow.rpcops.impl.v1

import java.time.Instant
import java.util.UUID
import net.corda.data.flow.FlowKey
import net.corda.data.flow.output.FlowStatus
import net.corda.flow.rpcops.FlowRPCOpsServiceException
import net.corda.flow.rpcops.FlowStatusCacheService
import net.corda.flow.rpcops.factory.MessageFactory
import net.corda.flow.rpcops.v1.types.request.StartFlowParameters
import net.corda.httprpc.exception.ResourceAlreadyExistsException
import net.corda.libs.configuration.SmartConfigImpl
import net.corda.libs.packaging.core.CpiIdentifier
import net.corda.messaging.api.exception.CordaMessageAPIFatalException
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.v5.crypto.SecureHash
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.VirtualNodeInfo
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class FlowRPCOpsImplTest {

    private lateinit var flowStatusCacheService: FlowStatusCacheService
    private lateinit var virtualNodeInfoReadService: VirtualNodeInfoReadService
    private lateinit var publisherFactory: PublisherFactory
    private lateinit var messageFactory: MessageFactory
    private lateinit var publisher: Publisher

    private fun getStubVirtualNode(): VirtualNodeInfo {
        return VirtualNodeInfo(HoldingIdentity("", ""),
            CpiIdentifier("", "",
            SecureHash("", "bytes".toByteArray())),
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            0,
            Instant.now()
        )
    }
    
    @BeforeEach
    fun setup() {
        flowStatusCacheService = mock()
        publisherFactory = mock()
        publisher = mock()
        messageFactory = mock()
        virtualNodeInfoReadService = mock()

        whenever(virtualNodeInfoReadService.getByHoldingIdentityShortHash(any())).thenReturn(getStubVirtualNode())
        whenever(flowStatusCacheService.getStatus(any(), any())).thenReturn(null)
        whenever(messageFactory.createStartFlowStatus(any(), any(), any())).thenReturn(FlowStatus().apply { key = FlowKey() })
        whenever(publisherFactory.createPublisher(any(), any())).thenReturn(publisher)
        whenever(publisher.publish(any())).thenReturn(arrayListOf())
    }

    @Test
    fun `initialize creates the publisher`() {
        val flowRPCOps = FlowRPCOpsImpl(virtualNodeInfoReadService, flowStatusCacheService, publisherFactory, messageFactory)
        flowRPCOps.initialise(SmartConfigImpl.empty())
        verify(publisherFactory, times(1)).createPublisher(any(), any())
    }

    @Test
    fun `get flow status`() {
        whenever(flowStatusCacheService.getStatus(any(), any())).thenReturn(FlowStatus())
        val flowRPCOps = FlowRPCOpsImpl(virtualNodeInfoReadService, flowStatusCacheService, publisherFactory, messageFactory)
        flowRPCOps.getFlowStatus("", "")

        verify(virtualNodeInfoReadService, times(1)).getByHoldingIdentityShortHash(any())
        verify(flowStatusCacheService, times(1)).getStatus(any(), any())
        verify(messageFactory, times(1)).createFlowStatusResponse(any())
    }

    @Test
    fun `get multiple flow status`() {
        whenever(flowStatusCacheService.getStatusesPerIdentity(any())).thenReturn(listOf(FlowStatus(), FlowStatus()))
        val flowRPCOps = FlowRPCOpsImpl(virtualNodeInfoReadService, flowStatusCacheService, publisherFactory, messageFactory)
        flowRPCOps.getMultipleFlowStatus("")

        verify(virtualNodeInfoReadService, times(1)).getByHoldingIdentityShortHash(any())
        verify(flowStatusCacheService, times(1)).getStatusesPerIdentity(any())
        verify(messageFactory, times(2)).createFlowStatusResponse(any())
    }

    @Test
    fun `start flow event triggers successfully`() {
        val flowRPCOps = FlowRPCOpsImpl(virtualNodeInfoReadService, flowStatusCacheService, publisherFactory, messageFactory)
        flowRPCOps.initialise(SmartConfigImpl.empty())
        flowRPCOps.startFlow("", StartFlowParameters("", "", ""))

        verify(virtualNodeInfoReadService, times(1)).getByHoldingIdentityShortHash(any())
        verify(flowStatusCacheService, times(1)).getStatus(any(), any())
        verify(messageFactory, times(1)).createStartFlowEvent(any(), any(), any(), any())
        verify(messageFactory, times(1)).createStartFlowStatus(any(), any(), any())
        verify(publisher, times(1)).publish(any())
        verify(messageFactory, times(1)).createStartFlowStatus(any(), any(), any())
        verify(messageFactory, times(1)).createFlowStatusResponse(any())
    }

    @Test
    fun `start flow event fails when not initialized`() {
        val flowRPCOps = FlowRPCOpsImpl(virtualNodeInfoReadService, flowStatusCacheService, publisherFactory, messageFactory)

        assertThrows<FlowRPCOpsServiceException> {
            flowRPCOps.startFlow("", StartFlowParameters("", "", ""))
        }

        verify(virtualNodeInfoReadService, times(0)).getByHoldingIdentityShortHash(any())
        verify(flowStatusCacheService, times(0)).getStatus(any(), any())
        verify(messageFactory, times(0)).createStartFlowEvent(any(), any(), any(), any())
        verify(messageFactory, times(0)).createStartFlowStatus(any(), any(), any())
        verify(publisher, times(0)).publish(any())
        verify(messageFactory, times(0)).createStartFlowStatus(any(), any(), any())
    }

    @Test
    fun `start flow throws resource exists exception for same criteria`() {
        val flowRPCOps = FlowRPCOpsImpl(virtualNodeInfoReadService, flowStatusCacheService, publisherFactory, messageFactory)
        flowRPCOps.initialise(SmartConfigImpl.empty())

        whenever(flowStatusCacheService.getStatus(any(), any())).thenReturn(mock())
        assertThrows<ResourceAlreadyExistsException> {
            flowRPCOps.startFlow("", StartFlowParameters("", "", ""))
        }

        verify(virtualNodeInfoReadService, times(1)).getByHoldingIdentityShortHash(any())
        verify(flowStatusCacheService, times(1)).getStatus(any(), any())
        verify(messageFactory, times(0)).createStartFlowEvent(any(), any(), any(), any())
        verify(messageFactory, times(0)).createStartFlowStatus(any(), any(), any())
        verify(publisher, times(0)).publish(any())
        verify(messageFactory, times(0)).createStartFlowStatus(any(), any(), any())
    }

    @Test
    fun `start flow throws FlowRPCOpsServiceException exception when publish fails`() {
        val flowRPCOps = FlowRPCOpsImpl(virtualNodeInfoReadService, flowStatusCacheService, publisherFactory, messageFactory)
        flowRPCOps.initialise(SmartConfigImpl.empty())

        doThrow(CordaMessageAPIFatalException("")).whenever(publisher).publish(any())
        assertThrows<FlowRPCOpsServiceException> {
            flowRPCOps.startFlow("", StartFlowParameters("", "", ""))
        }

        verify(virtualNodeInfoReadService, times(1)).getByHoldingIdentityShortHash(any())
        verify(flowStatusCacheService, times(1)).getStatus(any(), any())
        verify(messageFactory, times(1)).createStartFlowEvent(any(), any(), any(), any())
        verify(messageFactory, times(1)).createStartFlowStatus(any(), any(), any())
        verify(publisher, times(1)).publish(any())
        verify(messageFactory, times(1)).createStartFlowStatus(any(), any(), any())
    }

}
