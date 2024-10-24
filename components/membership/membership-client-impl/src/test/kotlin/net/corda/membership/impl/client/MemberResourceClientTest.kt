package net.corda.membership.impl.client

import net.corda.avro.serialization.CordaAvroDeserializer
import net.corda.avro.serialization.CordaAvroSerializationFactory
import net.corda.avro.serialization.CordaAvroSerializer
import net.corda.configuration.read.ConfigChangedEvent
import net.corda.configuration.read.ConfigurationReadService
import net.corda.crypto.core.ShortHash
import net.corda.crypto.impl.utils.toWire
import net.corda.data.KeyValuePair
import net.corda.data.KeyValuePairList
import net.corda.data.crypto.wire.CryptoSignatureSpec
import net.corda.data.crypto.wire.CryptoSignatureWithKey
import net.corda.data.membership.SignedData
import net.corda.data.membership.async.request.MembershipAsyncRequest
import net.corda.data.membership.common.RegistrationRequestDetails
import net.corda.data.membership.common.v2.RegistrationStatus
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
import net.corda.membership.client.CouldNotFindEntityException
import net.corda.membership.client.RegistrationProgressNotFoundException
import net.corda.membership.client.ServiceNotReadyException
import net.corda.membership.client.dto.MemberInfoSubmittedDto
import net.corda.membership.client.dto.RegistrationRequestStatusDto
import net.corda.membership.client.dto.RegistrationStatusDto
import net.corda.membership.client.dto.SubmittedRegistrationStatus
import net.corda.membership.lib.ContextDeserializationException
import net.corda.membership.lib.MemberInfoExtension
import net.corda.membership.lib.registration.PRE_AUTH_TOKEN
import net.corda.membership.lib.registration.RegistrationRequest
import net.corda.membership.persistence.client.MembershipPersistenceClient
import net.corda.membership.persistence.client.MembershipPersistenceOperation
import net.corda.membership.persistence.client.MembershipPersistenceResult
import net.corda.membership.persistence.client.MembershipQueryClient
import net.corda.membership.persistence.client.MembershipQueryResult
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.records.Record
import net.corda.schema.Schemas.Membership.MEMBERSHIP_ASYNC_REQUEST_TOPIC
import net.corda.schema.configuration.ConfigKeys
import net.corda.test.util.time.TestClock
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.virtualnode.HoldingIdentity
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
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import java.nio.ByteBuffer
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CompletableFuture
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MemberResourceClientTest {
    companion object {
        private const val HOLDING_IDENTITY_ID = "00AABB00AABB"
        private val clock = TestClock(Instant.ofEpochSecond(100))
        private const val SERIAL = 0L
    }

    private val componentHandle: RegistrationHandle = mock()
    private val configHandle: Resource = mock()

    private var coordinatorIsRunning = false
    private val resources = mutableMapOf<String, Resource>()
    private val coordinator: LifecycleCoordinator = mock {
        on { isRunning } doAnswer { coordinatorIsRunning }
        on { start() } doAnswer { coordinatorIsRunning = true }
        on { stop() } doAnswer { coordinatorIsRunning = false }
        on { followStatusChangesByName(any()) } doReturn componentHandle
        on { createManagedResource(any(), any<() -> Resource>()) } doAnswer {
            resources.compute(it.getArgument(0)) { _, r ->
                r?.close()
                it.getArgument<() -> Resource>(1).invoke()
            }
        }
        on { closeManagedResources(any()) } doAnswer {
            it.getArgument<Collection<String>>(0).forEach { name ->
                resources.remove(name)?.close()
            }
        }
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
    private val operation = mock<MembershipPersistenceOperation<Unit>> {
        on { execute() } doReturn MembershipPersistenceResult.success()
    }
    private val membershipPersistenceClient = mock<MembershipPersistenceClient> {
        on { persistRegistrationRequest(any(), any()) } doReturn operation
    }
    private val bytes = byteArrayOf(1, 2, 3)
    private val signatureWithKey: CryptoSignatureWithKey = mock()
    private val signatureSpec: CryptoSignatureSpec = mock()
    private val signedContext = SignedData(
        ByteBuffer.wrap(bytes),
        signatureWithKey,
        signatureSpec,
    )
    private val holdingIdentity = mock<HoldingIdentity>()
    private val virtualNodeInfo = mock<VirtualNodeInfo> {
        on { holdingIdentity } doReturn holdingIdentity
    }
    private val virtualNodeInfoReadService = mock<VirtualNodeInfoReadService> {
        on { getByHoldingIdentityShortHash(ShortHash.of(HOLDING_IDENTITY_ID)) } doReturn virtualNodeInfo
    }
    private val keyValuePairListSerializer = mock<CordaAvroSerializer<KeyValuePairList>> {
        on { serialize(any()) } doReturn bytes
    }
    private val keyValuePairListDeserializer = mock<CordaAvroDeserializer<KeyValuePairList>>()
    private val cordaAvroSerializationFactory = mock<CordaAvroSerializationFactory> {
        on { createAvroSerializer<KeyValuePairList>(any()) } doReturn keyValuePairListSerializer
        on { createAvroDeserializer(any(), eq(KeyValuePairList::class.java)) } doReturn keyValuePairListDeserializer
    }
    private val membershipQueryClient = mock<MembershipQueryClient>()
    private val memberOpsClient = MemberResourceClientImpl(
        lifecycleCoordinatorFactory,
        publisherFactory,
        configurationReadService,
        membershipPersistenceClient,
        virtualNodeInfoReadService,
        membershipQueryClient,
        cordaAvroSerializationFactory,
    )

    private val messagingConfig: SmartConfig = mock()
    private val bootConfig: SmartConfig = mock()

    private val configs = mapOf(
        ConfigKeys.BOOT_CONFIG to bootConfig,
        ConfigKeys.MESSAGING_CONFIG to messagingConfig
    )

    private fun startComponent() = lifecycleHandler?.processEvent(StartEvent(), coordinator)
    private fun stopComponent() = lifecycleHandler?.processEvent(StopEvent(), coordinator)
    private fun changeRegistrationStatus(status: LifecycleStatus) = lifecycleHandler?.processEvent(
        RegistrationStatusChangeEvent(mock(), status),
        coordinator
    )

    private fun changeConfig() = lifecycleHandler?.processEvent(
        ConfigChangedEvent(setOf(ConfigKeys.BOOT_CONFIG, ConfigKeys.MESSAGING_CONFIG), configs),
        coordinator
    )

    private fun setUpConfig() {
        // kicks off the MessagingConfigurationReceived event to be able to mock the REST sender
        changeConfig()
    }

    private val holdingIdentityId = ShortHash.of(HOLDING_IDENTITY_ID)
    private val context = mapOf("property" to "test")

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
            any(),
            any()
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
            any(),
            any()
        )

        changeRegistrationStatus(LifecycleStatus.DOWN)

        verify(configHandle).close()
    }

    @Test
    fun `registration status UP closes config handle if status was previously UP`() {
        changeRegistrationStatus(LifecycleStatus.UP)

        verify(configurationReadService).registerComponentForUpdates(
            any(),
            any()
        )

        changeRegistrationStatus(LifecycleStatus.UP)

        verify(configHandle).close()
    }

    @Test
    fun `second config change will closes the publisher if status was previously UP`() {
        changeRegistrationStatus(LifecycleStatus.UP)
        changeConfig()

        changeConfig()

        verify(asyncPublisher).close()
    }

    @Test
    fun `after receiving the messaging configuration the REST sender is initialized`() {
        changeConfig()
        verify(coordinator).updateStatus(eq(LifecycleStatus.UP), any())
    }

    @Test
    fun `checkRegistrationProgress return correct data`() {
        val bytesId1 = "id1".toByteArray()
        whenever(keyValuePairListDeserializer.deserialize(bytesId1))
            .doReturn(KeyValuePairList(listOf(KeyValuePair("key", "value"))))
        val bytesId2 = "id2".toByteArray()
        whenever(keyValuePairListDeserializer.deserialize(bytesId2))
            .doReturn(KeyValuePairList(listOf(KeyValuePair("key 2", "value 2"))))
        val bytesId3 = "id3".toByteArray()
        whenever(keyValuePairListDeserializer.deserialize(bytesId3))
            .doReturn(KeyValuePairList(listOf(KeyValuePair("key 3", "value 3"))))
        val response =
            listOf(
                RegistrationRequestDetails(
                    clock.instant().plusSeconds(3),
                    clock.instant().plusSeconds(7),
                    RegistrationStatus.APPROVED,
                    "registration id",
                    "holdingId1",
                    1,
                    SignedData(
                        ByteBuffer.wrap(bytesId1),
                        signatureWithKey,
                        signatureSpec,
                    ),
                    signedContext,
                    null,
                    SERIAL,
                ),
                RegistrationRequestDetails(
                    clock.instant().plusSeconds(10),
                    clock.instant().plusSeconds(20),
                    RegistrationStatus.SENT_TO_MGM,
                    "registration id 2",
                    "holdingId2",
                    1,
                    SignedData(
                        ByteBuffer.wrap(bytesId2),
                        signatureWithKey,
                        signatureSpec,
                    ),
                    signedContext,
                    null,
                    SERIAL,
                ),
                RegistrationRequestDetails(
                    clock.instant().plusSeconds(30),
                    clock.instant().plusSeconds(70),
                    RegistrationStatus.DECLINED,
                    "registration id 3",
                    "holdingId3",
                    1,
                    SignedData(
                        ByteBuffer.wrap(bytesId3),
                        signatureWithKey,
                        signatureSpec,
                    ),
                    signedContext,
                    null,
                    SERIAL,
                ),
            )
        whenever(
            membershipQueryClient.queryRegistrationRequests(
                any(),
                eq(null),
                eq(RegistrationStatus.values().toList()),
                eq(null)
            )
        ).doReturn(MembershipQueryResult.Success(response))

        memberOpsClient.start()
        setUpConfig()

        val statuses = memberOpsClient.checkRegistrationProgress(holdingIdentityId)

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
                    ),
                    serial = SERIAL,
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
                    ),
                    serial = SERIAL,
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
                    ),
                    serial = SERIAL,
                ),
            )
    }

    @Test
    fun `checkRegistrationProgress throw exception if member could not be found`() {
        whenever(virtualNodeInfoReadService.getByHoldingIdentityShortHash(any())).doReturn(null)

        memberOpsClient.start()
        setUpConfig()

        assertThrows<CouldNotFindEntityException> {
            memberOpsClient.checkRegistrationProgress(holdingIdentityId)
        }
    }

    @Test
    fun `checkRegistrationProgress throw exception if the request fails`() {
        whenever(
            membershipQueryClient.queryRegistrationRequests(
                any(),
                eq(null),
                eq(RegistrationStatus.values().toList()),
                eq(null)
            )
        ).doReturn(MembershipQueryResult.Failure("oops"))

        memberOpsClient.start()
        setUpConfig()

        assertThrows<ServiceNotReadyException> {
            memberOpsClient.checkRegistrationProgress(holdingIdentityId)
        }
    }

    @Test
    fun `checkRegistrationProgress throw exception if deserialization fails`() {
        whenever(
            membershipQueryClient.queryRegistrationRequests(
                any(),
                eq(null),
                eq(RegistrationStatus.values().toList()),
                eq(null)
            )
        ).doReturn(
            MembershipQueryResult.Success(
                listOf(
                    RegistrationRequestDetails(
                        clock.instant().plusSeconds(3),
                        clock.instant().plusSeconds(7),
                        RegistrationStatus.APPROVED,
                        "registration id",
                        "holdingId1",
                        1,
                        signedContext,
                        signedContext,
                        null,
                        SERIAL,
                    )
                )
            )
        )

        memberOpsClient.start()
        setUpConfig()

        assertThrows<ContextDeserializationException> {
            memberOpsClient.checkRegistrationProgress(holdingIdentityId)
        }
    }

    @ParameterizedTest
    @EnumSource(RegistrationStatus::class)
    fun `checkSpecificRegistrationProgress return correct data when response is not null`(status: RegistrationStatus) {
        val bytesId = "id".toByteArray()
        whenever(keyValuePairListDeserializer.deserialize(bytesId))
            .doReturn(KeyValuePairList(listOf(KeyValuePair("key", "value"))))
        val response =
            RegistrationRequestDetails(
                clock.instant().plusSeconds(1),
                clock.instant().plusSeconds(2),
                status,
                "registration id",
                "holdingId1",
                1,
                SignedData(
                    ByteBuffer.wrap(bytesId),
                    signatureWithKey,
                    signatureSpec,
                ),
                signedContext,
                null,
                SERIAL,
            )
        whenever(
            membershipQueryClient.queryRegistrationRequest(any(), any())
        ).doReturn(MembershipQueryResult.Success(response))
        memberOpsClient.start()
        setUpConfig()

        val result = memberOpsClient.checkSpecificRegistrationProgress(
            holdingIdentityId,
            "registration id"
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
                    ),
                    serial = SERIAL,
                )
            )
    }

    @Test
    fun `checkSpecificRegistrationProgress throw exception if member could not be found`() {
        whenever(virtualNodeInfoReadService.getByHoldingIdentityShortHash(any())).doReturn(null)

        memberOpsClient.start()
        setUpConfig()

        assertThrows<CouldNotFindEntityException> {
            memberOpsClient.checkSpecificRegistrationProgress(holdingIdentityId, "registration id")
        }
    }

    @Test
    fun `checkSpecificRegistrationProgress throw exception if request could not be found`() {
        whenever(membershipQueryClient.queryRegistrationRequest(any(), any())).doReturn(MembershipQueryResult.Success(null))

        memberOpsClient.start()
        setUpConfig()

        assertThrows<RegistrationProgressNotFoundException> {
            memberOpsClient.checkSpecificRegistrationProgress(holdingIdentityId, "registration id")
        }
    }

    @Test
    fun `checkSpecificRegistrationProgress throw exception if request fails`() {
        whenever(membershipQueryClient.queryRegistrationRequest(any(), any())).doReturn(MembershipQueryResult.Failure("oops"))

        memberOpsClient.start()
        setUpConfig()

        assertThrows<ServiceNotReadyException> {
            memberOpsClient.checkSpecificRegistrationProgress(holdingIdentityId, "registration id")
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

        val result = memberOpsClient.startRegistration(holdingIdentityId, context)

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

        memberOpsClient.startRegistration(holdingIdentityId, context)

        assertThat(records.firstValue).hasSize(1)
            .anySatisfy { record ->
                assertThat(record.topic).isEqualTo(MEMBERSHIP_ASYNC_REQUEST_TOPIC)
                assertThat(record.key.toString()).isEqualTo(HOLDING_IDENTITY_ID)
                val value = record.value as? MembershipAsyncRequest
                val request = value?.request
                assertThat(request?.holdingIdentityId).isEqualTo(HOLDING_IDENTITY_ID)
                assertThat(request?.context).isEqualTo(mapOf("property" to "test").toWire())
            }
    }

    @Test
    fun `startRegistration uses serial specified in registration context`() {
        val serial = 12L
        val context = mapOf("property" to "test", MemberInfoExtension.SERIAL to serial.toString())
        memberOpsClient.start()
        setUpConfig()

        val capturedRequest = argumentCaptor<RegistrationRequest>()
        memberOpsClient.startRegistration(holdingIdentityId, context)
        verify(membershipPersistenceClient).persistRegistrationRequest(eq(holdingIdentity), capturedRequest.capture())
        assertThat(capturedRequest.firstValue.serial).isEqualTo(serial)
    }

    @Test
    fun `startRegistration return submitted`() {
        whenever(asyncPublisher.publish(any())).doReturn(emptyList())
        memberOpsClient.start()
        setUpConfig()

        val result = memberOpsClient.startRegistration(holdingIdentityId, context)

        assertThat(result.registrationStatus).isEqualTo(SubmittedRegistrationStatus.SUBMITTED)
        assertThat(result.availableNow).isEqualTo(true)
    }

    @Test
    fun `startRegistration throws CouldNotFindMemberException for unknown member`() {
        whenever(virtualNodeInfoReadService.getByHoldingIdentityShortHash(any())).doReturn(null)
        whenever(asyncPublisher.publish(any())).doReturn(emptyList())
        memberOpsClient.start()
        setUpConfig()

        assertThrows<CouldNotFindEntityException> {
            memberOpsClient.startRegistration(holdingIdentityId, context)
        }
    }

    @Test
    fun `startRegistration persist the correct data`() {
        whenever(asyncPublisher.publish(any())).doReturn(emptyList())
        memberOpsClient.start()
        setUpConfig()

        val expectedMemberContext = mapOf("property" to "test")
        val expectedRegistrationContext = mapOf(PRE_AUTH_TOKEN to UUID(0, 1).toString())
        val inputContext = expectedMemberContext + expectedRegistrationContext

        val serialisedMemberContext = "member-context".toByteArray()
        val serialisedRegistrationContext = "registration-context".toByteArray()

        whenever(
            keyValuePairListSerializer.serialize(expectedMemberContext.toWire())
        ).doReturn(serialisedMemberContext)
        whenever(
            keyValuePairListSerializer.serialize(expectedRegistrationContext.toWire())
        ).doReturn(serialisedRegistrationContext)

        memberOpsClient.startRegistration(holdingIdentityId, inputContext)

        verify(membershipPersistenceClient).persistRegistrationRequest(
            eq(holdingIdentity),
            argThat {
                status == RegistrationStatus.NEW &&
                    requester == holdingIdentity &&
                    memberContext.data == ByteBuffer.wrap(serialisedMemberContext) &&
                    registrationContext.data == ByteBuffer.wrap(serialisedRegistrationContext)
            },
        )
    }

    @Test
    fun `startRegistration will be successful if persistence failed`() {
        whenever(operation.execute())
            .doReturn(MembershipPersistenceResult.Failure("Oops"))
        memberOpsClient.start()
        setUpConfig()

        val result = memberOpsClient.startRegistration(holdingIdentityId, context)

        assertThat(result.registrationStatus).isEqualTo(SubmittedRegistrationStatus.SUBMITTED)
        assertThat(result.availableNow).isEqualTo(false)
    }

    @Test
    fun `startRegistration will try to post async command to persistence layer if sync command failed`() {
        val record = Record(
            "topic",
            "key",
            4
        )
        whenever(operation.execute())
            .doReturn(MembershipPersistenceResult.Failure("Oops"))
        whenever(operation.createAsyncCommands()).doReturn(listOf(record))
        memberOpsClient.start()
        setUpConfig()

        memberOpsClient.startRegistration(holdingIdentityId, context)

        verify(asyncPublisher).publish(listOf(record))
    }

    @Test
    fun `startRegistration will not try to persist if published will fail`() {
        whenever(asyncPublisher.publish(any()))
            .doThrow(CordaRuntimeException(""))
        memberOpsClient.start()
        setUpConfig()

        memberOpsClient.startRegistration(holdingIdentityId, context)

        verifyNoInteractions(membershipPersistenceClient)
    }
}
