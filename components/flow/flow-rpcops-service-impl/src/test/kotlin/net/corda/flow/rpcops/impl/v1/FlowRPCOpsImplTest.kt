package net.corda.flow.rpcops.impl.v1

import java.time.Instant
import java.util.UUID
import net.corda.cpiinfo.read.CpiInfoReadService
import net.corda.data.flow.FlowKey
import net.corda.data.flow.output.FlowStatus
import net.corda.flow.rpcops.FlowRPCOpsServiceException
import net.corda.flow.rpcops.FlowStatusCacheService
import net.corda.flow.rpcops.factory.MessageFactory
import net.corda.flow.rpcops.v1.types.request.StartFlowParameters
import net.corda.httprpc.JsonObject
import net.corda.httprpc.exception.BadRequestException
import net.corda.httprpc.exception.InvalidInputDataException
import net.corda.httprpc.exception.ResourceAlreadyExistsException
import net.corda.httprpc.exception.ResourceNotFoundException
import net.corda.httprpc.ws.DuplexChannel
import net.corda.libs.configuration.SmartConfigImpl
import net.corda.libs.packaging.core.CordappManifest
import net.corda.libs.packaging.core.CpiIdentifier
import net.corda.libs.packaging.core.CpiMetadata
import net.corda.libs.packaging.core.CpkMetadata
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
    private lateinit var cpiInfoReadService: CpiInfoReadService
    private lateinit var publisher: Publisher

    private fun getMockCPIMeta(): CpiMetadata {
        val mockManifest = mock<CordappManifest>().also {
            whenever(it.rpcStartableFlows).thenReturn(setOf("flow1", "flow2"))
        }
        val mockCPKMetadata = mock<CpkMetadata>().also {
            whenever(it.cordappManifest).thenReturn(mockManifest)
        }

        return mock<CpiMetadata>().also {
            whenever(it.cpksMetadata).thenReturn(setOf(mockCPKMetadata))
        }
    }
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
        cpiInfoReadService  = mock()

        val cpiMetadata= getMockCPIMeta()
        whenever(cpiInfoReadService.get(any())).thenReturn(cpiMetadata)
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
            FlowRPCOpsImpl(virtualNodeInfoReadService, flowStatusCacheService, publisherFactory, messageFactory, cpiInfoReadService)
        flowRPCOps.initialise(SmartConfigImpl.empty())
        verify(publisherFactory, times(1)).createPublisher(any(), any())
    }

    @Test
    fun `get flow status`() {
        whenever(flowStatusCacheService.getStatus(any(), any())).thenReturn(FlowStatus())
        val flowRPCOps =
            FlowRPCOpsImpl(virtualNodeInfoReadService, flowStatusCacheService, publisherFactory, messageFactory, cpiInfoReadService)
        flowRPCOps.getFlowStatus("1234567890ab", "")

        verify(virtualNodeInfoReadService, times(1)).getByHoldingIdentityShortHash(any())
        verify(flowStatusCacheService, times(1)).getStatus(any(), any())
        verify(messageFactory, times(1)).createFlowStatusResponse(any())
    }

    @Test
    fun `get flow status throws resource not found if virtual node does not exist`() {
        whenever(virtualNodeInfoReadService.getByHoldingIdentityShortHash(any())).thenReturn(null)

        val flowRPCOps =
            FlowRPCOpsImpl(virtualNodeInfoReadService, flowStatusCacheService, publisherFactory, messageFactory, cpiInfoReadService)
        flowRPCOps.initialise(SmartConfigImpl.empty())

        assertThrows<ResourceNotFoundException> {
            flowRPCOps.getFlowStatus("1234567890ab", "")
        }

        verify(virtualNodeInfoReadService, times(1)).getByHoldingIdentityShortHash(any())
        verify(flowStatusCacheService, never()).getStatus(any(), any())
        verify(messageFactory, never()).createFlowStatusResponse(any())
    }

    @Test
    fun `get flow status throws bad request if short hash is invalid`() {
        val flowRPCOps =
            FlowRPCOpsImpl(virtualNodeInfoReadService, flowStatusCacheService, publisherFactory, messageFactory, cpiInfoReadService)
        flowRPCOps.initialise(SmartConfigImpl.empty())

        assertThrows<BadRequestException> {
            flowRPCOps.getFlowStatus("invalid", "")
        }

        verify(virtualNodeInfoReadService, never()).getByHoldingIdentityShortHash(any())
        verify(flowStatusCacheService, never()).getStatus(any(), any())
        verify(messageFactory, never()).createFlowStatusResponse(any())
    }

    @Test
    fun `get multiple flow status`() {
        whenever(flowStatusCacheService.getStatusesPerIdentity(any())).thenReturn(listOf(FlowStatus(), FlowStatus()))
        val flowRPCOps =
            FlowRPCOpsImpl(virtualNodeInfoReadService, flowStatusCacheService, publisherFactory, messageFactory, cpiInfoReadService)
        flowRPCOps.getMultipleFlowStatus("1234567890ab")

        verify(virtualNodeInfoReadService, times(1)).getByHoldingIdentityShortHash(any())
        verify(flowStatusCacheService, times(1)).getStatusesPerIdentity(any())
        verify(messageFactory, times(2)).createFlowStatusResponse(any())
    }

    @Test
    fun `get multiple flow status throws resource not found if virtual node does not exist`() {
        whenever(virtualNodeInfoReadService.getByHoldingIdentityShortHash(any())).thenReturn(null)

        val flowRPCOps =
            FlowRPCOpsImpl(virtualNodeInfoReadService, flowStatusCacheService, publisherFactory, messageFactory, cpiInfoReadService)
        flowRPCOps.initialise(SmartConfigImpl.empty())

        assertThrows<ResourceNotFoundException> {
            flowRPCOps.getMultipleFlowStatus("1234567890ab")
        }

        verify(virtualNodeInfoReadService, times(1)).getByHoldingIdentityShortHash(any())
        verify(flowStatusCacheService, never()).getStatusesPerIdentity(any())
        verify(messageFactory, never()).createFlowStatusResponse(any())
    }

    @Test
    fun `get multiple flow status throws bad request if short hash is invalid`() {
        val flowRPCOps =
            FlowRPCOpsImpl(virtualNodeInfoReadService, flowStatusCacheService, publisherFactory, messageFactory, cpiInfoReadService)
        flowRPCOps.initialise(SmartConfigImpl.empty())

        assertThrows<BadRequestException> {
            flowRPCOps.getMultipleFlowStatus("invalid")
        }

        verify(virtualNodeInfoReadService, never()).getByHoldingIdentityShortHash(any())
        verify(flowStatusCacheService, never()).getStatusesPerIdentity(any())
        verify(messageFactory, never()).createFlowStatusResponse(any())
    }

    data class TestJsonObject(override val escapedJson: String = "") : JsonObject

    @Test
    fun `start flow event triggers successfully`() {
        val flowRPCOps =
            FlowRPCOpsImpl(virtualNodeInfoReadService, flowStatusCacheService, publisherFactory, messageFactory, cpiInfoReadService)
        flowRPCOps.initialise(SmartConfigImpl.empty())

        whenever(messageFactory.createFlowStatusResponse(any())).thenReturn(mock())

        flowRPCOps.startFlow("1234567890ab", StartFlowParameters("", "flow1", TestJsonObject()))

        verify(virtualNodeInfoReadService, times(1)).getByHoldingIdentityShortHash(any())
        verify(cpiInfoReadService, times(1)).get(any())
        verify(flowStatusCacheService, times(1)).getStatus(any(), any())
        verify(messageFactory, times(1)).createStartFlowEvent(any(), any(), any(), any(), any())
        verify(messageFactory, times(1)).createStartFlowStatus(any(), any(), any())
        verify(publisher, times(1)).publish(any())
        verify(messageFactory, times(1)).createStartFlowStatus(any(), any(), any())
    }

    @Test
    fun `start flow event fails when not initialized`() {
        val flowRPCOps =
            FlowRPCOpsImpl(virtualNodeInfoReadService, flowStatusCacheService, publisherFactory, messageFactory, cpiInfoReadService)

        assertThrows<FlowRPCOpsServiceException> {
            flowRPCOps.startFlow("1234567890ab", StartFlowParameters("", "flow1", TestJsonObject()))
        }

        verify(virtualNodeInfoReadService, never()).getByHoldingIdentityShortHash(any())
        verify(cpiInfoReadService, never()).get(any())
        verify(flowStatusCacheService, never()).getStatus(any(), any())
        verify(messageFactory, never()).createStartFlowEvent(any(), any(), any(), any(), any())
        verify(messageFactory, never()).createStartFlowStatus(any(), any(), any())
        verify(publisher, never()).publish(any())
        verify(messageFactory, never()).createStartFlowStatus(any(), any(), any())
    }

    @Test
    fun `start flow event throws bad request if short hash is invalid`() {
        val flowRPCOps =
            FlowRPCOpsImpl(virtualNodeInfoReadService, flowStatusCacheService, publisherFactory, messageFactory, cpiInfoReadService)
        flowRPCOps.initialise(SmartConfigImpl.empty())

        assertThrows<BadRequestException> {
            flowRPCOps.startFlow("invalid", StartFlowParameters("", "", TestJsonObject()))
        }

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
            FlowRPCOpsImpl(virtualNodeInfoReadService, flowStatusCacheService, publisherFactory, messageFactory, cpiInfoReadService)
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
            FlowRPCOpsImpl(virtualNodeInfoReadService, flowStatusCacheService, publisherFactory, messageFactory, cpiInfoReadService)
        flowRPCOps.initialise(SmartConfigImpl.empty())

        whenever(flowStatusCacheService.getStatus(any(), any())).thenReturn(mock())
        assertThrows<ResourceAlreadyExistsException> {
            flowRPCOps.startFlow("1234567890ab", StartFlowParameters("", "flow1", TestJsonObject()))
        }

        verify(virtualNodeInfoReadService, times(1)).getByHoldingIdentityShortHash(any())
        verify(flowStatusCacheService, times(1)).getStatus(any(), any())
        verify(cpiInfoReadService, times(0)).get(any())
        verify(messageFactory, times(0)).createStartFlowEvent(any(), any(), any(), any(), any())
        verify(messageFactory, times(0)).createStartFlowStatus(any(), any(), any())
        verify(publisher, times(0)).publish(any())
        verify(messageFactory, times(0)).createStartFlowStatus(any(), any(), any())
    }

    @Test
    fun `start flow throws invalid data exception when starting invalid flows`() {
        val flowRPCOps =
            FlowRPCOpsImpl(virtualNodeInfoReadService, flowStatusCacheService, publisherFactory, messageFactory, cpiInfoReadService)
        flowRPCOps.initialise(SmartConfigImpl.empty())


        whenever(messageFactory.createFlowStatusResponse(any())).thenReturn(mock())

        assertThrows<InvalidInputDataException> {
            flowRPCOps.startFlow("1234567890ab", StartFlowParameters("requetsId", "invalid", TestJsonObject()))
        }

        verify(virtualNodeInfoReadService, times(1)).getByHoldingIdentityShortHash(any())
        verify(flowStatusCacheService, times(1)).getStatus(any(), any())
        verify(cpiInfoReadService, times(1)).get(any())
        verify(messageFactory, never()).createStartFlowEvent(any(), any(), any(), any(), any())
        verify(messageFactory, never()).createStartFlowStatus(any(), any(), any())
        verify(publisher, never()).publish(any())
        verify(messageFactory, never()).createStartFlowStatus(any(), any(), any())
    }

    @Test
    fun `start flow throws FlowRPCOpsServiceException exception when publish fails`() {
        val flowRPCOps =
            FlowRPCOpsImpl(virtualNodeInfoReadService, flowStatusCacheService, publisherFactory, messageFactory, cpiInfoReadService)
        flowRPCOps.initialise(SmartConfigImpl.empty())

        doThrow(CordaMessageAPIFatalException("")).whenever(publisher).publish(any())
        assertThrows<FlowRPCOpsServiceException> {
            flowRPCOps.startFlow("1234567890ab", StartFlowParameters("", "flow1", TestJsonObject()))
        }

        verify(virtualNodeInfoReadService, times(1)).getByHoldingIdentityShortHash(any())
        verify(flowStatusCacheService, times(1)).getStatus(any(), any())
        verify(messageFactory, times(1)).createStartFlowEvent(any(), any(), any(), any(), any())
        verify(messageFactory, times(1)).createStartFlowStatus(any(), any(), any())
        verify(publisher, times(1)).publish(any())
        verify(messageFactory, times(1)).createStartFlowStatus(any(), any(), any())
    }

    @Test
    fun `registerFlowStatusUpdatesFeed sends resource not found if virtual node does not exist`() {
        val duplexChannel = mock<DuplexChannel>()
        val exceptionArgumentCaptor = argumentCaptor<Exception>()

        whenever(virtualNodeInfoReadService.getByHoldingIdentityShortHash(any())).thenReturn(null)
        doNothing().whenever(duplexChannel).error(exceptionArgumentCaptor.capture())

        val flowRPCOps =
            FlowRPCOpsImpl(virtualNodeInfoReadService, flowStatusCacheService, publisherFactory, messageFactory, cpiInfoReadService)
        flowRPCOps.initialise(SmartConfigImpl.empty())

        flowRPCOps.registerFlowStatusUpdatesFeed(duplexChannel, "1234567890ab", "")

        verify(virtualNodeInfoReadService, times(1)).getByHoldingIdentityShortHash(any())
        verify(duplexChannel, times(1)).error(any())
        assertInstanceOf(ResourceNotFoundException::class.java, exceptionArgumentCaptor.firstValue.cause)
    }

    @Test
    fun `registerFlowStatusUpdatesFeed sends bad request if short hash is invalid`() {
        val duplexChannel = mock<DuplexChannel>()
        val exceptionArgumentCaptor = argumentCaptor<Exception>()

        doNothing().whenever(duplexChannel).error(exceptionArgumentCaptor.capture())

        val flowRPCOps =
            FlowRPCOpsImpl(virtualNodeInfoReadService, flowStatusCacheService, publisherFactory, messageFactory, cpiInfoReadService)
        flowRPCOps.initialise(SmartConfigImpl.empty())

        flowRPCOps.registerFlowStatusUpdatesFeed(duplexChannel, "invalid", "")

        verify(virtualNodeInfoReadService, never()).getByHoldingIdentityShortHash(any())
        verify(duplexChannel, times(1)).error(any())
        assertInstanceOf(BadRequestException::class.java, exceptionArgumentCaptor.firstValue.cause)
    }

}
