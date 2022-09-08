package net.corda.flow.rpcops.impl.v1

import java.time.Instant
import java.util.UUID
import net.corda.data.flow.FlowKey
import net.corda.data.flow.output.FlowStatus
import net.corda.flow.rpcops.FlowRPCOpsServiceException
import net.corda.flow.rpcops.FlowStatusCacheService
import net.corda.flow.rpcops.factory.MessageFactory
import net.corda.flow.rpcops.v1.types.request.StartFlowParameters
import net.corda.httprpc.JsonObject
import net.corda.httprpc.exception.ResourceAlreadyExistsException
import net.corda.httprpc.exception.ResourceNotFoundException
import net.corda.httprpc.ws.DuplexChannel
import net.corda.libs.configuration.SmartConfigImpl
import net.corda.libs.packaging.core.CpiIdentifier
import net.corda.messaging.api.exception.CordaMessageAPIFatalException
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.test.util.identity.createTestHoldingIdentity
import net.corda.v5.crypto.SecureHash
import net.corda.virtualnode.VirtualNodeInfo
import net.corda.virtualnode.VirtualNodeState
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doNothing
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
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
        return VirtualNodeInfo(
            createTestHoldingIdentity("CN=Bob, O=Bob Corp, L=LDN, C=GB", ""),
            CpiIdentifier(
                "", "",
                SecureHash("", "bytes".toByteArray())
            ),
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            VirtualNodeState.ACTIVE,
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
        whenever(messageFactory.createStartFlowStatus(any(), any(), any())).thenReturn(FlowStatus().apply {
            key = FlowKey()
        })
        whenever(publisherFactory.createPublisher(any(), any())).thenReturn(publisher)
        whenever(publisher.publish(any())).thenReturn(arrayListOf())
    }

    @Test
    fun `initialize creates the publisher`() {
        val flowRPCOps =
            FlowRPCOpsImpl(virtualNodeInfoReadService, flowStatusCacheService, publisherFactory, messageFactory)
        flowRPCOps.initialise(SmartConfigImpl.empty())
        verify(publisherFactory, times(1)).createPublisher(any(), any())
    }

    @Test
    fun `get flow status`() {
        whenever(flowStatusCacheService.getStatus(any(), any())).thenReturn(FlowStatus())
        val flowRPCOps =
            FlowRPCOpsImpl(virtualNodeInfoReadService, flowStatusCacheService, publisherFactory, messageFactory)
        flowRPCOps.getFlowStatus("1234567890ab", "")

        verify(virtualNodeInfoReadService, times(1)).getByHoldingIdentityShortHash(any())
        verify(flowStatusCacheService, times(1)).getStatus(any(), any())
        verify(messageFactory, times(1)).createFlowStatusResponse(any())
    }

    @Test
    fun `get flow status throws resource not found if virtual node does not exist`() {
        whenever(virtualNodeInfoReadService.getByHoldingIdentityShortHash(any())).thenReturn(null)

        val flowRPCOps =
            FlowRPCOpsImpl(virtualNodeInfoReadService, flowStatusCacheService, publisherFactory, messageFactory)
        flowRPCOps.initialise(SmartConfigImpl.empty())

        assertThrows<ResourceNotFoundException> {
            flowRPCOps.getFlowStatus("1234567890ab", "")
        }

        verify(virtualNodeInfoReadService, times(1)).getByHoldingIdentityShortHash(any())
        verify(flowStatusCacheService, never()).getStatus(any(), any())
        verify(messageFactory, never()).createFlowStatusResponse(any())
    }

    @Test
    fun `get multiple flow status`() {
        whenever(flowStatusCacheService.getStatusesPerIdentity(any())).thenReturn(listOf(FlowStatus(), FlowStatus()))
        val flowRPCOps =
            FlowRPCOpsImpl(virtualNodeInfoReadService, flowStatusCacheService, publisherFactory, messageFactory)
        flowRPCOps.getMultipleFlowStatus("1234567890ab")

        verify(virtualNodeInfoReadService, times(1)).getByHoldingIdentityShortHash(any())
        verify(flowStatusCacheService, times(1)).getStatusesPerIdentity(any())
        verify(messageFactory, times(2)).createFlowStatusResponse(any())
    }

    @Test
    fun `get multiple flow status throws resource not found if virtual node does not exist`() {
        whenever(virtualNodeInfoReadService.getByHoldingIdentityShortHash(any())).thenReturn(null)

        val flowRPCOps =
            FlowRPCOpsImpl(virtualNodeInfoReadService, flowStatusCacheService, publisherFactory, messageFactory)
        flowRPCOps.initialise(SmartConfigImpl.empty())

        assertThrows<ResourceNotFoundException> {
            flowRPCOps.getMultipleFlowStatus("1234567890ab")
        }

        verify(virtualNodeInfoReadService, times(1)).getByHoldingIdentityShortHash(any())
        verify(flowStatusCacheService, never()).getStatusesPerIdentity(any())
        verify(messageFactory, never()).createFlowStatusResponse(any())
    }

    data class TestJsonObject(override val escapedJson: String = "") : JsonObject

    @Test
    fun `start flow event triggers successfully`() {
        val flowRPCOps =
            FlowRPCOpsImpl(virtualNodeInfoReadService, flowStatusCacheService, publisherFactory, messageFactory)
        flowRPCOps.initialise(SmartConfigImpl.empty())

        whenever(messageFactory.createFlowStatusResponse(any())).thenReturn(mock())

        flowRPCOps.startFlow("1234567890ab", StartFlowParameters("", "", TestJsonObject()))

        verify(virtualNodeInfoReadService, times(1)).getByHoldingIdentityShortHash(any())
        verify(flowStatusCacheService, times(1)).getStatus(any(), any())
        verify(messageFactory, times(1)).createStartFlowEvent(any(), any(), any(), any(), any())
        verify(messageFactory, times(1)).createStartFlowStatus(any(), any(), any())
        verify(publisher, times(1)).publish(any())
        verify(messageFactory, times(1)).createStartFlowStatus(any(), any(), any())
    }

    @Test
    fun `start flow event fails when not initialized`() {
        val flowRPCOps =
            FlowRPCOpsImpl(virtualNodeInfoReadService, flowStatusCacheService, publisherFactory, messageFactory)

        assertThrows<FlowRPCOpsServiceException> {
            flowRPCOps.startFlow("1234567890ab", StartFlowParameters("", "", TestJsonObject()))
        }

        verify(virtualNodeInfoReadService, never()).getByHoldingIdentityShortHash(any())
        verify(flowStatusCacheService, never()).getStatus(any(), any())
        verify(messageFactory, never()).createStartFlowEvent(any(), any(), any(), any(), any())
        verify(messageFactory, never()).createStartFlowStatus(any(), any(), any())
        verify(publisher, never()).publish(any())
        verify(messageFactory, never()).createStartFlowStatus(any(), any(), any())
    }

    @Test
    fun `start flow event throws resource not found if virtual node does not exist`() {
        whenever(virtualNodeInfoReadService.getByHoldingIdentityShortHash(any())).thenReturn(null)

        val flowRPCOps =
            FlowRPCOpsImpl(virtualNodeInfoReadService, flowStatusCacheService, publisherFactory, messageFactory)
        flowRPCOps.initialise(SmartConfigImpl.empty())

        assertThrows<ResourceNotFoundException> {
            flowRPCOps.startFlow("1234567890ab", StartFlowParameters("", "", TestJsonObject()))
        }

        verify(flowStatusCacheService, never()).getStatus(any(), any())
        verify(messageFactory, never()).createStartFlowEvent(any(), any(), any(), any(), any())
        verify(messageFactory, never()).createStartFlowStatus(any(), any(), any())
        verify(publisher, never()).publish(any())
        verify(messageFactory, never()).createStartFlowStatus(any(), any(), any())
    }

    @Test
    fun `start flow throws resource exists exception for same criteria`() {
        val flowRPCOps =
            FlowRPCOpsImpl(virtualNodeInfoReadService, flowStatusCacheService, publisherFactory, messageFactory)
        flowRPCOps.initialise(SmartConfigImpl.empty())

        whenever(flowStatusCacheService.getStatus(any(), any())).thenReturn(mock())
        assertThrows<ResourceAlreadyExistsException> {
            flowRPCOps.startFlow("1234567890ab", StartFlowParameters("", "", TestJsonObject()))
        }

        verify(virtualNodeInfoReadService, times(1)).getByHoldingIdentityShortHash(any())
        verify(flowStatusCacheService, times(1)).getStatus(any(), any())
        verify(messageFactory, never()).createStartFlowEvent(any(), any(), any(), any(), any())
        verify(messageFactory, never()).createStartFlowStatus(any(), any(), any())
        verify(publisher, never()).publish(any())
        verify(messageFactory, never()).createStartFlowStatus(any(), any(), any())
    }

    @Test
    fun `start flow throws FlowRPCOpsServiceException exception when publish fails`() {
        val flowRPCOps =
            FlowRPCOpsImpl(virtualNodeInfoReadService, flowStatusCacheService, publisherFactory, messageFactory)
        flowRPCOps.initialise(SmartConfigImpl.empty())

        doThrow(CordaMessageAPIFatalException("")).whenever(publisher).publish(any())
        assertThrows<FlowRPCOpsServiceException> {
            flowRPCOps.startFlow("1234567890ab", StartFlowParameters("", "", TestJsonObject()))
        }

        verify(virtualNodeInfoReadService, times(1)).getByHoldingIdentityShortHash(any())
        verify(flowStatusCacheService, times(1)).getStatus(any(), any())
        verify(messageFactory, times(1)).createStartFlowEvent(any(), any(), any(), any(), any())
        verify(messageFactory, times(1)).createStartFlowStatus(any(), any(), any())
        verify(publisher, times(1)).publish(any())
        verify(messageFactory, times(1)).createStartFlowStatus(any(), any(), any())
    }

    @Test
    fun `registerFlowStatusUpdatesFeed throws resource not found if virtual node does not exist`() {
        val duplexChannel = mock<DuplexChannel>()
        val exceptionArgumentCaptor = argumentCaptor<Exception>()

        whenever(virtualNodeInfoReadService.getByHoldingIdentityShortHash(any())).thenReturn(null)
        doNothing().whenever(duplexChannel).error(exceptionArgumentCaptor.capture())

        val flowRPCOps =
            FlowRPCOpsImpl(virtualNodeInfoReadService, flowStatusCacheService, publisherFactory, messageFactory)
        flowRPCOps.initialise(SmartConfigImpl.empty())

        flowRPCOps.registerFlowStatusUpdatesFeed(duplexChannel, "1234567890ab", "")

        verify(virtualNodeInfoReadService, times(1)).getByHoldingIdentityShortHash(any())
        verify(duplexChannel, times(1)).error(any())
        assertInstanceOf(ResourceNotFoundException::class.java, exceptionArgumentCaptor.firstValue.cause)
    }

}
