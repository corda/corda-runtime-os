package net.corda.membership.impl.client

import net.corda.configuration.read.ConfigChangedEvent
import net.corda.configuration.read.ConfigurationReadService
import net.corda.crypto.impl.toWire
import net.corda.data.CordaAvroSerializationFactory
import net.corda.data.CordaAvroSerializer
import net.corda.data.KeyValuePair
import net.corda.data.KeyValuePairList
import net.corda.data.membership.async.request.MembershipAsyncRequest
import net.corda.data.membership.async.request.RegistrationAction
import net.corda.data.membership.async.request.RegistrationAsyncRequest
import net.corda.data.membership.common.RegistrationStatus
import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleEventHandler
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.RegistrationHandle
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.lifecycle.Resource
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import net.corda.membership.client.CouldNotFindMemberException
import net.corda.membership.client.RegistrationProgressNotFoundException
import net.corda.membership.client.ServiceNotReadyException
import net.corda.membership.client.dto.MemberInfoSubmittedDto
import net.corda.membership.client.dto.MemberRegistrationRequestDto
import net.corda.membership.client.dto.RegistrationActionDto
import net.corda.membership.client.dto.RegistrationRequestStatusDto
import net.corda.membership.client.dto.RegistrationStatusDto
import net.corda.membership.client.dto.SubmittedRegistrationStatus
import net.corda.membership.lib.registration.RegistrationRequestStatus
import net.corda.membership.persistence.client.MembershipPersistenceClient
import net.corda.membership.persistence.client.MembershipPersistenceResult
import net.corda.membership.persistence.client.MembershipQueryClient
import net.corda.membership.persistence.client.MembershipQueryResult
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.records.Record
import net.corda.schema.Schemas.Membership.Companion.MEMBERSHIP_ASYNC_REQUEST_TOPIC
import net.corda.schema.configuration.ConfigKeys
import net.corda.test.util.time.TestClock
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.ShortHash
import net.corda.virtualnode.VirtualNodeInfo
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.SoftAssertions.assertSoftly
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.nio.ByteBuffer
import java.time.Instant
import java.util.concurrent.CompletableFuture
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MemberOpsClientTest {
    companion object {
        private const val HOLDING_IDENTITY_ID = "00AABB00AABB"
        private val clock = TestClock(Instant.ofEpochSecond(100))
    }

    private val componentHandle: RegistrationHandle = mock()
    private val configHandle: Resource = mock()

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

    private val asyncPublisher = mock<Publisher>()

    private val publisherFactory = mock<PublisherFactory> {
        on { createPublisher(any(), any()) } doReturn asyncPublisher
    }

    private val configurationReadService: ConfigurationReadService = mock {
        on { registerComponentForUpdates(any(), any()) } doReturn configHandle
    }
    private val membershipPersistenceClient = mock<MembershipPersistenceClient> {
        on { persistRegistrationRequest(any(), any()) } doReturn MembershipPersistenceResult.success()
    }
    private val holdingIdentity = mock<HoldingIdentity>()
    private val virtualNodeInfo = mock<VirtualNodeInfo> {
        on { holdingIdentity } doReturn holdingIdentity
    }
    private val virtualNodeInfoReadService = mock<VirtualNodeInfoReadService> {
        on { getByHoldingIdentityShortHash(ShortHash.of(HOLDING_IDENTITY_ID)) } doReturn virtualNodeInfo
    }
    private val keyValuePairListSerializer = mock<CordaAvroSerializer<KeyValuePairList>> {
        on { serialize(any()) } doReturn byteArrayOf(1, 2, 3)
    }
    private val cordaAvroSerializationFactory = mock<CordaAvroSerializationFactory> {
        on { createAvroSerializer<KeyValuePairList>(any()) } doReturn keyValuePairListSerializer
    }
    private val membershipQueryClient = mock<MembershipQueryClient>()
    private val memberOpsClient = MemberOpsClientImpl(
        lifecycleCoordinatorFactory,
        publisherFactory,
        configurationReadService,
        membershipPersistenceClient,
        virtualNodeInfoReadService,
        membershipQueryClient,
        cordaAvroSerializationFactory,
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

    private fun setUpConfig() {
        // kicks off the MessagingConfigurationReceived event to be able to mock the rpc sender
        changeConfig()
    }

    private val request = MemberRegistrationRequestDto(
        ShortHash.of(HOLDING_IDENTITY_ID),
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
        verify(coordinator).updateStatus(eq(LifecycleStatus.UP), any())
    }

    @Test
    fun `checkRegistrationProgress return correct data`() {
        val response =
            listOf(
                RegistrationRequestStatus(
                    registrationSent = clock.instant().plusSeconds(3),
                    registrationLastModified = clock.instant().plusSeconds(7),
                    status = RegistrationStatus.APPROVED,
                    registrationId = "registration id",
                    protocolVersion = 1,
                    memberContext = KeyValuePairList(listOf(KeyValuePair("key", "value"))),
                ),
                RegistrationRequestStatus(
                    registrationSent = clock.instant().plusSeconds(10),
                    registrationLastModified = clock.instant().plusSeconds(20),
                    status = RegistrationStatus.SENT_TO_MGM,
                    registrationId = "registration id 2",
                    protocolVersion = 1,
                    memberContext = KeyValuePairList(listOf(KeyValuePair("key 2", "value 2"))),
                ),
                RegistrationRequestStatus(
                    registrationSent = clock.instant().plusSeconds(30),
                    registrationLastModified = clock.instant().plusSeconds(70),
                    status = RegistrationStatus.DECLINED,
                    registrationId = "registration id 3",
                    protocolVersion = 1,
                    memberContext = KeyValuePairList(listOf(KeyValuePair("key 3", "value 3"))),
                ),
            )
        whenever(membershipQueryClient.queryRegistrationRequestsStatus(
            any(), eq(null), eq(RegistrationStatus.values().toList()))
        ).doReturn(MembershipQueryResult.Success(response))

        memberOpsClient.start()
        setUpConfig()

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
                    registrationStatus = RegistrationStatusDto.SENT_TO_MGM,
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

    @Test
    fun `checkRegistrationProgress throw exception if member could not be found`() {
        whenever(virtualNodeInfoReadService.getByHoldingIdentityShortHash(any())).doReturn(null)

        memberOpsClient.start()
        setUpConfig()

        assertThrows<CouldNotFindMemberException> {
            memberOpsClient.checkRegistrationProgress(request.holdingIdentityShortHash)
        }
    }

    @Test
    fun `checkRegistrationProgress throw exception if the request fails`() {
        whenever(membershipQueryClient.queryRegistrationRequestsStatus(
            any(), eq(null), eq(RegistrationStatus.values().toList()))
        ).doReturn(MembershipQueryResult.Failure("oops"))

        memberOpsClient.start()
        setUpConfig()

        assertThrows<ServiceNotReadyException> {
            memberOpsClient.checkRegistrationProgress(request.holdingIdentityShortHash)
        }
    }

    @ParameterizedTest
    @EnumSource(RegistrationStatus::class)
    fun `checkSpecificRegistrationProgress return correct data when response is not null`(status: RegistrationStatus) {
        val response =
            RegistrationRequestStatus(
                registrationSent = clock.instant().plusSeconds(1),
                registrationLastModified = clock.instant().plusSeconds(2),
                status = status,
                registrationId = "registration id",
                protocolVersion = 1,
                memberContext = KeyValuePairList(listOf(KeyValuePair("key", "value"))),
            )
        whenever(
            membershipQueryClient.queryRegistrationRequestStatus(any(), any())
        ).doReturn(MembershipQueryResult.Success(response))
        memberOpsClient.start()
        setUpConfig()

        val result = memberOpsClient.checkSpecificRegistrationProgress(
            request.holdingIdentityShortHash, "registration id"
        )

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
    fun `checkSpecificRegistrationProgress throw exception if member could not be found`() {
        whenever(virtualNodeInfoReadService.getByHoldingIdentityShortHash(any())).doReturn(null)

        memberOpsClient.start()
        setUpConfig()

        assertThrows<CouldNotFindMemberException> {
            memberOpsClient.checkSpecificRegistrationProgress(request.holdingIdentityShortHash, "registration id")
        }
    }

    @Test
    fun `checkSpecificRegistrationProgress throw exception if request could not be found`() {
        whenever(membershipQueryClient.queryRegistrationRequestStatus(any(), any())).doReturn(MembershipQueryResult.Success(null))

        memberOpsClient.start()
        setUpConfig()

        assertThrows<RegistrationProgressNotFoundException> {
            memberOpsClient.checkSpecificRegistrationProgress(request.holdingIdentityShortHash, "registration id")
        }
    }

    @Test
    fun `checkSpecificRegistrationProgress throw exception if request fails`() {
        whenever(membershipQueryClient.queryRegistrationRequestStatus(any(), any())).doReturn(MembershipQueryResult.Failure("oops"))

        memberOpsClient.start()
        setUpConfig()

        assertThrows<ServiceNotReadyException> {
            memberOpsClient.checkSpecificRegistrationProgress(request.holdingIdentityShortHash, "registration id")
        }
    }

    @Test
    fun `startRegistration should add exception message to reason field if exception happened`() {
        val future = CompletableFuture.failedFuture<Unit>(
            CordaRuntimeException(
                "Ooops."
            )
        )
        whenever(asyncPublisher.publish(any())).doReturn(listOf(future))
        memberOpsClient.start()
        setUpConfig()

        val result = memberOpsClient.startRegistration(request)

        assertSoftly {
            it.assertThat(result.reason)
                .isEqualTo(
                    "Ooops."
                )
            it.assertThat(result.registrationStatus).isEqualTo(SubmittedRegistrationStatus.NOT_SUBMITTED)
        }
    }

    @Test
    fun `startRegistration publish the correct data`() {
        val records = argumentCaptor<List<Record<*, *>>>()
        whenever(asyncPublisher.publish(records.capture())).doReturn(emptyList())
        memberOpsClient.start()
        setUpConfig()

        memberOpsClient.startRegistration(request)

        assertThat(records.firstValue).hasSize(1)
            .anySatisfy { record ->
                assertThat(record.topic).isEqualTo(MEMBERSHIP_ASYNC_REQUEST_TOPIC)
                val value = record.value as? MembershipAsyncRequest
                val request = value?.request as? RegistrationAsyncRequest
                assertThat(request?.requestId).isEqualTo(record.key)
                assertThat(request?.holdingIdentityId).isEqualTo(HOLDING_IDENTITY_ID)
                assertThat(request?.registrationAction).isEqualTo(RegistrationAction.REQUEST_JOIN)
                assertThat(request?.context).isEqualTo(mapOf("property" to "test").toWire())
            }
    }

    @Test
    fun `startRegistration return submitted`() {
        whenever(asyncPublisher.publish(any())).doReturn(emptyList())
        memberOpsClient.start()
        setUpConfig()

        val result = memberOpsClient.startRegistration(request)

        assertThat(result.registrationStatus).isEqualTo(SubmittedRegistrationStatus.SUBMITTED)
    }

    @Test
    fun `startRegistration throws CouldNotFindMemberException for unknown member`() {
        whenever(virtualNodeInfoReadService.getByHoldingIdentityShortHash(any())).doReturn(null)
        whenever(asyncPublisher.publish(any())).doReturn(emptyList())
        memberOpsClient.start()
        setUpConfig()

        assertThrows<CouldNotFindMemberException> {
            memberOpsClient.startRegistration(request)
        }
    }

    @Test
    fun `startRegistration persist the correct data`() {
        whenever(asyncPublisher.publish(any())).doReturn(emptyList())
        memberOpsClient.start()
        setUpConfig()

        memberOpsClient.startRegistration(request)

        verify(membershipPersistenceClient).persistRegistrationRequest(
            eq(holdingIdentity),
            argThat {
                status == RegistrationStatus.NEW &&
                    requester == holdingIdentity &&
                    memberContext == ByteBuffer.wrap(byteArrayOf(1, 2, 3))
            },
        )
    }

    @Test
    fun `startRegistration will fail if persistence failed`() {
        whenever(membershipPersistenceClient.persistRegistrationRequest(any(), any()))
            .doReturn(MembershipPersistenceResult.Failure("Ooops"))
        memberOpsClient.start()
        setUpConfig()

        val result = memberOpsClient.startRegistration(request)

        assertThat(result.registrationStatus).isEqualTo(SubmittedRegistrationStatus.NOT_SUBMITTED)
    }

    @Test
    fun `startRegistration will not try to register if persistence failed`() {
        whenever(membershipPersistenceClient.persistRegistrationRequest(any(), any()))
            .doReturn(MembershipPersistenceResult.Failure("Ooops"))
        memberOpsClient.start()
        setUpConfig()

        memberOpsClient.startRegistration(request)

        verify(asyncPublisher, never()).publish(any())
    }
}
