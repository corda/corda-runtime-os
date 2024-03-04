package net.corda.configuration.rest.impl.v1

import net.corda.cpiinfo.read.CpiInfoReadService
import net.corda.crypto.core.parseSecureHash
import net.corda.data.ExceptionEnvelope
import net.corda.data.virtualnode.VirtualNodeAsynchronousRequest
import net.corda.data.virtualnode.VirtualNodeManagementResponse
import net.corda.data.virtualnode.VirtualNodeManagementResponseFailure
import net.corda.data.virtualnode.VirtualNodeOperationStatusResponse
import net.corda.libs.packaging.core.CpiIdentifier
import net.corda.libs.packaging.core.CpiMetadata
import net.corda.libs.virtualnode.common.exception.VirtualNodeOperationNotFoundException
import net.corda.libs.virtualnode.endpoints.v1.types.CreateVirtualNodeRequestType.JsonCreateVirtualNodeRequest
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.rest.asynchronous.v1.AsyncOperationStatus
import net.corda.rest.exception.InvalidInputDataException
import net.corda.rest.exception.InvalidStateChangeException
import net.corda.rest.exception.ResourceNotFoundException
import net.corda.rest.security.RestContextProvider
import net.corda.utilities.time.UTCClock
import net.corda.v5.base.types.MemberX500Name
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.OperationalStatus
import net.corda.virtualnode.VirtualNodeInfo
import net.corda.virtualnode.rest.common.VirtualNodeSender
import net.corda.virtualnode.rest.converters.MessageConverter
import net.corda.virtualnode.rest.factories.RequestFactory
import net.corda.virtualnode.rest.impl.v1.VirtualNodeRestResourceImpl
import net.corda.virtualnode.rest.impl.validation.VirtualNodeValidationService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Instant
import java.util.*
import net.corda.data.virtualnode.VirtualNodeOperationStatus as AvroVirtualNodeOperationStatus

class VirtualNodeRestResourceImplTest {

    private val virtualNodeSender = mock<VirtualNodeSender>()
    private val virtualNodeValidationService = mock<VirtualNodeValidationService>()
    private val requestFactory = mock<RequestFactory>()
    private val restContextProvider = mock<RestContextProvider>().apply {
        whenever(principal).thenReturn("user1")
    }
    private val messageConverter = mock<MessageConverter>()

    private val mockCoordinator = mock<LifecycleCoordinator>().apply {
        whenever(isRunning).thenReturn(true)
        whenever(this.getManagedResource<VirtualNodeSender>("SENDER")).thenReturn(virtualNodeSender)
    }
    private val mockCoordinatorFactory = mock<LifecycleCoordinatorFactory>().apply {
        whenever(createCoordinator(any(), any())) doReturn mockCoordinator
    }

    private val cpiInfoReadService = mock<CpiInfoReadService>()

    @Test
    fun `verify coordinator is started on start`() {
        val restResource = createVirtualNodeRestResourceImpl(mockCoordinatorFactory)
        restResource.start()

        verify(mockCoordinator).start()
    }

    @Test
    fun `verify coordinator is stopped on stop`() {
        val restResource = createVirtualNodeRestResourceImpl(mockCoordinatorFactory)
        restResource.stop()

        verify(mockCoordinator).stop()
    }

    @Test
    fun `verify coordinator isRunning defers to the coordinator`() {
        val restResource = createVirtualNodeRestResourceImpl(mockCoordinatorFactory)
        restResource.isRunning

        verify(mockCoordinator).isRunning
    }

    @Test
    fun `create virtual node`() {
        val groupId = "grp1"
        val requestId = "r1"
        val request = JsonCreateVirtualNodeRequest(
            "",
            "checkSum",
            null,
            null,
            null,
            null,
            null,
            null
        )
        val holdingIdentity = mock<HoldingIdentity>()
        val asyncRequest = VirtualNodeAsynchronousRequest().apply { this.requestId = requestId }

        whenever(virtualNodeValidationService.validateAndGetGroupId(request)).then {
            if (request.cpiFileChecksum.uppercase() != request.cpiFileChecksum) {
                throw IllegalArgumentException("CPI checksum must be uppercase at this point")
            }

            groupId
        }

        whenever(requestFactory.createHoldingIdentity(groupId, request)).thenReturn(holdingIdentity)
        whenever(requestFactory.createVirtualNodeRequest(holdingIdentity, request)).thenReturn(asyncRequest)

        val target = createVirtualNodeRestResourceImpl(mockCoordinatorFactory)

        val result = target.createVirtualNode(request)

        verify(virtualNodeValidationService).validateVirtualNodeDoesNotExist(holdingIdentity)
        verify(virtualNodeSender).sendAsync(requestId, asyncRequest)
        assertThat(result.responseBody.requestId).isEqualTo(requestId)
    }

    @Test
    fun `cant set state of virtual node to non defined value`() {
        val vnodeResource = createVirtualNodeRestResourceImpl(mockCoordinatorFactory)
        vnodeResource.start()

        assertThrows<InvalidInputDataException> {
            vnodeResource.updateVirtualNodeState("ABCABC123123", "BLAHBLAHBLAH")
        }
    }

    @Test
    fun `upgradeVirtualNode throws error when trying to upgrade to same CPI as current one`() {
        val currentVNode = mockVnode()
        val currentCpi = mock<CpiMetadata>().apply {
            whenever(fileChecksum).thenReturn(parseSecureHash("SHA-256:1234567890"))
        }
        whenever(virtualNodeValidationService.validateAndGetVirtualNode(any())).thenReturn(currentVNode)
        whenever(cpiInfoReadService.get(currentVNode.cpiIdentifier)).thenReturn(currentCpi)

        val vnodeResource = createVirtualNodeRestResourceImpl(mockCoordinatorFactory)
        vnodeResource.start()

        assertThrows<InvalidStateChangeException> {
            vnodeResource.upgradeVirtualNode(currentVNode.holdingIdentity.toString(), "1234567890")
        }
    }

    @Test
    fun `get upgrade node status for missing request id returns 404`() {
        val requestId = UUID.randomUUID().toString()
        val target = createVirtualNodeRestResourceImpl(mockCoordinatorFactory)
        val errorResponse = VirtualNodeManagementResponseFailure(
            ExceptionEnvelope(VirtualNodeOperationNotFoundException::class.java.name, "b")
        )

        val response = VirtualNodeManagementResponse(Instant.now(), errorResponse)
        whenever(virtualNodeSender.sendAndReceive(any())).thenReturn(response)

        assertThrows<ResourceNotFoundException> { target.getVirtualNodeOperationStatus(requestId) }
    }

    @Test
    fun `get upgrade node status for request id returns status`() {
        val requestId = UUID.randomUUID().toString()
        val avroStatus = getAvroVirtualNodeOperationStatus()
        val statusResponse = VirtualNodeOperationStatusResponse(requestId, listOf(avroStatus))
        val response = VirtualNodeManagementResponse(Instant.now(), statusResponse)

        val status = AsyncOperationStatus.accepted("r1", "op", Instant.ofEpochMilli(1))

        whenever(virtualNodeSender.sendAndReceive(any())).thenReturn(response)
        whenever(messageConverter.convert(any(), any(), anyOrNull())).thenReturn(status)

        val target = createVirtualNodeRestResourceImpl(mockCoordinatorFactory)

        val result = target.getVirtualNodeOperationStatus(requestId)

        assertThat(result).isEqualTo(status)
    }

    private fun createVirtualNodeRestResourceImpl(lifecycleCoordinatorFactory: LifecycleCoordinatorFactory): VirtualNodeRestResourceImpl {
        return VirtualNodeRestResourceImpl(
            lifecycleCoordinatorFactory,
            mock(),
            mock(),
            mock(),
            cpiInfoReadService,
            requestFactory,
            UTCClock(),
            virtualNodeValidationService,
            restContextProvider,
            messageConverter,
            mock()
        )
    }

    private fun mockVnode(operational: OperationalStatus = OperationalStatus.ACTIVE): VirtualNodeInfo {
        return VirtualNodeInfo(
            HoldingIdentity(MemberX500Name("test", "IE", "IE"), "group"),
            CpiIdentifier("cpi", "1", parseSecureHash("SHA-256:1234567890")),
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            operational,
            operational,
            operational,
            operational,
            null,
            null,
            -1,
            mock(),
            false
        )
    }

    private fun getAvroVirtualNodeOperationStatus(): AvroVirtualNodeOperationStatus {
        return AvroVirtualNodeOperationStatus.newBuilder()
            .setRequestId("request1")
            .setState("a")
            .setRequestData("requestData1")
            .setRequestTimestamp(Instant.now())
            .setLatestUpdateTimestamp(Instant.now())
            .setHeartbeatTimestamp(null)
            .setErrors("error1")
            .build()
    }
}
