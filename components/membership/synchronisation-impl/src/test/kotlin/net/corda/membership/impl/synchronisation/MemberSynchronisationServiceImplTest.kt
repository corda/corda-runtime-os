package net.corda.membership.impl.synchronisation

import com.typesafe.config.ConfigFactory
import net.corda.chunking.toCorda
import net.corda.configuration.read.ConfigChangedEvent
import net.corda.configuration.read.ConfigurationReadService
import net.corda.data.CordaAvroDeserializer
import net.corda.data.CordaAvroSerializationFactory
import net.corda.data.KeyValuePair
import net.corda.data.KeyValuePairList
import net.corda.data.crypto.SecureHash
import net.corda.data.crypto.wire.CryptoSignatureWithKey
import net.corda.data.membership.PersistentMemberInfo
import net.corda.data.membership.SignedMemberInfo
import net.corda.data.membership.command.synchronisation.SynchronisationMetaData
import net.corda.data.membership.command.synchronisation.member.ProcessMembershipUpdates
import net.corda.data.membership.p2p.MembershipPackage
import net.corda.data.membership.p2p.MembershipSyncRequest
import net.corda.data.membership.p2p.SignedMemberships
import net.corda.data.membership.p2p.WireGroupParameters
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.SmartConfigFactory
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
import net.corda.lifecycle.TimerEvent
import net.corda.membership.groupparams.writer.service.GroupParametersWriterService
import net.corda.membership.lib.GroupParametersFactory
import net.corda.membership.lib.MemberInfoExtension
import net.corda.membership.lib.MemberInfoExtension.Companion.PARTY_NAME
import net.corda.membership.lib.MemberInfoExtension.Companion.groupId
import net.corda.membership.lib.MemberInfoExtension.Companion.id
import net.corda.membership.lib.MemberInfoFactory
import net.corda.membership.p2p.helpers.MerkleTreeGenerator
import net.corda.membership.p2p.helpers.P2pRecordsFactory
import net.corda.membership.p2p.helpers.Verifier
import net.corda.membership.persistence.client.MembershipPersistenceClient
import net.corda.membership.read.MembershipGroupReader
import net.corda.membership.read.MembershipGroupReaderProvider
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.records.Record
import net.corda.data.p2p.app.AppMessage
import net.corda.schema.Schemas.Membership.MEMBER_LIST_TOPIC
import net.corda.schema.configuration.ConfigKeys
import net.corda.schema.configuration.MembershipConfig
import net.corda.test.util.time.TestClock
import net.corda.utilities.minutes
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.crypto.merkle.MerkleTree
import net.corda.v5.membership.GroupParameters
import net.corda.v5.membership.MGMContext
import net.corda.v5.membership.MemberContext
import net.corda.v5.membership.MemberInfo
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import net.corda.virtualnode.toAvro
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.SoftAssertions.assertSoftly
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.KArgumentCaptor
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doNothing
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.nio.ByteBuffer
import java.time.Instant
import java.util.*
import java.util.concurrent.CompletableFuture
import kotlin.test.assertFailsWith

class MemberSynchronisationServiceImplTest {
    private companion object {
        const val GROUP_NAME = "dummy_group"
        const val PUBLISHER_CLIENT_ID = "member-synchronisation-service"
        val MEMBER_CONTEXT_BYTES = "2222".toByteArray()
        val MGM_CONTEXT_BYTES = "3333".toByteArray()
        val GROUP_PARAMETERS_BYTES = "dummy-parameters".toByteArray()
    }
    private val mockPublisher = mock<Publisher>().apply {
        whenever(publish(any())).thenReturn(listOf(CompletableFuture.completedFuture(Unit)))
    }
    private val publisherFactory: PublisherFactory = mock {
        on { createPublisher(any(), any()) } doReturn mockPublisher
    }
    private val componentHandle: RegistrationHandle = mock()
    private val configHandle: Resource = mock()
    private val testConfig =
        SmartConfigFactory.createWithoutSecurityServices().create(ConfigFactory.parseString("instanceId=1"))
    private val membershipConfig = mock<SmartConfig> {
        on { getLong(MembershipConfig.MAX_DURATION_BETWEEN_SYNC_REQUESTS_MINUTES) } doReturn 10
    }
    private val dependentComponents = setOf(
        LifecycleCoordinatorName.forComponent<ConfigurationReadService>(),
        LifecycleCoordinatorName.forComponent<VirtualNodeInfoReadService>(),
    )

    private var coordinatorIsRunning = false
    private var coordinatorStatus = argumentCaptor<LifecycleStatus>()
    private val coordinator: LifecycleCoordinator = mock {
        on { followStatusChangesByName(eq(dependentComponents)) } doReturn componentHandle
        on { isRunning } doAnswer { coordinatorIsRunning }
        on { start() } doAnswer {
            coordinatorIsRunning = true
            lifecycleHandlerCaptor.firstValue.processEvent(StartEvent(), mock)
        }
        on { stop() } doAnswer {
            coordinatorIsRunning = false
            lifecycleHandlerCaptor.firstValue.processEvent(StopEvent(), mock)
        }
        doNothing().whenever(it).updateStatus(coordinatorStatus.capture(), any())
        on { status } doAnswer { coordinatorStatus.firstValue }
    }

    private val lifecycleHandlerCaptor: KArgumentCaptor<LifecycleEventHandler> = argumentCaptor()
    private val lifecycleCoordinatorFactory: LifecycleCoordinatorFactory = mock {
        on { createCoordinator(any(), lifecycleHandlerCaptor.capture()) } doReturn coordinator
    }
    private val configurationReadService: ConfigurationReadService = mock {
        on { registerComponentForUpdates(eq(coordinator), any()) } doReturn configHandle
    }
    private val participantName = MemberX500Name("Bob", "London", "GB")
    private val participantId = HoldingIdentity(participantName, GROUP_NAME).toAvro()
    private val memberProvidedContext = mock<MemberContext> {
        on { entries } doReturn mapOf(PARTY_NAME to participantName.toString()).entries
    }
    private val mgmProvidedContext = mock<MGMContext> {
        on { entries } doReturn emptySet()
    }
    private val participant: MemberInfo = mock {
        on { memberProvidedContext } doReturn memberProvidedContext
        on { mgmProvidedContext } doReturn mgmProvidedContext
        on { name } doReturn participantName
        on { groupId } doReturn GROUP_NAME
    }
    private val memberInfoFactory: MemberInfoFactory = mock {
        on { create(any()) } doReturn participant
        on { create(any<SortedMap<String, String?>>(), any()) } doReturn participant
    }
    private val memberName = MemberX500Name("Alice", "London", "GB")
    private val member = HoldingIdentity(memberName, GROUP_NAME)
    private val memberContextList = KeyValuePairList(listOf(KeyValuePair(PARTY_NAME, participantName.toString())))
    private val mgmContextList = KeyValuePairList(listOf())
    private val keyValuePairListDeserializer: CordaAvroDeserializer<KeyValuePairList> = mock {
        on { deserialize(MEMBER_CONTEXT_BYTES) } doReturn memberContextList
        on { deserialize(MGM_CONTEXT_BYTES) } doReturn mgmContextList
        on { deserialize(GROUP_PARAMETERS_BYTES) } doReturn mock()
    }
    private val serializationFactory: CordaAvroSerializationFactory = mock {
        on { createAvroDeserializer(any(), eq(KeyValuePairList::class.java)) } doReturn keyValuePairListDeserializer
    }
    private val memberContext: ByteBuffer = mock {
        on { array() } doReturn MEMBER_CONTEXT_BYTES
    }
    private val mgmContext: ByteBuffer = mock {
        on { array() } doReturn MGM_CONTEXT_BYTES
    }
    private val memberSignature = mock<CryptoSignatureWithKey>()
    private val mgmSignature = mock<CryptoSignatureWithKey>()
    private val signedMemberInfo: SignedMemberInfo = mock {
        on { memberContext } doReturn memberContext
        on { mgmContext } doReturn mgmContext
        on { memberSignature } doReturn memberSignature
        on { mgmSignature } doReturn mgmSignature
    }
    private val hash = SecureHash("algo", ByteBuffer.wrap(byteArrayOf(1, 2, 3)))
    private val signedMemberships: SignedMemberships = mock {
        on { memberships } doReturn listOf(signedMemberInfo)
        on { hashCheck } doReturn hash
    }
    private val mgmSignatureGroupParameters = mock<CryptoSignatureWithKey>()
    private val wireGroupParameters = mock<WireGroupParameters> {
        on { mgmSignature } doReturn mgmSignatureGroupParameters
        on { groupParameters } doReturn ByteBuffer.wrap(GROUP_PARAMETERS_BYTES)
    }
    private val membershipPackage: MembershipPackage = mock {
        on { memberships } doReturn signedMemberships
        on { groupParameters } doReturn wireGroupParameters
    }
    private val synchronisationMetadata = mock<SynchronisationMetaData> {
        on { member } doReturn member.toAvro()
        on { mgm } doReturn participantId
    }
    private val updates: ProcessMembershipUpdates = mock {
        on { membershipPackage } doReturn membershipPackage
        on { synchronisationMetaData } doReturn synchronisationMetadata
    }
    private val synchronisationRequest = mock<Record<String, AppMessage>>()
    private val synchRequest = argumentCaptor<MembershipSyncRequest>()
    private val p2pRecordsFactory = mock<P2pRecordsFactory> {
        on {
            createAuthenticatedMessageRecord(
                eq(member.toAvro()),
                eq(HoldingIdentity(participantName, GROUP_NAME).toAvro()),
                synchRequest.capture(),
                isNull(),
                any(),
            )
        } doReturn synchronisationRequest
    }
    private val tree = mock<MerkleTree> {
        on { root } doReturn hash.toCorda()
    }
    private val merkleTreeGenerator = mock<MerkleTreeGenerator> {
        on { generateTree(any()) } doReturn tree
        on { createTree(any()) } doReturn tree
    }
    private val memberInfo = mock<MemberInfo>()
    private val groupReader = mock<MembershipGroupReader> {
        on { lookup() } doReturn emptyList()
        on { lookup(any()) } doReturn memberInfo
    }
    private val groupReaderProvider = mock<MembershipGroupReaderProvider> {
        on { getGroupReader(member) } doReturn groupReader
    }
    private val locallyHostedMembersReader = mock<LocallyHostedMembersReader> {
        on { readAllLocalMembers() } doReturn emptyList()
    }
    private val clock = TestClock(Instant.ofEpochSecond(100))
    private val verifier = mock<Verifier>()
    private val persistenceClient = mock<MembershipPersistenceClient> {
        on { persistGroupParameters(any(), any()) } doAnswer { invocation ->
            mock {
                on {
                    getOrThrow()
                } doReturn invocation.getArgument(1)
            }
        }
    }
    private val groupParameters = mock<GroupParameters>()
    private val groupParametersFactory = mock<GroupParametersFactory> {
        on { create(any()) } doReturn groupParameters
    }
    private val groupParametersWriterService = mock<GroupParametersWriterService>()
    private val synchronisationService = MemberSynchronisationServiceImpl(
        publisherFactory,
        configurationReadService,
        lifecycleCoordinatorFactory,
        serializationFactory,
        memberInfoFactory,
        groupReaderProvider,
        verifier,
        locallyHostedMembersReader,
        p2pRecordsFactory,
        merkleTreeGenerator,
        clock,
        persistenceClient,
        groupParametersFactory,
        groupParametersWriterService,
    )

    private fun postStartEvent() {
        lifecycleHandlerCaptor.firstValue.processEvent(StartEvent(), coordinator)
    }

    private fun postStopEvent() {
        lifecycleHandlerCaptor.firstValue.processEvent(StopEvent(), coordinator)
    }

    private fun postRegistrationStatusChangeEvent(
        status: LifecycleStatus,
        handle: RegistrationHandle = componentHandle
    ) {
        lifecycleHandlerCaptor.firstValue.processEvent(
            RegistrationStatusChangeEvent(
                handle,
                status
            ),
            coordinator
        )
    }

    private fun postConfigChangedEvent() {
        lifecycleHandlerCaptor.firstValue.processEvent(
            ConfigChangedEvent(
                setOf(ConfigKeys.BOOT_CONFIG, ConfigKeys.MESSAGING_CONFIG, ConfigKeys.MEMBERSHIP_CONFIG),
                mapOf(
                    ConfigKeys.BOOT_CONFIG to testConfig,
                    ConfigKeys.MESSAGING_CONFIG to testConfig,
                    ConfigKeys.MEMBERSHIP_CONFIG to membershipConfig,
                )
            ),
            coordinator
        )
    }

    @Test
    fun `starting the service succeeds`() {
        synchronisationService.start()
        assertThat(synchronisationService.isRunning).isTrue
        verify(coordinator).start()
    }

    @Test
    fun `stopping the service succeeds`() {
        synchronisationService.start()
        synchronisationService.stop()
        assertThat(synchronisationService.isRunning).isFalse
        verify(coordinator).stop()
    }

    @Test
    fun `member list is successfully published on receiving membership package from MGM`() {
        postConfigChangedEvent()
        synchronisationService.start()
        val capturedPublishedList = argumentCaptor<List<Record<String, Any>>>()
        whenever(mockPublisher.publish(capturedPublishedList.capture())).thenReturn(listOf(CompletableFuture.completedFuture(Unit)))

        synchronisationService.processMembershipUpdates(updates)

        val publishedMemberList = capturedPublishedList.firstValue
        assertSoftly {
            it.assertThat(publishedMemberList).hasSize(1)

            val publishedMember = publishedMemberList.first()
            it.assertThat(publishedMember.topic).isEqualTo(MEMBER_LIST_TOPIC)
            it.assertThat(publishedMember.key).isEqualTo("${member.shortHash}-${participant.id}")
            it.assertThat(publishedMember.value).isInstanceOf(PersistentMemberInfo::class.java)
            val value = publishedMember.value as? PersistentMemberInfo
            val name = value?.memberContext?.items?.firstOrNull { item -> item.key == PARTY_NAME }?.value
            it.assertThat(name).isEqualTo(participantName.toString())
            it.assertThat(value?.mgmContext?.items).isEmpty()
        }
    }

    @Test
    fun `group parameters are successfully persisted on receiving membership package from MGM`() {
        postConfigChangedEvent()
        synchronisationService.start()
        val capturedPersistedGroupParameters = argumentCaptor<GroupParameters>()
        whenever(
            persistenceClient.persistGroupParameters(
                any(),
                capturedPersistedGroupParameters.capture()
            )
        ).thenReturn(mock())

        synchronisationService.processMembershipUpdates(updates)

        val persistedGroupParameters = capturedPersistedGroupParameters.firstValue
        assertSoftly {
            it.assertThat(persistedGroupParameters).isEqualTo(groupParameters)
        }
    }

    @Test
    fun `group parameters are successfully published to kafka on receiving membership package from MGM`() {
        postConfigChangedEvent()
        synchronisationService.start()
        val capturedPersistedGroupParameters = argumentCaptor<GroupParameters>()
        doNothing().whenever(groupParametersWriterService).put(any(), capturedPersistedGroupParameters.capture())

        synchronisationService.processMembershipUpdates(updates)

        val publishedGroupParameters = capturedPersistedGroupParameters.firstValue
        assertSoftly {
            it.assertThat(publishedGroupParameters).isEqualTo(groupParameters)
        }
    }

    @Test
    fun `failed member signature verification will not persist the member`() {
        whenever(verifier.verify(eq(memberSignature), any())).thenThrow(CordaRuntimeException("Mock failure"))
        postConfigChangedEvent()
        synchronisationService.start()

        synchronisationService.processMembershipUpdates(updates)

        verify(mockPublisher, never()).publish(any())
    }

    @Test
    fun `failed MGM signature verification will not persist the member`() {
        whenever(verifier.verify(eq(mgmSignature), any())).thenThrow(CordaRuntimeException("Mock failure"))
        postConfigChangedEvent()
        synchronisationService.start()

        synchronisationService.processMembershipUpdates(updates)

        verify(mockPublisher, never()).publish(any())
    }

    @Test
    fun `failed MGM signature verification will not persist the group parameters`() {
        whenever(verifier.verify(eq(mgmSignatureGroupParameters), any())).thenThrow(CordaRuntimeException("Mock failure"))
        postConfigChangedEvent()
        synchronisationService.start()

        synchronisationService.processMembershipUpdates(updates)

        verify(persistenceClient, never()).persistGroupParameters(any(), any())
    }

    @Test
    fun `failed MGM signature verification will not publish the group parameters to Kafka`() {
        whenever(verifier.verify(eq(mgmSignatureGroupParameters), any())).thenThrow(CordaRuntimeException("Mock failure"))
        postConfigChangedEvent()
        synchronisationService.start()

        synchronisationService.processMembershipUpdates(updates)

        verify(groupParametersWriterService, never()).put(any(), any())
    }

    @Test
    fun `verification is called with the correct data on receiving membership package from MGM`() {
        postConfigChangedEvent()
        synchronisationService.start()

        synchronisationService.processMembershipUpdates(updates)

        verify(verifier).verify(memberSignature, MEMBER_CONTEXT_BYTES)
        verify(verifier).verify(mgmSignature, byteArrayOf(1, 2, 3))
    }

    @Test
    fun `processMembershipUpdates asks for synchronization if hash is empty`() {
        postConfigChangedEvent()
        synchronisationService.start()
        val capturedPublishedList = argumentCaptor<List<Record<String, *>>>()
        whenever(mockPublisher.publish(capturedPublishedList.capture())).doReturn(listOf(CompletableFuture.completedFuture(Unit)))
        whenever(signedMemberships.hashCheck) doReturn null

        synchronisationService.processMembershipUpdates(updates)

        val publishedMemberList = capturedPublishedList.firstValue
        assertSoftly {
            it.assertThat(publishedMemberList)
                .hasSize(2)
                .anySatisfy {
                    assertThat(it.topic).isEqualTo(MEMBER_LIST_TOPIC)
                }
                .contains(synchronisationRequest)
        }
    }

    @Test
    fun `processMembershipUpdates asks for synchronization when hashes are misaligned`() {
        postConfigChangedEvent()
        synchronisationService.start()
        val capturedPublishedList = argumentCaptor<List<Record<String, *>>>()
        whenever(mockPublisher.publish(capturedPublishedList.capture())).doReturn(listOf(CompletableFuture.completedFuture(Unit)))
        whenever(signedMemberships.hashCheck) doReturn SecureHash("algo", ByteBuffer.wrap(byteArrayOf(4, 5, 6)))

        synchronisationService.processMembershipUpdates(updates)

        val publishedMemberList = capturedPublishedList.firstValue
        assertSoftly {
            it.assertThat(publishedMemberList)
                .hasSize(2)
                .anySatisfy {
                    assertThat(it.topic).isEqualTo(MEMBER_LIST_TOPIC)
                }
                .contains(synchronisationRequest)
        }
    }

    @Test
    fun `processMembershipUpdates create the correct sync request when hashes are misaligned`() {
        postConfigChangedEvent()
        synchronisationService.start()
        val capturedPublishedList = argumentCaptor<List<Record<String, *>>>()
        whenever(mockPublisher.publish(capturedPublishedList.capture())).doReturn(listOf(CompletableFuture.completedFuture(Unit)))
        whenever(signedMemberships.hashCheck) doReturn SecureHash("algo", ByteBuffer.wrap(byteArrayOf(4, 5, 6)))

        synchronisationService.processMembershipUpdates(updates)

        assertSoftly {
            val request = synchRequest.firstValue
            it.assertThat(request.membersHash).isEqualTo(hash)
            it.assertThat(request.bloomFilter).isNull()
            it.assertThat(request.distributionMetaData.syncRequested).isEqualTo(clock.instant())
        }
    }

    @Test
    fun `startup schedual sync for all the virtual nodes`() {
        val mgm = HoldingIdentity(participantName, GROUP_NAME)
        val records = argumentCaptor<List<Record<*, *>>>()
        whenever(mockPublisher.publish(records.capture())).doReturn(listOf(CompletableFuture.completedFuture(Unit)))
        val captureFactory = argumentCaptor<(String) -> TimerEvent>()
        doNothing().whenever(coordinator).setTimer(any(), any(), captureFactory.capture())
        whenever(locallyHostedMembersReader.readAllLocalMembers()).doReturn(
            listOf(
                LocallyHostedMembersReader.LocallyHostedMember(
                    member, mgm
                )
            )
        )

        postConfigChangedEvent()
        postStartEvent()
        synchronisationService.start()

        val event = captureFactory.firstValue.invoke("")
        lifecycleHandlerCaptor.firstValue.processEvent(event, coordinator)

        assertThat(records.allValues).anySatisfy {
            assertThat(it).hasSize(1)
                .containsExactly(synchronisationRequest)
        }
    }

    @Test
    fun `processMembershipUpdates hash the correct members`() {
        postConfigChangedEvent()
        synchronisationService.start()
        val mgmContextMgm = mock<MGMContext> {
            on {
                parseOrNull(
                    MemberInfoExtension.IS_MGM,
                    Boolean::class.javaObjectType
                )
            } doReturn true
        }
        val mgmInfo = mock<MemberInfo> {
            on { mgmProvidedContext } doReturn mgmContextMgm
        }
        val memberContext = mock<MemberContext> {
            on { parse(MemberInfoExtension.GROUP_ID, String::class.java) } doReturn GROUP_NAME
        }
        val memberInfo = mock<MemberInfo> {
            on { mgmProvidedContext } doReturn mgmProvidedContext
            on { memberProvidedContext } doReturn memberContext
            on { name } doReturn MemberX500Name("Member", "London", "GB")
        }
        whenever(groupReader.lookup()).doReturn(
            listOf(
                mgmInfo,
                memberInfo,
            )
        )

        synchronisationService.processMembershipUpdates(updates)

        verify(
            merkleTreeGenerator
        ).generateTree(
            argThat {
                this.contains(memberInfo) && this.contains(participant) && !this.contains(mgmInfo)
            }
        )
    }

    @Test
    fun `processing of membership updates fails when coordinator is not running`() {
        val ex1 = assertFailsWith<IllegalStateException> {
            synchronisationService.processMembershipUpdates(mock())
        }
        assertThat(ex1.message).isEqualTo("MemberSynchronisationService is currently inactive.")
    }

    @Test
    fun `component handle created on start and closed on stop`() {
        postStartEvent()

        verify(componentHandle, never()).close()
        verify(coordinator).followStatusChangesByName(eq(dependentComponents))

        postStartEvent()

        verify(componentHandle).close()
        verify(coordinator, times(2)).followStatusChangesByName(eq(dependentComponents))

        postStopEvent()
        verify(componentHandle, times(2)).close()
    }

    @Test
    fun `status set to down after stop`() {
        postStopEvent()

        verify(coordinator).updateStatus(eq(LifecycleStatus.DOWN), any())
        verify(componentHandle, never()).close()
        verify(configHandle, never()).close()
        verify(mockPublisher, never()).close()
    }

    @Test
    fun `registration status UP creates config handle and closes it first if it exists`() {
        postStartEvent()
        postRegistrationStatusChangeEvent(LifecycleStatus.UP)

        val configArgs = argumentCaptor<Set<String>>()
        verify(configHandle, never()).close()
        verify(configurationReadService).registerComponentForUpdates(
            eq(coordinator),
            configArgs.capture()
        )
        assertThat(configArgs.firstValue)
            .isEqualTo(setOf(ConfigKeys.BOOT_CONFIG, ConfigKeys.MESSAGING_CONFIG, ConfigKeys.MEMBERSHIP_CONFIG))

        postRegistrationStatusChangeEvent(LifecycleStatus.UP)
        verify(configHandle).close()
        verify(configurationReadService, times(2)).registerComponentForUpdates(eq(coordinator), any())

        postStopEvent()
        verify(configHandle, times(2)).close()
    }

    @Test
    fun `registration status DOWN sets status to DOWN`() {
        postRegistrationStatusChangeEvent(LifecycleStatus.DOWN)

        verify(coordinator).updateStatus(eq(LifecycleStatus.DOWN), any())
    }

    @Test
    fun `registration status ERROR sets status to DOWN`() {
        postRegistrationStatusChangeEvent(LifecycleStatus.ERROR)

        verify(coordinator).updateStatus(eq(LifecycleStatus.DOWN), any())
    }

    @Test
    fun `config changed event creates publisher`() {
        postConfigChangedEvent()

        val configCaptor = argumentCaptor<PublisherConfig>()
        verify(mockPublisher, never()).close()
        verify(publisherFactory).createPublisher(
            configCaptor.capture(),
            any()
        )
        verify(mockPublisher).start()
        verify(coordinator).updateStatus(eq(LifecycleStatus.UP), any())

        with(configCaptor.firstValue) {
            assertThat(clientId).isEqualTo(PUBLISHER_CLIENT_ID)
        }

        postConfigChangedEvent()
        verify(mockPublisher).close()
        verify(publisherFactory, times(2)).createPublisher(
            configCaptor.capture(),
            any()
        )
        verify(mockPublisher, times(2)).start()
        verify(coordinator, times(2)).updateStatus(eq(LifecycleStatus.UP), any())

        postStopEvent()
        verify(mockPublisher, times(3)).close()
    }

    @Nested
    inner class ScheduleSyncTests {
        @Test
        fun `processMembershipUpdates schedule a new request`() {
            val timerDuration = argumentCaptor<Long>()
            doNothing().whenever(coordinator).setTimer(any(), timerDuration.capture(), any())
            postConfigChangedEvent()
            synchronisationService.start()

            synchronisationService.processMembershipUpdates(updates)

            assertThat(timerDuration.firstValue)
                .isLessThanOrEqualTo(10.minutes.toMillis())
                .isGreaterThanOrEqualTo(9.minutes.toMillis())
        }

        @Test
        fun `second processMembershipUpdates will cancel the current schedual`() {
            postConfigChangedEvent()
            synchronisationService.start()
            val captureKey = argumentCaptor<String>()
            doNothing().whenever(coordinator).setTimer(captureKey.capture(), any(), any())

            synchronisationService.processMembershipUpdates(updates)
            synchronisationService.processMembershipUpdates(updates)

            assertThat(captureKey.allValues).hasSize(2).containsExactly(
                "SendSyncRequest-${member.fullHash}",
                "SendSyncRequest-${member.fullHash}",
            )
        }

        @Test
        fun `timer create sync request`() {
            postConfigChangedEvent()
            synchronisationService.start()
            val records = argumentCaptor<List<Record<*, *>>>()
            whenever(mockPublisher.publish(records.capture())).doReturn(listOf(CompletableFuture.completedFuture(Unit)))
            val captureFactory = argumentCaptor<(String) -> TimerEvent>()
            doNothing().whenever(coordinator).setTimer(any(), any(), captureFactory.capture())
            synchronisationService.processMembershipUpdates(updates)
            val event = captureFactory.firstValue.invoke("")

            lifecycleHandlerCaptor.firstValue.processEvent(event, coordinator)

            assertThat(records.allValues).anySatisfy {
                assertThat(it).hasSize(1)
                    .containsExactly(synchronisationRequest)
            }
        }

        @Test
        fun `timer after deactivation will not create sync request`() {
            postConfigChangedEvent()
            synchronisationService.start()
            val records = argumentCaptor<List<Record<*, *>>>()
            whenever(mockPublisher.publish(records.capture())).doReturn(listOf(CompletableFuture.completedFuture(Unit)))
            val captureFactory = argumentCaptor<(String) -> TimerEvent>()
            doNothing().whenever(coordinator).setTimer(any(), any(), captureFactory.capture())
            synchronisationService.processMembershipUpdates(updates)
            val event = captureFactory.firstValue.invoke("")
            lifecycleHandlerCaptor.firstValue.processEvent(StopEvent(), coordinator)

            lifecycleHandlerCaptor.firstValue.processEvent(event, coordinator)

            assertThat(records.allValues).noneSatisfy {
                assertThat(it).hasSize(1)
                    .containsExactly(synchronisationRequest)
            }
        }
    }
}
