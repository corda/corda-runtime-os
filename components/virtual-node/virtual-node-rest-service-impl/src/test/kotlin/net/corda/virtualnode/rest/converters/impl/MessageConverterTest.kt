package net.corda.virtualnode.rest.converters.impl

import net.corda.crypto.core.SecureHashImpl
import net.corda.libs.external.messaging.entities.RouteConfiguration
import net.corda.libs.external.messaging.entities.Routes
import net.corda.libs.external.messaging.serialization.ExternalMessagingRouteConfigSerializer
import net.corda.libs.virtualnode.endpoints.v1.types.external.messaging.Routes as RoutesRestResponse
import net.corda.libs.virtualnode.endpoints.v1.types.external.messaging.RouteConfiguration as RouteConfigurationRestResponse
import net.corda.rest.asynchronous.v1.AsyncOperationState
import net.corda.v5.base.types.MemberX500Name
import net.corda.virtualnode.OperationalStatus
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.SoftAssertions.assertSoftly
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Instant
import java.util.UUID
import net.corda.data.virtualnode.VirtualNodeOperationStatus as AvroVirtualNodeOperationStatus
import net.corda.libs.cpiupload.endpoints.v1.CpiIdentifier as CpiIdentifierRestResponse
import net.corda.libs.packaging.core.CpiIdentifier as CpiIdentifierDto
import net.corda.libs.virtualnode.endpoints.v1.types.HoldingIdentity as HoldingIdentityRestResponse
import net.corda.virtualnode.HoldingIdentity as HoldingIdentityDto
import net.corda.virtualnode.VirtualNodeInfo as VirtualNodeInfoDto

class MessageConverterTest {
    private val now1 = Instant.ofEpochMilli(1)
    private val now2 = Instant.ofEpochMilli(2)
    private val operationName = "op1"
    private val aliceX500 = "CN=Alice, O=Alice Corp, L=LDN, C=GB"
    private val aliceX500Name = MemberX500Name.parse(aliceX500)
    private val groupId = "group1"
    private val cpiName = "CPI1"
    private val cpiVersion = "1.0"
    private val exampleSecureHash = SecureHashImpl("ALGO", "abc".toByteArray())
    private val vaultDdlConnectionId = UUID.randomUUID()
    private val vaultDmlConnectionId = UUID.randomUUID()
    private val cryptoDdlConnectionId = UUID.randomUUID()
    private val cryptoDmlConnectionId = UUID.randomUUID()
    private val uniquenessDdlConnectionId = UUID.randomUUID()
    private val uniquenessDmlConnectionId = UUID.randomUUID()
    private val hsmConnectionId = UUID.randomUUID()
    private val flowP2pOperationalStatus = OperationalStatus.ACTIVE
    private val flowStartOperationalStatus = OperationalStatus.INACTIVE
    private val flowOperationalStatus = OperationalStatus.ACTIVE
    private val vaultDbOperationalStatus = OperationalStatus.INACTIVE
    private val operationInProgress = "op in progress"
    private val externalMessagingRouteConfig = "external messaging route config json"
    private val version = 1
    private val timestamp = now1
    private val isDeleted = false

    private val holdingIdentityDto = HoldingIdentityDto(aliceX500Name, groupId)
    private val cpiIdentifierDto = CpiIdentifierDto(cpiName, cpiVersion, exampleSecureHash)
    private val longHash = holdingIdentityDto.fullHash
    private val shortHash = holdingIdentityDto.shortHash.toString()

    private val routeConfiguration = RouteConfiguration(
        Routes(cpiIdentifierDto, listOf()),
        listOf()
    )

    private val routeConfigSerializer = mock<ExternalMessagingRouteConfigSerializer>().apply {
        whenever(deserialize(anyOrNull())).thenReturn(routeConfiguration)
    }

    @Test
    fun `Convert AvroVirtualNodeOperationStatus with ACCEPTED status`() {
        val avroState = getAvroVirtualNodeOperationStatus("ACCEPTED")

        val result = MessageConverterImpl(mock()).convert(avroState, operationName)

        assertThat(result.requestId).isEqualTo("request1")
        assertThat(result.operation).isEqualTo(operationName)
        assertThat(result.status).isEqualTo(AsyncOperationState.ACCEPTED)
        assertThat(result.lastUpdatedTimestamp).isEqualTo(now2)
        assertThat(result.processingStage).isEqualTo(null)
        assertThat(result.errorReason).isEqualTo(null)
        assertThat(result.resourceId).isEqualTo(null)
    }

    @Test
    fun `Convert AvroVirtualNodeOperationStatus with IN_PROGRESS status`() {
        val avroState = getAvroVirtualNodeOperationStatus("IN_PROGRESS")

        val result = MessageConverterImpl(mock()).convert(avroState, operationName)

        assertThat(result.requestId).isEqualTo("request1")
        assertThat(result.operation).isEqualTo(operationName)
        assertThat(result.status).isEqualTo(AsyncOperationState.IN_PROGRESS)
        assertThat(result.lastUpdatedTimestamp).isEqualTo(now2)
        assertThat(result.processingStage).isEqualTo(null)
        assertThat(result.errorReason).isEqualTo(null)
        assertThat(result.resourceId).isEqualTo(null)
    }

    @Test
    fun `Convert AvroVirtualNodeOperationStatus with VALIDATION_FAILED status`() {
        val avroState = getAvroVirtualNodeOperationStatus("VALIDATION_FAILED")

        val result = MessageConverterImpl(mock()).convert(avroState, operationName)

        assertThat(result.requestId).isEqualTo("request1")
        assertThat(result.operation).isEqualTo(operationName)
        assertThat(result.status).isEqualTo(AsyncOperationState.FAILED)
        assertThat(result.lastUpdatedTimestamp).isEqualTo(now2)
        assertThat(result.processingStage).isEqualTo("Validation")
        assertThat(result.errorReason).isEqualTo("error1")
        assertThat(result.resourceId).isEqualTo(null)
    }

    @Test
    fun `Convert AvroVirtualNodeOperationStatus with LIQUIBASE_DIFF_CHECK_FAILED status`() {
        val avroState = getAvroVirtualNodeOperationStatus("LIQUIBASE_DIFF_CHECK_FAILED")

        val result = MessageConverterImpl(mock()).convert(avroState, operationName)

        assertThat(result.requestId).isEqualTo("request1")
        assertThat(result.operation).isEqualTo(operationName)
        assertThat(result.status).isEqualTo(AsyncOperationState.FAILED)
        assertThat(result.lastUpdatedTimestamp).isEqualTo(now2)
        assertThat(result.processingStage).isEqualTo("Liquibase diff check")
        assertThat(result.errorReason).isEqualTo("error1")
        assertThat(result.resourceId).isEqualTo(null)
    }

    @Test
    fun `Convert AvroVirtualNodeOperationStatus with MIGRATIONS_FAILED status`() {
        val avroState = getAvroVirtualNodeOperationStatus("MIGRATIONS_FAILED")

        val result = MessageConverterImpl(mock()).convert(avroState, operationName)

        assertThat(result.requestId).isEqualTo("request1")
        assertThat(result.operation).isEqualTo(operationName)
        assertThat(result.status).isEqualTo(AsyncOperationState.FAILED)
        assertThat(result.lastUpdatedTimestamp).isEqualTo(now2)
        assertThat(result.processingStage).isEqualTo("Migration")
        assertThat(result.errorReason).isEqualTo("error1")
        assertThat(result.resourceId).isEqualTo(null)
    }

    @Test
    fun `Convert AvroVirtualNodeOperationStatus with UNEXPECTED_FAILURE status`() {
        val avroState = getAvroVirtualNodeOperationStatus("UNEXPECTED_FAILURE")

        val result = MessageConverterImpl(mock()).convert(avroState, operationName)

        assertThat(result.requestId).isEqualTo("request1")
        assertThat(result.operation).isEqualTo(operationName)
        assertThat(result.status).isEqualTo(AsyncOperationState.FAILED)
        assertThat(result.lastUpdatedTimestamp).isEqualTo(now2)
        assertThat(result.processingStage).isEqualTo(null)
        assertThat(result.errorReason).isEqualTo("error1")
        assertThat(result.resourceId).isEqualTo(null)
    }

    @Test
    fun `Convert AvroVirtualNodeOperationStatus with COMPLETED status`() {
        val avroState = getAvroVirtualNodeOperationStatus("COMPLETED")

        val result = MessageConverterImpl(mock()).convert(avroState, operationName)

        assertThat(result.requestId).isEqualTo("request1")
        assertThat(result.operation).isEqualTo(operationName)
        assertThat(result.status).isEqualTo(AsyncOperationState.SUCCEEDED)
        assertThat(result.lastUpdatedTimestamp).isEqualTo(now2)
        assertThat(result.processingStage).isEqualTo(null)
        assertThat(result.errorReason).isEqualTo(null)
        assertThat(result.resourceId).isEqualTo(null)
    }

    @Test
    fun `Convert AvroVirtualNodeOperationStatus with ABORTED status`() {
        val avroState = getAvroVirtualNodeOperationStatus("ABORTED")

        val result = MessageConverterImpl(mock()).convert(avroState, operationName)

        assertThat(result.requestId).isEqualTo("request1")
        assertThat(result.operation).isEqualTo(operationName)
        assertThat(result.status).isEqualTo(AsyncOperationState.ABORTED)
        assertThat(result.lastUpdatedTimestamp).isEqualTo(now2)
        assertThat(result.processingStage).isEqualTo(null)
        assertThat(result.errorReason).isEqualTo(null)
        assertThat(result.resourceId).isEqualTo(null)
    }

    @Test
    fun `Convert VirtualNodeInfo DTO to rest response with external messaging config`() {
        val target = MessageConverterImpl(routeConfigSerializer)

        val virtualNodeInfoDto = getExampleVirtualNodeInfoDto(
            holdingIdentityDto,
            cpiIdentifierDto,
            externalMessagingRouteConfig
        )

        val result = target.convert(virtualNodeInfoDto)

        val expectedHoldingIdentityRestResponse = HoldingIdentityRestResponse(aliceX500, groupId, shortHash, longHash)
        val expectedCpiRestResponse = CpiIdentifierRestResponse(cpiName, cpiVersion, exampleSecureHash.toString())
        val expectedRouteConfigurationRestResponse = RouteConfigurationRestResponse(
            RoutesRestResponse(
                CpiIdentifierRestResponse(
                    cpiIdentifierDto.name,
                    cpiIdentifierDto.version,
                    cpiIdentifierDto.signerSummaryHash.toString()
                ),
                listOf()
            ),
            listOf()
        )

        assertSoftly {
            assertThat(result.holdingIdentity).isEqualTo(expectedHoldingIdentityRestResponse)
            assertThat(result.cpiIdentifier).isEqualTo(expectedCpiRestResponse)
            assertThat(result.vaultDdlConnectionId).isEqualTo(vaultDdlConnectionId.toString())

            assertThat(result.vaultDdlConnectionId).isEqualTo(vaultDdlConnectionId.toString())
            assertThat(result.vaultDmlConnectionId).isEqualTo(vaultDmlConnectionId.toString())
            assertThat(result.cryptoDdlConnectionId).isEqualTo(cryptoDdlConnectionId.toString())
            assertThat(result.cryptoDmlConnectionId).isEqualTo(cryptoDmlConnectionId.toString())
            assertThat(result.uniquenessDdlConnectionId).isEqualTo(uniquenessDdlConnectionId.toString())
            assertThat(result.uniquenessDmlConnectionId).isEqualTo(uniquenessDmlConnectionId.toString())
            assertThat(result.hsmConnectionId).isEqualTo(hsmConnectionId.toString())
            assertThat(result.flowP2pOperationalStatus).isEqualTo(flowP2pOperationalStatus)
            assertThat(result.flowStartOperationalStatus).isEqualTo(flowStartOperationalStatus)
            assertThat(result.flowOperationalStatus).isEqualTo(flowOperationalStatus)
            assertThat(result.vaultDbOperationalStatus).isEqualTo(vaultDbOperationalStatus)
            assertThat(result.externalMessagingRouteConfiguration).isEqualTo(expectedRouteConfigurationRestResponse)
        }

        verify(routeConfigSerializer).deserialize(externalMessagingRouteConfig)
    }

    @Test
    fun `Convert VirtualNodeInfo DTO to rest response without external messaging config`() {
        val target = MessageConverterImpl(routeConfigSerializer)

        val virtualNodeInfoDto = getExampleVirtualNodeInfoDto(
            holdingIdentityDto,
            cpiIdentifierDto,
            externalMessagingRouteConfig = null
        )

        val result = target.convert(virtualNodeInfoDto)

        assertThat(result.externalMessagingRouteConfiguration).isNull()

        verify(routeConfigSerializer, never()).deserialize(any())
    }

    private fun getAvroVirtualNodeOperationStatus(stateString: String): AvroVirtualNodeOperationStatus {
        return AvroVirtualNodeOperationStatus.newBuilder()
            .setRequestId("request1")
            .setState(stateString)
            .setRequestData("requestData1")
            .setRequestTimestamp(now1)
            .setLatestUpdateTimestamp(now2)
            .setHeartbeatTimestamp(null)
            .setErrors("error1")
            .build()
    }

    private fun getExampleVirtualNodeInfoDto(
        holdingIdDto: HoldingIdentityDto,
        cpiIdentifierDto: CpiIdentifierDto,
        externalMessagingRouteConfig: String?
    ): VirtualNodeInfoDto {
        return VirtualNodeInfoDto(
            holdingIdDto,
            cpiIdentifierDto,
            vaultDdlConnectionId,
            vaultDmlConnectionId,
            cryptoDdlConnectionId,
            cryptoDmlConnectionId,
            uniquenessDdlConnectionId,
            uniquenessDmlConnectionId,
            hsmConnectionId,
            flowP2pOperationalStatus,
            flowStartOperationalStatus,
            flowOperationalStatus,
            vaultDbOperationalStatus,
            operationInProgress,
            externalMessagingRouteConfig,
            version,
            timestamp,
            isDeleted
        )
    }
}
