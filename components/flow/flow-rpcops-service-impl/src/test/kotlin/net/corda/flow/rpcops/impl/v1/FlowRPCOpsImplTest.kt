package net.corda.flow.rpcops.impl.v1

import net.corda.cpiinfo.read.CpiInfoReadService
import net.corda.data.flow.FlowKey
import net.corda.data.flow.output.FlowStatus
import net.corda.flow.rpcops.FlowRPCOpsServiceException
import net.corda.flow.rpcops.FlowStatusCacheService
import net.corda.flow.rpcops.factory.MessageFactory
import net.corda.flow.rpcops.v1.FlowRestResource
import net.corda.flow.rpcops.v1.types.request.StartFlowParameters
import net.corda.httprpc.JsonObject
import net.corda.httprpc.exception.BadRequestException
import net.corda.httprpc.exception.ForbiddenException
import net.corda.httprpc.exception.InvalidInputDataException
import net.corda.httprpc.exception.ResourceAlreadyExistsException
import net.corda.httprpc.exception.ResourceNotFoundException
import net.corda.httprpc.security.CURRENT_RPC_CONTEXT
import net.corda.httprpc.security.RpcAuthContext
import net.corda.httprpc.ws.DuplexChannel
import net.corda.libs.configuration.SmartConfigImpl
import net.corda.libs.packaging.core.CordappManifest
import net.corda.libs.packaging.core.CpiIdentifier
import net.corda.libs.packaging.core.CpiMetadata
import net.corda.libs.packaging.core.CpkMetadata
import net.corda.libs.permission.PermissionValidator
import net.corda.messaging.api.exception.CordaMessageAPIFatalException
import net.corda.messaging.api.exception.CordaMessageAPIIntermittentException
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.permissions.validation.PermissionValidationService
import net.corda.rbac.schema.RbacKeys.PREFIX_SEPARATOR
import net.corda.rbac.schema.RbacKeys.START_FLOW_PREFIX
import net.corda.test.util.identity.createTestHoldingIdentity
import net.corda.v5.crypto.SecureHash
import net.corda.virtualnode.VirtualNodeInfo
import net.corda.virtualnode.VirtualNodeState
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.atLeastOnce
import org.mockito.kotlin.doNothing
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CompletableFuture

class FlowRPCOpsImplTest {

    private lateinit var flowStatusCacheService: FlowStatusCacheService
    private lateinit var virtualNodeInfoReadService: VirtualNodeInfoReadService
    private lateinit var publisherFactory: PublisherFactory
    private lateinit var messageFactory: MessageFactory
    private lateinit var cpiInfoReadService: CpiInfoReadService
    private lateinit var publisher: Publisher
    private lateinit var permissionValidationService: PermissionValidationService
    private lateinit var permissionValidator: PermissionValidator
    private lateinit var fatalErrorFunction: () -> Unit
    private val clientRequestId = UUID.randomUUID().toString()

    private companion object {
        val loginName = "${FlowRPCOpsImplTest::class.java.simpleName}-User"
        const val FLOW1 = "flow1"
        const val VALID_SHORT_HASH = "1234567890ab"
    }

    private fun getMockCPIMeta(): CpiMetadata {

        val mockManifest = mock<CordappManifest>().also {
            whenever(it.clientStartableFlows).thenReturn(setOf(FLOW1, "flow2"))
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
        cpiInfoReadService = mock()
        permissionValidationService = mock()
        permissionValidator = mock()
        fatalErrorFunction = mock()

        val cpiMetadata = getMockCPIMeta()
        whenever(cpiInfoReadService.get(any())).thenReturn(cpiMetadata)
        whenever(virtualNodeInfoReadService.getByHoldingIdentityShortHash(any())).thenReturn(getStubVirtualNode())
        whenever(flowStatusCacheService.getStatus(any(), any())).thenReturn(null)
        whenever(messageFactory.createStartFlowStatus(any(), any(), any())).thenReturn(FlowStatus().apply {
            key = FlowKey()
        })
        whenever(publisherFactory.createPublisher(any(), any())).thenReturn(publisher)
        whenever(publisher.publish(any())).thenReturn(listOf(CompletableFuture<Unit>().apply { complete(Unit) }))

        val rpcAuthContext = mock<RpcAuthContext>().apply {
            whenever(principal).thenReturn(loginName)
        }
        CURRENT_RPC_CONTEXT.set(rpcAuthContext)

        whenever(permissionValidationService.permissionValidator).thenReturn(permissionValidator)

        whenever(
            permissionValidator.authorizeUser(
                eq(loginName),
                any()
            )
        ).thenReturn(true)
    }

    private fun createFlowRpcOps(initialise: Boolean = true): FlowRestResource {
        return FlowRestResourceImpl(
            virtualNodeInfoReadService,
            flowStatusCacheService,
            publisherFactory,
            messageFactory,
            cpiInfoReadService,
            permissionValidationService
        ).apply { if (initialise) (initialise(SmartConfigImpl.empty(), fatalErrorFunction)) }
    }

    @Test
    fun `initialize creates the publisher`() {
        createFlowRpcOps()
        verify(publisherFactory, times(1)).createPublisher(any(), any())
    }

    @Test
    fun `get flow status`() {
        whenever(flowStatusCacheService.getStatus(any(), any())).thenReturn(FlowStatus())
        val flowRPCOps = createFlowRpcOps()
        flowRPCOps.getFlowStatus(VALID_SHORT_HASH, clientRequestId)

        verify(virtualNodeInfoReadService, times(1)).getByHoldingIdentityShortHash(any())
        verify(flowStatusCacheService, times(1)).getStatus(any(), any())
        verify(messageFactory, times(1)).createFlowStatusResponse(any())
        verify(fatalErrorFunction, never()).invoke()
    }

    @Test
    fun `get flow status throws resource not found if virtual node does not exist`() {
        whenever(virtualNodeInfoReadService.getByHoldingIdentityShortHash(any())).thenReturn(null)

        val flowRPCOps = createFlowRpcOps()

        assertThrows<ResourceNotFoundException> {
            flowRPCOps.getFlowStatus(VALID_SHORT_HASH, clientRequestId)
        }

        verify(virtualNodeInfoReadService, times(1)).getByHoldingIdentityShortHash(any())
        verify(flowStatusCacheService, never()).getStatus(any(), any())
        verify(messageFactory, never()).createFlowStatusResponse(any())
        verify(fatalErrorFunction, never()).invoke()
    }

    @ParameterizedTest
    @ValueSource(strings = ["invalid", "${VALID_SHORT_HASH}AB"])
    fun `get flow status throws bad request if short hash is invalid`(invalidShortHash: String) {
        val flowRPCOps = createFlowRpcOps()

        assertThrows<BadRequestException> {
            flowRPCOps.getFlowStatus(invalidShortHash, "")
        }

        verify(virtualNodeInfoReadService, never()).getByHoldingIdentityShortHash(any())
        verify(flowStatusCacheService, never()).getStatus(any(), any())
        verify(messageFactory, never()).createFlowStatusResponse(any())
        verify(fatalErrorFunction, never()).invoke()
    }

    @Test
    fun `get multiple flow status`() {
        whenever(flowStatusCacheService.getStatusesPerIdentity(any())).thenReturn(listOf(FlowStatus(), FlowStatus()))
        val flowRPCOps = createFlowRpcOps()
        flowRPCOps.getMultipleFlowStatus(VALID_SHORT_HASH)

        verify(virtualNodeInfoReadService, times(1)).getByHoldingIdentityShortHash(any())
        verify(flowStatusCacheService, times(1)).getStatusesPerIdentity(any())
        verify(messageFactory, times(2)).createFlowStatusResponse(any())
        verify(fatalErrorFunction, never()).invoke()
    }

    @Test
    fun `get multiple flow status throws resource not found if virtual node does not exist`() {
        whenever(virtualNodeInfoReadService.getByHoldingIdentityShortHash(any())).thenReturn(null)

        val flowRPCOps = createFlowRpcOps()

        assertThrows<ResourceNotFoundException> {
            flowRPCOps.getMultipleFlowStatus(VALID_SHORT_HASH)
        }

        verify(virtualNodeInfoReadService, times(1)).getByHoldingIdentityShortHash(any())
        verify(flowStatusCacheService, never()).getStatusesPerIdentity(any())
        verify(messageFactory, never()).createFlowStatusResponse(any())
        verify(fatalErrorFunction, never()).invoke()
    }

    @ParameterizedTest
    @ValueSource(strings = ["invalid", "${VALID_SHORT_HASH}AB"])
    fun `get multiple flow status throws bad request if short hash is invalid`(invalidShortHash: String) {
        val flowRPCOps = createFlowRpcOps()

        assertThrows<BadRequestException> {
            flowRPCOps.getMultipleFlowStatus(invalidShortHash)
        }

        verify(virtualNodeInfoReadService, never()).getByHoldingIdentityShortHash(any())
        verify(flowStatusCacheService, never()).getStatusesPerIdentity(any())
        verify(messageFactory, never()).createFlowStatusResponse(any())
        verify(fatalErrorFunction, never()).invoke()
    }

    data class TestJsonObject(override val escapedJson: String = "") : JsonObject

    @Test
    fun `start flow event triggers successfully`() {
        val flowRPCOps = createFlowRpcOps()

        whenever(messageFactory.createFlowStatusResponse(any())).thenReturn(mock())

        flowRPCOps.startFlow(VALID_SHORT_HASH, StartFlowParameters(clientRequestId, FLOW1, TestJsonObject()))

        verify(virtualNodeInfoReadService, times(1)).getByHoldingIdentityShortHash(any())
        verify(cpiInfoReadService, times(1)).get(any())
        verify(flowStatusCacheService, times(1)).getStatus(any(), any())
        verify(messageFactory, times(1)).createStartFlowEvent(any(), any(), any(), any(), any())
        verify(messageFactory, times(1)).createStartFlowStatus(any(), any(), any())
        verify(publisher, times(1)).publish(any())
        verify(messageFactory, times(1)).createStartFlowStatus(any(), any(), any())
        verify(fatalErrorFunction, never()).invoke()
    }

    @Test
    fun `start flow event fails when not initialized`() {
        val flowRPCOps = createFlowRpcOps(false)

        assertThrows<FlowRPCOpsServiceException> {
            flowRPCOps.startFlow(VALID_SHORT_HASH, StartFlowParameters(clientRequestId, FLOW1, TestJsonObject()))
        }

        verify(virtualNodeInfoReadService, never()).getByHoldingIdentityShortHash(any())
        verify(cpiInfoReadService, never()).get(any())
        verify(flowStatusCacheService, never()).getStatus(any(), any())
        verify(messageFactory, never()).createStartFlowEvent(any(), any(), any(), any(), any())
        verify(messageFactory, never()).createStartFlowStatus(any(), any(), any())
        verify(publisher, never()).publish(any())
        verify(messageFactory, never()).createStartFlowStatus(any(), any(), any())
        verify(fatalErrorFunction, never()).invoke()
    }

    @ParameterizedTest
    @ValueSource(strings = ["invalid", "${VALID_SHORT_HASH}AB"])
    fun `start flow event throws bad request if short hash is invalid`(invalidShortHash: String) {
        val flowRPCOps = createFlowRpcOps()

        assertThrows<BadRequestException> {
            flowRPCOps.startFlow(invalidShortHash, StartFlowParameters(clientRequestId, "", TestJsonObject()))
        }

        verify(flowStatusCacheService, never()).getStatus(any(), any())
        verify(messageFactory, never()).createStartFlowEvent(any(), any(), any(), any(), any())
        verify(messageFactory, never()).createStartFlowStatus(any(), any(), any())
        verify(publisher, never()).publish(any())
        verify(messageFactory, never()).createStartFlowStatus(any(), any(), any())
        verify(fatalErrorFunction, never()).invoke()
    }

    @Test
    fun `start flow event throws resource not found if virtual node does not exist`() {
        whenever(virtualNodeInfoReadService.getByHoldingIdentityShortHash(any())).thenReturn(null)

        val flowRPCOps = createFlowRpcOps()

        assertThrows<ResourceNotFoundException> {
            flowRPCOps.startFlow(VALID_SHORT_HASH, StartFlowParameters(clientRequestId, "", TestJsonObject()))
        }

        verify(flowStatusCacheService, never()).getStatus(any(), any())
        verify(messageFactory, never()).createStartFlowEvent(any(), any(), any(), any(), any())
        verify(messageFactory, never()).createStartFlowStatus(any(), any(), any())
        verify(publisher, never()).publish(any())
        verify(messageFactory, never()).createStartFlowStatus(any(), any(), any())
        verify(fatalErrorFunction, never()).invoke()
    }

    @Test
    fun `start flow throws resource exists exception for same criteria`() {
        val flowRPCOps = createFlowRpcOps()

        whenever(flowStatusCacheService.getStatus(any(), any())).thenReturn(mock())
        assertThrows<ResourceAlreadyExistsException> {
            flowRPCOps.startFlow(VALID_SHORT_HASH, StartFlowParameters(clientRequestId, FLOW1, TestJsonObject()))
        }

        verify(virtualNodeInfoReadService, times(1)).getByHoldingIdentityShortHash(any())
        verify(flowStatusCacheService, times(1)).getStatus(any(), any())
        verify(cpiInfoReadService, times(0)).get(any())
        verify(messageFactory, times(0)).createStartFlowEvent(any(), any(), any(), any(), any())
        verify(messageFactory, times(0)).createStartFlowStatus(any(), any(), any())
        verify(publisher, times(0)).publish(any())
        verify(messageFactory, times(0)).createStartFlowStatus(any(), any(), any())
        verify(fatalErrorFunction, never()).invoke()
    }

    @Test
    fun `start flow throws invalid data exception when starting invalid flows`() {
        val flowRPCOps = createFlowRpcOps()

        whenever(messageFactory.createFlowStatusResponse(any())).thenReturn(mock())

        assertThrows<InvalidInputDataException> {
            flowRPCOps.startFlow(VALID_SHORT_HASH, StartFlowParameters("requetsId", "invalid", TestJsonObject()))
        }

        verify(virtualNodeInfoReadService, times(1)).getByHoldingIdentityShortHash(any())
        verify(flowStatusCacheService, times(1)).getStatus(any(), any())
        verify(cpiInfoReadService, atLeastOnce()).get(any())
        verify(messageFactory, never()).createStartFlowEvent(any(), any(), any(), any(), any())
        verify(messageFactory, never()).createStartFlowStatus(any(), any(), any())
        verify(publisher, never()).publish(any())
        verify(messageFactory, never()).createStartFlowStatus(any(), any(), any())
        verify(fatalErrorFunction, never()).invoke()
    }

    @Test
    fun `start flow throws FlowRPCOpsServiceException exception when publish fails synchronously`() {
        val flowRPCOps = createFlowRpcOps()

        doThrow(CordaMessageAPIIntermittentException("")).whenever(publisher).publish(any())
        assertThrows<FlowRPCOpsServiceException> {
            flowRPCOps.startFlow(VALID_SHORT_HASH, StartFlowParameters(clientRequestId, FLOW1, TestJsonObject()))
        }

        verify(virtualNodeInfoReadService, times(1)).getByHoldingIdentityShortHash(any())
        verify(flowStatusCacheService, times(1)).getStatus(any(), any())
        verify(messageFactory, times(1)).createStartFlowEvent(any(), any(), any(), any(), any())
        verify(messageFactory, times(1)).createStartFlowStatus(any(), any(), any())
        verify(publisher, times(1)).publish(any())
        verify(messageFactory, times(1)).createStartFlowStatus(any(), any(), any())
        verify(fatalErrorFunction, never()).invoke()
    }

    @Test
    fun `start flow throws FlowRPCOpsServiceException exception when publish fails asynchronously`() {
        val flowRPCOps = createFlowRpcOps()
        whenever(publisher.publish(any())).thenReturn(listOf(CompletableFuture<Unit>().apply {
            completeExceptionally(
                CordaMessageAPIIntermittentException("")
            )
        }))

        assertThrows<FlowRPCOpsServiceException> {
            flowRPCOps.startFlow(VALID_SHORT_HASH, StartFlowParameters(clientRequestId, FLOW1, TestJsonObject()))
        }

        verify(virtualNodeInfoReadService, times(1)).getByHoldingIdentityShortHash(any())
        verify(flowStatusCacheService, times(1)).getStatus(any(), any())
        verify(messageFactory, times(1)).createStartFlowEvent(any(), any(), any(), any(), any())
        verify(messageFactory, times(1)).createStartFlowStatus(any(), any(), any())
        verify(publisher, times(1)).publish(any())
        verify(messageFactory, times(1)).createStartFlowStatus(any(), any(), any())
        verify(fatalErrorFunction, never()).invoke()
    }

    @Test
    fun `start flow always returns error after synchronous fatal failure`() {
        val flowRPCOps = createFlowRpcOps()

        doThrow(CordaMessageAPIFatalException("")).whenever(publisher).publish(any())
        assertThrows<FlowRPCOpsServiceException> {
            flowRPCOps.startFlow(VALID_SHORT_HASH, StartFlowParameters(clientRequestId, FLOW1, TestJsonObject()))
        }

        verify(virtualNodeInfoReadService, times(1)).getByHoldingIdentityShortHash(any())
        verify(flowStatusCacheService, times(1)).getStatus(any(), any())
        verify(messageFactory, times(1)).createStartFlowEvent(any(), any(), any(), any(), any())
        verify(messageFactory, times(1)).createStartFlowStatus(any(), any(), any())
        verify(publisher, times(1)).publish(any())
        verify(messageFactory, times(1)).createStartFlowStatus(any(), any(), any())
        verify(fatalErrorFunction, times(1)).invoke()

        // flowRPCOps should have marked itself as unable to start flows after fata error, which means throwing without
        // attempting to start the flow

        assertThrows<FlowRPCOpsServiceException> {
            flowRPCOps.startFlow(VALID_SHORT_HASH, StartFlowParameters(clientRequestId, FLOW1, TestJsonObject()))
        }

        verify(virtualNodeInfoReadService, times(1)).getByHoldingIdentityShortHash(any())
        verify(flowStatusCacheService, times(1)).getStatus(any(), any())
        verify(messageFactory, times(1)).createStartFlowEvent(any(), any(), any(), any(), any())
        verify(messageFactory, times(1)).createStartFlowStatus(any(), any(), any())
        verify(publisher, times(1)).publish(any())
        verify(messageFactory, times(1)).createStartFlowStatus(any(), any(), any())
        verify(fatalErrorFunction, times(1)).invoke()
    }

    @Test
    fun `start flow always returns error after asynchronous fatal failure`() {
        val flowRPCOps = createFlowRpcOps()
        whenever(publisher.publish(any())).thenReturn(listOf(CompletableFuture<Unit>().apply {
            completeExceptionally(
                CordaMessageAPIFatalException("")
            )
        }))

        assertThrows<FlowRPCOpsServiceException> {
            flowRPCOps.startFlow(VALID_SHORT_HASH, StartFlowParameters(clientRequestId, FLOW1, TestJsonObject()))
        }

        verify(virtualNodeInfoReadService, times(1)).getByHoldingIdentityShortHash(any())
        verify(flowStatusCacheService, times(1)).getStatus(any(), any())
        verify(messageFactory, times(1)).createStartFlowEvent(any(), any(), any(), any(), any())
        verify(messageFactory, times(1)).createStartFlowStatus(any(), any(), any())
        verify(publisher, times(1)).publish(any())
        verify(messageFactory, times(1)).createStartFlowStatus(any(), any(), any())
        verify(fatalErrorFunction, times(1)).invoke()

        // flowRPCOps should have marked itself as unable to start flows after fata error, which means throwing without
        // attempting to start the flow

        assertThrows<FlowRPCOpsServiceException> {
            flowRPCOps.startFlow(VALID_SHORT_HASH, StartFlowParameters(clientRequestId, FLOW1, TestJsonObject()))
        }

        verify(virtualNodeInfoReadService, times(1)).getByHoldingIdentityShortHash(any())
        verify(flowStatusCacheService, times(1)).getStatus(any(), any())
        verify(messageFactory, times(1)).createStartFlowEvent(any(), any(), any(), any(), any())
        verify(messageFactory, times(1)).createStartFlowStatus(any(), any(), any())
        verify(publisher, times(1)).publish(any())
        verify(messageFactory, times(1)).createStartFlowStatus(any(), any(), any())
        verify(fatalErrorFunction, times(1)).invoke()
    }

    @Test
    fun `registerFlowStatusUpdatesFeed sends resource not found if virtual node does not exist`() {
        val duplexChannel = mock<DuplexChannel>()
        val exceptionArgumentCaptor = argumentCaptor<Exception>()

        whenever(virtualNodeInfoReadService.getByHoldingIdentityShortHash(any())).thenReturn(null)
        doNothing().whenever(duplexChannel).error(exceptionArgumentCaptor.capture())

        val flowRPCOps = createFlowRpcOps()

        flowRPCOps.registerFlowStatusUpdatesFeed(duplexChannel, VALID_SHORT_HASH, clientRequestId)

        verify(virtualNodeInfoReadService, times(1)).getByHoldingIdentityShortHash(any())
        verify(duplexChannel, times(1)).error(any())
        assertInstanceOf(ResourceNotFoundException::class.java, exceptionArgumentCaptor.firstValue.cause)
        verify(fatalErrorFunction, never()).invoke()
    }

    @Test
    fun `registerFlowStatusUpdatesFeed sends bad request if short hash is invalid`() {
        val duplexChannel = mock<DuplexChannel>()
        val exceptionArgumentCaptor = argumentCaptor<Exception>()

        doNothing().whenever(duplexChannel).error(exceptionArgumentCaptor.capture())

        val flowRPCOps = createFlowRpcOps()

        flowRPCOps.registerFlowStatusUpdatesFeed(duplexChannel, "invalid", clientRequestId)

        verify(virtualNodeInfoReadService, never()).getByHoldingIdentityShortHash(any())
        verify(duplexChannel, times(1)).error(any())
        assertInstanceOf(BadRequestException::class.java, exceptionArgumentCaptor.firstValue.cause)
        verify(fatalErrorFunction, never()).invoke()
    }

    @Test
    fun `start flow throws ForbiddenException exception when no permission granted`() {
        val flowRPCOps = createFlowRpcOps()

        whenever(
            permissionValidator.authorizeUser(
                loginName,
                "$START_FLOW_PREFIX$PREFIX_SEPARATOR$FLOW1"
            )
        ).thenReturn(false)

        assertThrows<ForbiddenException> {
            flowRPCOps.startFlow(VALID_SHORT_HASH, StartFlowParameters(clientRequestId, FLOW1, TestJsonObject()))
        }
        verify(virtualNodeInfoReadService, times(1)).getByHoldingIdentityShortHash(any())
        verify(fatalErrorFunction, never()).invoke()
    }

    @Test
    fun `start flow throws bad request if clientRequestId is empty`() {
        val flowRPCOps = createFlowRpcOps()

        whenever(messageFactory.createFlowStatusResponse(any())).thenReturn(mock())

        assertThrows<BadRequestException> {
            flowRPCOps.startFlow(VALID_SHORT_HASH, StartFlowParameters("", FLOW1, TestJsonObject()))
        }
    }
}
