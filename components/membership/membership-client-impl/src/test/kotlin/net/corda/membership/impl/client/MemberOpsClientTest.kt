package net.corda.membership.impl.client

import net.corda.configuration.read.ConfigChangedEvent
import net.corda.configuration.read.ConfigurationReadService
import net.corda.data.KeyValuePair
import net.corda.data.KeyValuePairList
import net.corda.data.membership.rpc.request.MembershipRpcRequest
import net.corda.data.membership.rpc.request.RegistrationRpcRequest
import net.corda.data.membership.rpc.request.RegistrationStatusRpcRequest
import net.corda.data.membership.rpc.request.RegistrationStatusSpecificRpcRequest
import net.corda.data.membership.rpc.response.MembershipRpcResponse
import net.corda.data.membership.rpc.response.MembershipRpcResponseContext
import net.corda.data.membership.rpc.response.RegistrationRpcResponse
import net.corda.data.membership.rpc.response.RegistrationRpcStatus
import net.corda.data.membership.rpc.response.RegistrationStatus
import net.corda.data.membership.rpc.response.RegistrationStatusDetails
import net.corda.data.membership.rpc.response.RegistrationStatusResponse
import net.corda.data.membership.rpc.response.RegistrationsStatusResponse
import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleEventHandler
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.RegistrationHandle
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import net.corda.membership.client.dto.MemberInfoSubmittedDto
import net.corda.membership.client.dto.MemberRegistrationRequestDto
import net.corda.membership.client.dto.RegistrationActionDto
import net.corda.membership.client.dto.RegistrationRequestStatusDto
import net.corda.membership.client.dto.RegistrationStatusDto
import net.corda.membership.lib.toMap
import net.corda.messaging.api.exception.CordaRPCAPISenderException
import net.corda.messaging.api.publisher.RPCSender
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.subscription.config.RPCConfig
import net.corda.schema.configuration.ConfigKeys
import net.corda.test.util.time.TestClock
import net.corda.v5.base.exceptions.CordaRuntimeException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Instant
import java.util.concurrent.CompletableFuture
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MemberOpsClientTest {
    companion object {
        private const val HOLDING_IDENTITY_ID = "nodeId"
        private val clock = TestClock(Instant.ofEpochSecond(100))
    }

    private val componentHandle: RegistrationHandle = mock()
    private val configHandle: AutoCloseable = mock()

    private var coordinatorIsRunning = false
    private val coordinator: LifecycleCoordinator = mock {
        on { isRunning } doAnswer { coordinatorIsRunning }
        on { start() } doAnswer { coordinatorIsRunning = true }
        on { stop() } doAnswer { coordinatorIsRunning = false }
        on { followStatusChangesByName(any()) } doReturn componentHandle
    }

    private var lifecycleHandler: LifecycleEventHandler? = null

    private val lifecycleCoordinatorFactory: LifecycleCoordinatorFactory = mock {
        on { createCoordinator(any(), any()) } doAnswer {
            lifecycleHandler = it.arguments[1] as LifecycleEventHandler
            coordinator
        }
    }

    private val rpcSender = mock<RPCSender<MembershipRpcRequest, MembershipRpcResponse>>()

    private val publisherFactory = mock<PublisherFactory> {
        on {
            createRPCSender(
                any<RPCConfig<MembershipRpcRequest, MembershipRpcResponse>>(),
                any()
            )
        } doReturn rpcSender
    }

    private val configurationReadService: ConfigurationReadService = mock {
        on { registerComponentForUpdates(any(), any()) } doReturn configHandle
    }

    private val memberOpsClient = MemberOpsClientImpl(
        lifecycleCoordinatorFactory,
        publisherFactory,
        configurationReadService
    )

    private val messagingConfig: SmartConfig = mock()
    private val bootConfig: SmartConfig = mock ()

    private val configs = mapOf(
        ConfigKeys.BOOT_CONFIG to bootConfig,
        ConfigKeys.MESSAGING_CONFIG to messagingConfig
    )

    private fun startComponent() = lifecycleHandler?.processEvent(StartEvent(), coordinator)
    private fun stopComponent() = lifecycleHandler?.processEvent(StopEvent(), coordinator)
    private fun changeRegistrationStatus(status: LifecycleStatus) = lifecycleHandler?.processEvent(
        RegistrationStatusChangeEvent(mock(), status), coordinator
    )

    private fun changeConfig() = lifecycleHandler?.processEvent(
        ConfigChangedEvent(setOf(ConfigKeys.BOOT_CONFIG, ConfigKeys.MESSAGING_CONFIG), configs),
        coordinator
    )

    private fun setUpRpcSender() {
        // kicks off the MessagingConfigurationReceived event to be able to mock the rpc sender
        changeConfig()
    }

    private val request = MemberRegistrationRequestDto(
        HOLDING_IDENTITY_ID,
        RegistrationActionDto.REQUEST_JOIN,
        mapOf("property" to "test"),
    )


    @Test
    fun `starting and stopping the service succeeds`() {
        memberOpsClient.start()
        assertTrue(memberOpsClient.isRunning)
        memberOpsClient.stop()
        assertFalse(memberOpsClient.isRunning)
    }

    @Test
    fun `rpc sender sends the expected request - starting registration process`() {
        val rpcRequest = argumentCaptor<MembershipRpcRequest>()
        val response = RegistrationRpcResponse(
            "Registration-ID",
            clock.instant(),
            RegistrationRpcStatus.SUBMITTED,
            1,
            KeyValuePairList(listOf(KeyValuePair("key", "value"))),
            KeyValuePairList(emptyList())
        )
        whenever(rpcSender.sendRequest(rpcRequest.capture())).then {
            val requestContext = it.getArgument<MembershipRpcRequest>(0).requestContext
            CompletableFuture.completedFuture(
                MembershipRpcResponse(
                    MembershipRpcResponseContext(
                        requestContext.requestId,
                        requestContext.requestTimestamp,
                        clock.instant()
                    ),
                    response,
                )
            )
        }
        memberOpsClient.start()
        setUpRpcSender()
        memberOpsClient.startRegistration(request)
        memberOpsClient.stop()

        val requestSent = rpcRequest.firstValue.request as RegistrationRpcRequest

        assertThat(requestSent.holdingIdentityId).isEqualTo(request.holdingIdentityShortHash)
        assertThat(requestSent.registrationAction.name).isEqualTo(request.action.name)
        assertThat(requestSent.context.toMap()).isEqualTo(request.context)
    }

    @Test
    fun `rpc sender sends the expected request - checking registration progress`() {
        val rpcRequest = argumentCaptor<MembershipRpcRequest>()
        val response = RegistrationsStatusResponse(
            emptyList()
        )
        whenever(rpcSender.sendRequest(rpcRequest.capture())).then {
            val requestContext = it.getArgument<MembershipRpcRequest>(0).requestContext
            CompletableFuture.completedFuture(
                MembershipRpcResponse(
                    MembershipRpcResponseContext(
                        requestContext.requestId,
                        requestContext.requestTimestamp,
                        clock.instant()
                    ),
                    response,
                )
            )
        }
        memberOpsClient.start()
        setUpRpcSender()
        memberOpsClient.checkRegistrationProgress(request.holdingIdentityShortHash)
        memberOpsClient.stop()

        val requestSent = rpcRequest.firstValue.request as RegistrationStatusRpcRequest

        assertEquals(request.holdingIdentityShortHash, requestSent.holdingIdentityId)
    }

    @Test
    fun `should fail when rpc sender is not ready`() {
        memberOpsClient.start()
        val ex = assertFailsWith<IllegalStateException> {
            memberOpsClient.checkRegistrationProgress(request.holdingIdentityShortHash)
        }
        assertTrue { ex.message!!.contains("incorrect state") }
        memberOpsClient.stop()
    }

    @Test
    fun `checkRegistrationProgress should fail when service is not running`() {
        val ex = assertFailsWith<IllegalStateException> {
            memberOpsClient.checkRegistrationProgress(request.holdingIdentityShortHash)
        }
        assertThat(ex).hasMessageContaining("incorrect state")
    }

    @Test
    fun `checkSpecificRegistrationProgress should fail when service is not running`() {
        val ex = assertFailsWith<IllegalStateException> {
            memberOpsClient.checkSpecificRegistrationProgress(request.holdingIdentityShortHash, "")
        }
        assertThat(ex).hasMessageContaining("incorrect state")
    }

    @Test
    fun `should fail when there is an RPC sender exception while sending the request`() {
        memberOpsClient.start()
        setUpRpcSender()
        val message = "Sender exception."
        whenever(rpcSender.sendRequest(any())).thenThrow(CordaRPCAPISenderException(message))
        val ex = assertFailsWith<CordaRuntimeException> {
            memberOpsClient.checkRegistrationProgress(request.holdingIdentityShortHash)
        }
        assertTrue { ex.message!!.contains(message) }
        memberOpsClient.stop()
    }

    @Test
    fun `should fail when response is null`() {
        memberOpsClient.start()
        setUpRpcSender()

        whenever(rpcSender.sendRequest(any())).then {
            val rpcRequest = it.arguments.first() as MembershipRpcRequest
            CompletableFuture.completedFuture(
                MembershipRpcResponse(
                    MembershipRpcResponseContext(
                        rpcRequest.requestContext.requestId,
                        rpcRequest.requestContext.requestTimestamp,
                        clock.instant()
                    ),
                    null
                )
            )
        }

        val ex = assertFailsWith<CordaRuntimeException> {
            memberOpsClient.checkRegistrationProgress(request.holdingIdentityShortHash)
        }
        assertTrue { ex.message!!.contains("null") }
        memberOpsClient.stop()
    }

    @Test
    fun `should fail when request and response has different ids`() {
        memberOpsClient.start()
        setUpRpcSender()

        whenever(rpcSender.sendRequest(any())).then {
            val rpcRequest = it.arguments.first() as MembershipRpcRequest

            CompletableFuture.completedFuture(
                MembershipRpcResponse(
                    MembershipRpcResponseContext(
                        "wrongId",
                        rpcRequest.requestContext.requestTimestamp,
                        clock.instant()
                    ),
                    RegistrationRpcResponse(
                        "RegistrationID",
                        clock.instant(),
                        RegistrationRpcStatus.SUBMITTED,
                        1,
                        KeyValuePairList(listOf(KeyValuePair("key", "value"))),
                        KeyValuePairList(emptyList())
                    )
                )
            )
        }

        val ex = assertFailsWith<CordaRuntimeException> {
            memberOpsClient.checkRegistrationProgress(request.holdingIdentityShortHash)
        }
        assertTrue { ex.message!!.contains("ID") }
        memberOpsClient.stop()
    }

    @Test
    fun `should fail when request and response has different requestTimestamp`() {
        memberOpsClient.start()
        setUpRpcSender()

        whenever(rpcSender.sendRequest(any())).then {
            val rpcRequest = it.arguments.first() as MembershipRpcRequest
            CompletableFuture.completedFuture(
                MembershipRpcResponse(
                    MembershipRpcResponseContext(
                        rpcRequest.requestContext.requestId,
                        clock.instant().plusMillis(10000000),
                        clock.instant()
                    ),
                    RegistrationRpcResponse(
                        "RegistrationID",
                        clock.instant(),
                        RegistrationRpcStatus.SUBMITTED,
                        1,
                        KeyValuePairList(listOf(KeyValuePair("key", "value"))),
                        KeyValuePairList(emptyList())
                    )
                )
            )
        }

        val ex = assertFailsWith<CordaRuntimeException> {
            memberOpsClient.checkRegistrationProgress(request.holdingIdentityShortHash)
        }
        assertTrue { ex.message!!.contains("timestamp") }
        memberOpsClient.stop()
    }

    @Test
    fun `should fail when response type is not the expected type`() {
        memberOpsClient.start()
        setUpRpcSender()

        whenever(rpcSender.sendRequest(any())).then {
            val rpcRequest = it.arguments.first() as MembershipRpcRequest
            CompletableFuture.completedFuture(
                MembershipRpcResponse(
                    MembershipRpcResponseContext(
                        rpcRequest.requestContext.requestId,
                        rpcRequest.requestContext.requestTimestamp,
                        clock.instant()
                    ),
                    "WRONG RESPONSE TYPE"
                )
            )
        }

        val ex = assertFailsWith<CordaRuntimeException> {
            memberOpsClient.checkRegistrationProgress(request.holdingIdentityShortHash)
        }
        assertTrue { ex.message!!.contains("Expected class") }
        memberOpsClient.stop()
    }

    @Test
    fun `start event starts following the statuses of the required dependencies`() {
        startComponent()

        verify(coordinator).followStatusChangesByName(
            eq(setOf(LifecycleCoordinatorName.forComponent<ConfigurationReadService>()))
        )
    }

    @Test
    fun `start event closes dependency handle if it exists`() {
        startComponent()
        startComponent()

        verify(componentHandle).close()
    }

    @Test
    fun `stop event doesn't closes handles before they are created`() {
        stopComponent()

        verify(componentHandle, never()).close()
        verify(configHandle, never()).close()
    }

    @Test
    fun `component handle is created after starting and closed when stopping`() {
        startComponent()
        stopComponent()

        verify(componentHandle).close()
    }

    @Test
    fun `config handle is created after registration status changes to UP and closed when stopping`() {
        changeRegistrationStatus(LifecycleStatus.UP)
        stopComponent()

        verify(configHandle).close()
    }

    @Test
    fun `registration status UP registers for config updates`() {
        changeRegistrationStatus(LifecycleStatus.UP)

        verify(configurationReadService).registerComponentForUpdates(
            any(), any()
        )
        verify(coordinator, never()).updateStatus(eq(LifecycleStatus.UP), any())
    }

    @Test
    fun `registration status DOWN sets component status to DOWN`() {
        startComponent()
        changeRegistrationStatus(LifecycleStatus.UP)
        changeRegistrationStatus(LifecycleStatus.DOWN)

        verify(configHandle).close()
        verify(coordinator).updateStatus(eq(LifecycleStatus.DOWN), any())
    }

    @Test
    fun `registration status ERROR sets component status to DOWN`() {
        startComponent()
        changeRegistrationStatus(LifecycleStatus.UP)
        changeRegistrationStatus(LifecycleStatus.ERROR)

        verify(configHandle).close()
        verify(coordinator).updateStatus(eq(LifecycleStatus.DOWN), any())
    }

    @Test
    fun `registration status DOWN closes config handle if status was previously UP`() {
        startComponent()
        changeRegistrationStatus(LifecycleStatus.UP)

        verify(configurationReadService).registerComponentForUpdates(
            any(), any()
        )

        changeRegistrationStatus(LifecycleStatus.DOWN)

        verify(configHandle).close()
    }

    @Test
    fun `registration status UP closes config handle if status was previously UP`() {
        changeRegistrationStatus(LifecycleStatus.UP)

        verify(configurationReadService).registerComponentForUpdates(
            any(), any()
        )

        changeRegistrationStatus(LifecycleStatus.UP)

        verify(configHandle).close()
    }

    @Test
    fun `after receiving the messaging configuration the rpc sender is initialized`() {
        changeConfig()
        verify(publisherFactory).createRPCSender(any<RPCConfig<MembershipRpcRequest, MembershipRpcResponse>>(), any())
        verify(rpcSender).start()
        verify(coordinator).updateStatus(eq(LifecycleStatus.UP), any())
    }

    @Test
    fun `checkRegistrationProgress return correct data`() {
        val response = RegistrationsStatusResponse(
            listOf(
                RegistrationStatusDetails(
                    clock.instant().plusSeconds(3),
                    clock.instant().plusSeconds(7),
                    RegistrationStatus.APPROVED,
                    "registration id",
                    1,
                    KeyValuePairList(listOf(KeyValuePair("key", "value"))),
                ),
                RegistrationStatusDetails(
                    clock.instant().plusSeconds(10),
                    clock.instant().plusSeconds(20),
                    RegistrationStatus.NEW,
                    "registration id 2",
                    1,
                    KeyValuePairList(listOf(KeyValuePair("key 2", "value 2"))),
                ),
                RegistrationStatusDetails(
                    clock.instant().plusSeconds(30),
                    clock.instant().plusSeconds(70),
                    RegistrationStatus.DECLINED,
                    "registration id 3",
                    1,
                    KeyValuePairList(listOf(KeyValuePair("key 3", "value 3"))),
                ),
            )
        )
        whenever(rpcSender.sendRequest(any())).then {
            val requestContext = it.getArgument<MembershipRpcRequest>(0).requestContext
            CompletableFuture.completedFuture(
                MembershipRpcResponse(
                    MembershipRpcResponseContext(
                        requestContext.requestId,
                        requestContext.requestTimestamp,
                        clock.instant()
                    ),
                    response,
                )
            )
        }
        memberOpsClient.start()
        setUpRpcSender()

        val statuses = memberOpsClient.checkRegistrationProgress(request.holdingIdentityShortHash)

        assertThat(statuses).hasSize(3)
            .contains(
                RegistrationRequestStatusDto(
                    registrationId = "registration id",
                    registrationSent = clock.instant().plusSeconds(3),
                    registrationUpdated = clock.instant().plusSeconds(7),
                    registrationStatus = RegistrationStatusDto.APPROVED,
                    memberInfoSubmitted = MemberInfoSubmittedDto(
                        mapOf(
                            "registrationProtocolVersion" to "1",
                            "key" to "value"
                        )
                    )
                ),
                RegistrationRequestStatusDto(
                    registrationId = "registration id 2",
                    registrationSent = clock.instant().plusSeconds(10),
                    registrationUpdated = clock.instant().plusSeconds(20),
                    registrationStatus = RegistrationStatusDto.NEW,
                    memberInfoSubmitted = MemberInfoSubmittedDto(
                        mapOf(
                            "registrationProtocolVersion" to "1",
                            "key 2" to "value 2"
                        )
                    )
                ),
                RegistrationRequestStatusDto(
                    registrationId = "registration id 3",
                    registrationSent = clock.instant().plusSeconds(30),
                    registrationUpdated = clock.instant().plusSeconds(70),
                    registrationStatus = RegistrationStatusDto.DECLINED,
                    memberInfoSubmitted = MemberInfoSubmittedDto(
                        mapOf(
                            "registrationProtocolVersion" to "1",
                            "key 3" to "value 3"
                        )
                    )
                ),
            )
    }

    @ParameterizedTest
    @EnumSource(RegistrationStatus::class)
    fun `checkSpecificRegistrationProgress return correct data when response is not null`(status: RegistrationStatus) {
        val response = RegistrationStatusResponse(
            RegistrationStatusDetails(
                clock.instant().plusSeconds(1),
                clock.instant().plusSeconds(2),
                status,
                "registration id",
                1,
                KeyValuePairList(listOf(KeyValuePair("key", "value"))),
            ),
        )
        whenever(rpcSender.sendRequest(any())).then {
            val requestContext = it.getArgument<MembershipRpcRequest>(0).requestContext
            CompletableFuture.completedFuture(
                MembershipRpcResponse(
                    MembershipRpcResponseContext(
                        requestContext.requestId,
                        requestContext.requestTimestamp,
                        clock.instant()
                    ),
                    response,
                )
            )
        }
        memberOpsClient.start()
        setUpRpcSender()

        val result = memberOpsClient.checkSpecificRegistrationProgress(request.holdingIdentityShortHash, "registration id")

        assertThat(result).isNotNull
            .isEqualTo(
                RegistrationRequestStatusDto(
                    registrationId = "registration id",
                    registrationSent = clock.instant().plusSeconds(1),
                    registrationUpdated = clock.instant().plusSeconds(2),
                    registrationStatus = RegistrationStatusDto.valueOf(status.name),
                    memberInfoSubmitted = MemberInfoSubmittedDto(
                        mapOf(
                            "registrationProtocolVersion" to "1",
                            "key" to "value"
                        )
                    )
                )
            )
    }

    @Test
    fun `checkSpecificRegistrationProgress return correct data when response is null`() {
        whenever(rpcSender.sendRequest(any())).then {
            val requestContext = it.getArgument<MembershipRpcRequest>(0).requestContext
            CompletableFuture.completedFuture(
                MembershipRpcResponse(
                    MembershipRpcResponseContext(
                        requestContext.requestId,
                        requestContext.requestTimestamp,
                        clock.instant()
                    ),
                    RegistrationStatusResponse(null),
                )
            )
        }
        memberOpsClient.start()
        setUpRpcSender()

        val status = memberOpsClient.checkSpecificRegistrationProgress(request.holdingIdentityShortHash, "registration id")

        assertThat(status).isNull()
    }


    @Test
    fun `checkSpecificRegistrationProgress sends the correct data`() {
        val requestCapture = argumentCaptor<MembershipRpcRequest>()
        whenever(rpcSender.sendRequest(requestCapture.capture())).then {
            val requestContext = it.getArgument<MembershipRpcRequest>(0).requestContext
            CompletableFuture.completedFuture(
                MembershipRpcResponse(
                    MembershipRpcResponseContext(
                        requestContext.requestId,
                        requestContext.requestTimestamp,
                        clock.instant()
                    ),
                    RegistrationStatusResponse(null),
                )
            )
        }
        memberOpsClient.start()
        setUpRpcSender()

        memberOpsClient.checkSpecificRegistrationProgress(request.holdingIdentityShortHash, "registration id")

        assertThat(requestCapture.firstValue.request as? RegistrationStatusSpecificRpcRequest)
            .isNotNull
            .isEqualTo(
                RegistrationStatusSpecificRpcRequest(
                    request.holdingIdentityShortHash, "registration id"
                )
            )
    }

}
