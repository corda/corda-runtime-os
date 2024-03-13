package net.corda.membership.impl.synchronisation

import com.typesafe.config.ConfigFactory
import net.corda.configuration.read.ConfigChangedEvent
import net.corda.configuration.read.ConfigurationReadService
import net.corda.crypto.core.toCorda
import net.corda.data.crypto.SecureHash
import net.corda.data.crypto.wire.CryptoSignatureSpec
import net.corda.data.crypto.wire.CryptoSignatureWithKey
import net.corda.data.membership.PersistentMemberInfo
import net.corda.data.membership.SignedData
import net.corda.data.membership.SignedMemberInfo
import net.corda.data.membership.command.synchronisation.SynchronisationMetaData
import net.corda.data.membership.command.synchronisation.member.ProcessMembershipUpdates
import net.corda.data.membership.p2p.MembershipPackage
import net.corda.data.membership.p2p.MembershipSyncRequest
import net.corda.data.membership.p2p.SignedMemberships
import net.corda.data.p2p.app.AppMessage
import net.corda.data.p2p.app.MembershipStatusFilter
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
import net.corda.membership.lib.GroupParametersFactory
import net.corda.membership.lib.InternalGroupParameters
import net.corda.membership.lib.MemberInfoExtension.Companion.GROUP_ID
import net.corda.membership.lib.MemberInfoExtension.Companion.IS_MGM
import net.corda.membership.lib.MemberInfoExtension.Companion.MEMBER_STATUS_SUSPENDED
import net.corda.membership.lib.MemberInfoExtension.Companion.PARTY_NAME
import net.corda.membership.lib.MemberInfoExtension.Companion.SESSION_KEYS
import net.corda.membership.lib.MemberInfoExtension.Companion.groupId
import net.corda.membership.lib.MemberInfoExtension.Companion.id
import net.corda.membership.lib.MemberInfoExtension.Companion.sessionInitiationKeys
import net.corda.membership.lib.MemberInfoExtension.Companion.status
import net.corda.membership.lib.MemberInfoFactory
import net.corda.membership.lib.SelfSignedMemberInfo
import net.corda.membership.lib.SignedGroupParameters
import net.corda.membership.p2p.helpers.MembershipP2pRecordsFactory
import net.corda.membership.p2p.helpers.MerkleTreeGenerator
import net.corda.membership.p2p.helpers.Verifier
import net.corda.membership.persistence.client.MembershipPersistenceClient
import net.corda.membership.persistence.client.MembershipPersistenceOperation
import net.corda.membership.read.MembershipGroupReader
import net.corda.membership.read.MembershipGroupReaderProvider
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.records.Record
import net.corda.schema.Schemas.Membership.MEMBER_LIST_TOPIC
import net.corda.schema.configuration.ConfigKeys
import net.corda.schema.configuration.MembershipConfig
import net.corda.test.util.time.TestClock
import net.corda.utilities.minutes
import net.corda.utilities.parseOrNull
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.crypto.merkle.MerkleTree
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
import java.security.PublicKey
import java.time.Instant
import java.util.concurrent.CompletableFuture
import kotlin.test.assertFailsWith
import net.corda.data.membership.SignedGroupParameters as AvroGroupParameters

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
        LifecycleCoordinatorName.forComponent<MembershipGroupReaderProvider>(),
        LifecycleCoordinatorName.forComponent<MembershipPersistenceClient>(),
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
    private val participantMemberProvidedContext = mock<MemberContext> {
        on { entries } doReturn mapOf(PARTY_NAME to participantName.toString()).entries
    }
    private val participantMgmProvidedContext = mock<MGMContext> {
        on { entries } doReturn emptySet()
    }
    private val memberSignature = mock<CryptoSignatureWithKey>()
    private val memberSignatureSpec = mock<CryptoSignatureSpec>()
    private val participant: SelfSignedMemberInfo = mock {
        on { memberProvidedContext } doReturn participantMemberProvidedContext
        on { mgmProvidedContext } doReturn participantMgmProvidedContext
        on { memberContextBytes } doReturn MEMBER_CONTEXT_BYTES
        on { mgmContextBytes } doReturn MGM_CONTEXT_BYTES
        on { memberSignature } doReturn memberSignature
        on { memberSignatureSpec } doReturn memberSignatureSpec
        on { name } doReturn participantName
        on { groupId } doReturn GROUP_NAME
    }
    private val memberName = MemberX500Name("Alice", "London", "GB")
    private val member = HoldingIdentity(memberName, GROUP_NAME)
    private val memberContextData: ByteBuffer = mock {
        on { array() } doReturn MEMBER_CONTEXT_BYTES
    }
    private val mgmContextData: ByteBuffer = mock {
        on { array() } doReturn MGM_CONTEXT_BYTES
    }
    private val mgmSignature = mock<CryptoSignatureWithKey>()
    private val mgmSignatureSpec = mock<CryptoSignatureSpec>()
    private val signedMemberContext = SignedData(
        memberContextData,
        memberSignature,
        memberSignatureSpec
    )
    private val signedMgmContext = SignedData(
        mgmContextData,
        mgmSignature,
        mgmSignatureSpec
    )
    private val signedMemberInfo: SignedMemberInfo = mock {
        on { memberContext } doReturn signedMemberContext
        on { mgmContext } doReturn signedMgmContext
    }
    private val hash = SecureHash("algo", ByteBuffer.wrap(byteArrayOf(1, 2, 3)))
    private val signedMemberships: SignedMemberships = mock {
        on { memberships } doReturn listOf(signedMemberInfo)
        on { hashCheck } doReturn hash
    }
    private val mgmSignatureGroupParameters = mock<CryptoSignatureWithKey>()
    private val signedGroupParameters = mock<AvroGroupParameters> {
        on { mgmSignature } doReturn mgmSignatureGroupParameters
        on { mgmSignatureSpec } doReturn mgmSignatureSpec
        on { groupParameters } doReturn ByteBuffer.wrap(GROUP_PARAMETERS_BYTES)
    }
    private val membershipPackage: MembershipPackage = mock {
        on { memberships } doReturn signedMemberships
        on { groupParameters } doReturn signedGroupParameters
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
    private val membershipP2PRecordsFactory = mock<MembershipP2pRecordsFactory> {
        on {
            createAuthenticatedMessageRecord(
                eq(member.toAvro()),
                eq(HoldingIdentity(participantName, GROUP_NAME).toAvro()),
                synchRequest.capture(),
                isNull(),
                any(),
                eq(MembershipStatusFilter.ACTIVE),
            )
        } doReturn synchronisationRequest
    }
    private val tree = mock<MerkleTree> {
        on { root } doReturn hash.toCorda()
    }
    private val merkleTreeGenerator = mock<MerkleTreeGenerator> {
        on { generateTreeUsingMembers(any()) } doReturn tree
        on { createTree(any()) } doReturn tree
    }
    private val memberMgmContext = mock<MGMContext> {
        on { parseOrNull<Boolean>(IS_MGM) } doReturn null
    }
    private val memberMemberContext = mock<MemberContext> {
        on { parse(GROUP_ID, String::class.java) } doReturn GROUP_NAME
    }
    private val memberInfo = mock<SelfSignedMemberInfo> {
        on { mgmProvidedContext } doReturn memberMgmContext
        on { memberProvidedContext } doReturn memberMemberContext
        on { name } doReturn MemberX500Name.parse("O=Alice, L=London, C=GB")
    }
    private val mgmMgmContext = mock<MGMContext> {
        on { parseOrNull<Boolean>(IS_MGM) } doReturn true
    }
    private val mgmMemberContext = mock<MemberContext> {
        on { parse(GROUP_ID, String::class.java) } doReturn GROUP_NAME
        on { parseList(SESSION_KEYS, PublicKey::class.java) } doReturn listOf(mock())
    }
    private val mgmInfo = mock<SelfSignedMemberInfo> {
        on { name } doReturn MemberX500Name.parse("O=MGM, L=London, C=GB")
        on { mgmProvidedContext } doReturn mgmMgmContext
        on { memberProvidedContext } doReturn mgmMemberContext
    }
    private val persistentParticipant = mock<PersistentMemberInfo> {
        on { signedMemberContext } doReturn signedMemberContext
        on { serializedMgmContext } doReturn mgmContextData
    }
    private val memberInfoFactory: MemberInfoFactory = mock {
        on { createSelfSignedMemberInfo(any(), any(), any(), any()) } doReturn participant
        on { createPersistentMemberInfo(any(), any(), any(), any(), any()) } doReturn persistentParticipant
    }
    private val groupReader = mock<MembershipGroupReader> {
        on { lookup() } doReturn listOf(memberInfo, mgmInfo)
        on { lookup(name = any(), filter = any()) } doReturn memberInfo
    }
    private val groupReaderProvider = mock<MembershipGroupReaderProvider> {
        on { getGroupReader(member) } doReturn groupReader
    }
    private val locallyHostedMembersReader = mock<LocallyHostedMembersReader> {
        on { readAllLocalMembers() } doReturn emptyList()
    }
    private val clock = TestClock(Instant.ofEpochSecond(100))
    private val verifier = mock<Verifier>()
    private val persistGroupParametersRecords = listOf(mock<Record<*, *>>())
    private val persistGroupParametersOperation = mock<MembershipPersistenceOperation<InternalGroupParameters>> {
        on { createAsyncCommands() } doReturn persistGroupParametersRecords
    }
    private val persistenceClient = mock<MembershipPersistenceClient> {
        on { persistGroupParameters(any(), any()) } doReturn persistGroupParametersOperation
    }
    private val groupParameters = mock<SignedGroupParameters>()
    private val groupParametersFactory = mock<GroupParametersFactory> {
        on { create(any<AvroGroupParameters>()) } doReturn groupParameters
    }
    private val synchronisationService = MemberSynchronisationServiceImpl(
        MemberSynchronisationServiceImpl.Services(
            publisherFactory,
            configurationReadService,
            memberInfoFactory,
            groupReaderProvider,
            verifier,
            locallyHostedMembersReader,
            membershipP2PRecordsFactory,
            merkleTreeGenerator,
            clock,
            persistenceClient,
            groupParametersFactory,
        ),
        lifecycleCoordinatorFactory,
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

        val producedRecords = synchronisationService.processMembershipUpdates(updates)

        assertSoftly {
            assertThat(producedRecords).hasSize(2)

            val publishedPersistentMemberInfo = producedRecords.first()
            it.assertThat(publishedPersistentMemberInfo.topic).isEqualTo(MEMBER_LIST_TOPIC)
            it.assertThat(publishedPersistentMemberInfo.key).isEqualTo("${member.shortHash}-${participant.id}")
            it.assertThat(publishedPersistentMemberInfo.value).isInstanceOf(PersistentMemberInfo::class.java)
            val value = publishedPersistentMemberInfo.value as? PersistentMemberInfo
            assertThat(value).isEqualTo(persistentParticipant)
            assertThat(value?.signedMemberContext).isEqualTo(signedMemberContext)
            assertThat(value?.serializedMgmContext).isEqualTo(mgmContextData)
        }
    }

    @Test
    fun `group parameters are successfully persisted on receiving membership package from MGM`() {
        whenever(membershipPackage.memberships).doReturn(null)
        postConfigChangedEvent()
        synchronisationService.start()

        val records = synchronisationService.processMembershipUpdates(updates)

        assertThat(records).containsAll(persistGroupParametersRecords)
    }

    @Test
    fun `failed member signature verification will ask for sync again`() {
        whenever(
            verifier.verify(
                eq(memberInfo.sessionInitiationKeys),
                eq(memberSignature),
                any(),
                any()
            )
        ).thenThrow(CordaRuntimeException("Mock failure"))
        postConfigChangedEvent()
        synchronisationService.start()

        val records = synchronisationService.processMembershipUpdates(updates)

        assertThat(records).containsExactly(synchronisationRequest)
    }

    @Test
    fun `failed MGM signature verification will ask for sync again`() {
        whenever(
            verifier.verify(
                eq(mgmInfo.sessionInitiationKeys),
                eq(mgmSignature),
                any(),
                any()
            )
        ).thenThrow(CordaRuntimeException("Mock failure"))
        postConfigChangedEvent()
        synchronisationService.start()

        val records = synchronisationService.processMembershipUpdates(updates)

        assertThat(records).containsExactly(synchronisationRequest)
    }

    @Test
    fun `failed MGM signature verification and create sync request will not return anything`() {
        whenever(
            verifier.verify(
                eq(mgmInfo.sessionInitiationKeys),
                eq(mgmSignature),
                any(),
                any()
            )
        ).thenThrow(CordaRuntimeException("Mock failure"))
        whenever(groupReader.lookup(any(), any())).doReturn(null)
        postConfigChangedEvent()
        synchronisationService.start()

        val records = synchronisationService.processMembershipUpdates(updates)

        assertThat(records).isEmpty()
    }

    @Test
    fun `failed MGM signature verification and create sync request will schedule another request`() {
        val captureDelay = argumentCaptor<Long>()
        doNothing().whenever(coordinator).setTimer(any(), captureDelay.capture(), any())
        whenever(
            verifier.verify(
                eq(mgmInfo.sessionInitiationKeys),
                eq(mgmSignature),
                any(),
                any()
            )
        ).thenThrow(CordaRuntimeException("Mock failure"))
        whenever(groupReader.lookup(any(), any())).doReturn(null)
        postConfigChangedEvent()
        synchronisationService.start()

        synchronisationService.processMembershipUpdates(updates)

        assertThat(captureDelay.lastValue).isEqualTo(5 * 1000 * 60)
    }

    @Test
    fun `failed MGM signature verification will not persist the group parameters`() {
        whenever(
            verifier.verify(
                any(),
                eq(mgmSignatureGroupParameters),
                any(),
                any()
            )
        ).thenThrow(CordaRuntimeException("Mock failure"))
        postConfigChangedEvent()
        synchronisationService.start()

        synchronisationService.processMembershipUpdates(updates)

        verify(persistenceClient, never()).persistGroupParameters(any(), any())
    }

    @Test
    fun `verification is called with the correct data on receiving membership package from MGM`() {
        postConfigChangedEvent()
        synchronisationService.start()

        synchronisationService.processMembershipUpdates(updates)

        verify(verifier).verify(memberInfo.sessionInitiationKeys, memberSignature, memberSignatureSpec, MEMBER_CONTEXT_BYTES)
        verify(verifier).verify(mgmInfo.sessionInitiationKeys, mgmSignature, mgmSignatureSpec, byteArrayOf(1, 2, 3))
    }

    @Test
    fun `processMembershipUpdates asks for synchronization if hash is empty`() {
        postConfigChangedEvent()
        synchronisationService.start()
        whenever(signedMemberships.hashCheck) doReturn null

        val producedRecords = synchronisationService.processMembershipUpdates(updates)

        assertSoftly {
            it.assertThat(producedRecords)
                .hasSize(3)
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
        whenever(signedMemberships.hashCheck) doReturn SecureHash("algo", ByteBuffer.wrap(byteArrayOf(4, 5, 6)))

        val published = synchronisationService.processMembershipUpdates(updates)

        assertSoftly {
            it.assertThat(published)
                .hasSize(3)
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
                    member,
                    mgm
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
                    IS_MGM,
                    Boolean::class.javaObjectType
                )
            } doReturn true
        }
        val mgmInfo = mock<MemberInfo> {
            on { mgmProvidedContext } doReturn mgmContextMgm
        }
        val memberContext = mock<MemberContext> {
            on { parse(GROUP_ID, String::class.java) } doReturn GROUP_NAME
        }
        val memberInfo = mock<MemberInfo> {
            on { mgmProvidedContext } doReturn participantMgmProvidedContext
            on { memberProvidedContext } doReturn memberContext
            on { name } doReturn MemberX500Name("Member", "London", "GB")
        }
        whenever(groupReader.lookup(filter = MembershipStatusFilter.ACTIVE_OR_SUSPENDED)).doReturn(
            listOf(
                mgmInfo,
                memberInfo,
            )
        )

        synchronisationService.processMembershipUpdates(updates)

        verify(
            merkleTreeGenerator
        ).generateTreeUsingMembers(
            argThat {
                this.contains(memberInfo) && this.contains(participant) && !this.contains(mgmInfo)
            }
        )
    }

    @Test
    fun `processMembershipUpdates hashes the correct members if the viewOwningMember is suspended`() {
        postConfigChangedEvent()
        synchronisationService.start()
        whenever(participant.status).doReturn(MEMBER_STATUS_SUSPENDED)
        whenever(participant.name).doReturn(member.x500Name)

        synchronisationService.processMembershipUpdates(updates)

        verify(
            merkleTreeGenerator
        ).generateTreeUsingMembers(
            listOf(participant)
        )
    }

    @Test
    fun `processMembershipUpdates hashes the correct members if the viewOwningMember is suspended and not in the update`() {
        postConfigChangedEvent()
        synchronisationService.start()
        whenever(memberInfo.status).thenReturn(MEMBER_STATUS_SUSPENDED)
        whenever(groupReader.lookup(filter = MembershipStatusFilter.ACTIVE_OR_SUSPENDED)).doReturn(
            listOf(
                mgmInfo,
                memberInfo,
            )
        )

        synchronisationService.processMembershipUpdates(updates)

        verify(
            merkleTreeGenerator
        ).generateTreeUsingMembers(
            listOf(memberInfo)
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
