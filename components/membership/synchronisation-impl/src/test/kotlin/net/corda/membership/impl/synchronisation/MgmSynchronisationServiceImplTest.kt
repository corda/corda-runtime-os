package net.corda.membership.impl.synchronisation

import net.corda.configuration.read.ConfigChangedEvent
import net.corda.configuration.read.ConfigurationReadService
import net.corda.crypto.client.CryptoOpsClient
import net.corda.crypto.core.toCorda
import net.corda.data.crypto.SecureHash
import net.corda.data.crypto.wire.CryptoSignatureSpec
import net.corda.data.crypto.wire.CryptoSignatureWithKey
import net.corda.data.identity.HoldingIdentity
import net.corda.data.membership.command.synchronisation.SynchronisationMetaData
import net.corda.data.membership.command.synchronisation.mgm.ProcessSyncRequest
import net.corda.data.membership.p2p.DistributionMetaData
import net.corda.data.membership.p2p.MembershipPackage
import net.corda.data.membership.p2p.MembershipSyncRequest
import net.corda.data.p2p.app.AppMessage
import net.corda.data.p2p.app.MembershipStatusFilter
import net.corda.data.sync.BloomFilter
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
import net.corda.membership.lib.InternalGroupParameters
import net.corda.membership.lib.MemberInfoExtension.Companion.MEMBER_STATUS_ACTIVE
import net.corda.membership.lib.MemberInfoExtension.Companion.MEMBER_STATUS_SUSPENDED
import net.corda.membership.lib.MemberInfoExtension.Companion.groupId
import net.corda.membership.lib.MemberInfoExtension.Companion.softwareVersion
import net.corda.membership.lib.MemberInfoExtension.Companion.status
import net.corda.membership.lib.SelfSignedMemberInfo
import net.corda.membership.locally.hosted.identities.LocallyHostedIdentitiesService
import net.corda.membership.p2p.helpers.MembershipPackageFactory
import net.corda.membership.p2p.helpers.MerkleTreeGenerator
import net.corda.membership.p2p.helpers.Signer
import net.corda.membership.p2p.helpers.SignerFactory
import net.corda.membership.persistence.client.MembershipQueryClient
import net.corda.membership.persistence.client.MembershipQueryResult
import net.corda.membership.persistence.client.MembershipQueryResult.QueryException
import net.corda.membership.read.MembershipGroupReader
import net.corda.membership.read.MembershipGroupReaderProvider
import net.corda.messaging.api.records.Record
import net.corda.p2p.messaging.P2pRecordsFactory
import net.corda.p2p.messaging.Subsystem
import net.corda.schema.configuration.ConfigKeys.MEMBERSHIP_CONFIG
import net.corda.test.util.time.TestClock
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.crypto.merkle.MerkleTree
import net.corda.virtualnode.toCorda
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.KArgumentCaptor
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doNothing
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.nio.ByteBuffer
import java.time.Instant
import java.util.UUID
import kotlin.test.assertFailsWith

class MgmSynchronisationServiceImplTest {
    private companion object {
        const val GROUP = "dummy_group"
        const val PERSISTENCE_EXCEPTION = "Persistence exception happened."
        val clock = TestClock(Instant.ofEpochSecond(100))
    }

    private val componentHandle: RegistrationHandle = mock()
    private val dependentComponents = setOf(
        LifecycleCoordinatorName.forComponent<ConfigurationReadService>(),
        LifecycleCoordinatorName.forComponent<CryptoOpsClient>(),
        LifecycleCoordinatorName.forComponent<MembershipQueryClient>(),
        LifecycleCoordinatorName.forComponent<MembershipGroupReaderProvider>(),
        LifecycleCoordinatorName.forComponent<LocallyHostedIdentitiesService>(),
    )
    private var coordinatorIsRunning = false
    private var coordinatorStatus: KArgumentCaptor<LifecycleStatus> = argumentCaptor()
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
    private val coordinatorFactory: LifecycleCoordinatorFactory = mock {
        on { createCoordinator(any(), lifecycleHandlerCaptor.capture()) } doReturn coordinator
    }

    private val configHandle: Resource = mock()
    private val configurationReadService: ConfigurationReadService = mock {
        on { registerComponentForUpdates(eq(coordinator), any()) } doReturn configHandle
    }

    private val membershipConfig = mock<SmartConfig> {
        on { getIsNull(any()) } doReturn false
        on { getLong(any()) } doReturn 3
    }

    private val aliceName = "C=GB, L=London, O=Alice"
    private val alice = HoldingIdentity(aliceName, GROUP)
    private val bobName = "C=GB, L=London, O=Bob"
    private val bob = HoldingIdentity(bobName, GROUP)
    private val charlieName = "C=GB, L=London, O=Charlie"
    private val charlie = HoldingIdentity(charlieName, GROUP)
    private val daisyName = "C=GB, L=London, O=Daisy"
    private val daisy = HoldingIdentity(daisyName, GROUP)
    private val mgmName = "C=GB, L=London, O=MGM"
    private val mgm = HoldingIdentity(mgmName, GROUP)
    private val simonName = "C=GB, L=London, O=Simon"
    private val simon = HoldingIdentity(simonName, GROUP)

    private val mgmInfo = createSignedMemberInfo(mgmName)
    private val aliceInfo = createSignedMemberInfo(aliceName)
    private val bobInfo = createSignedMemberInfo(bobName)
    private val daisyInfo = createSignedMemberInfo(daisyName)
    private val simonInfo = createSignedMemberInfo(simonName, membershipStatus = MEMBER_STATUS_SUSPENDED)

    private val memberInfosWithoutMgm = listOf(aliceInfo, bobInfo, daisyInfo)
    private val groupParameters: InternalGroupParameters = mock()
    private val groupReader: MembershipGroupReader = mock {
        on { lookup(eq(MemberX500Name.parse(mgmName)), any()) } doReturn mgmInfo
        on { groupParameters } doReturn groupParameters
    }
    private val groupReaderProvider: MembershipGroupReaderProvider = mock {
        on { getGroupReader(eq(mgm.toCorda())) } doReturn groupReader
    }

    private val syncId = UUID.randomUUID().toString()
    private val byteBuffer = "1234".toByteBuffer()
    private val secureHash = createSecureHash("algorithm1")
    private val matchingMerkleTree: MerkleTree = mock {
        on { root } doReturn secureHash.toCorda()
    }
    private val nonMatchingMerkleTree: MerkleTree = mock {
        on { root } doReturn createSecureHash("algorithm2").toCorda()
    }
    private val merkleTreeGenerator: MerkleTreeGenerator = mock {
        on { generateTreeUsingSignedMembers(argThat { contains(aliceInfo) && size == 1 }) } doReturn matchingMerkleTree
        on { generateTreeUsingSignedMembers(argThat { contains(simonInfo) && size == 1 }) } doReturn matchingMerkleTree
        on { generateTreeUsingSignedMembers(argThat { contains(bobInfo) && size == 1 }) } doReturn nonMatchingMerkleTree
        on { generateTreeUsingSignedMembers(argThat { contains(daisyInfo) && size == 1 }) } doReturn nonMatchingMerkleTree
        on {
            generateTreeUsingSignedMembers(
                argThat { containsAll(memberInfosWithoutMgm) && size == memberInfosWithoutMgm.size }
            )
        } doReturn matchingMerkleTree
    }
    private val membershipQueryClient: MembershipQueryClient = mock {
        on {
            queryMemberInfo(eq(mgm.toCorda()), eq(listOf(alice.toCorda())), eq(listOf(MEMBER_STATUS_ACTIVE, MEMBER_STATUS_SUSPENDED)))
        } doReturn MembershipQueryResult.Success(
            listOf(aliceInfo)
        )
        on {
            queryMemberInfo(eq(mgm.toCorda()), eq(listOf(bob.toCorda())), eq(listOf(MEMBER_STATUS_ACTIVE, MEMBER_STATUS_SUSPENDED)))
        } doReturn MembershipQueryResult.Success(
            listOf(bobInfo)
        )
        on {
            queryMemberInfo(eq(mgm.toCorda()), eq(listOf(simon.toCorda())), eq(listOf(MEMBER_STATUS_ACTIVE, MEMBER_STATUS_SUSPENDED)))
        } doReturn MembershipQueryResult.Success(
            listOf(simonInfo)
        )
        on {
            queryMemberInfo(eq(mgm.toCorda()), eq(listOf(daisy.toCorda())), eq(listOf(MEMBER_STATUS_ACTIVE, MEMBER_STATUS_SUSPENDED)))
        } doReturn MembershipQueryResult.Failure(
            PERSISTENCE_EXCEPTION
        )
        on {
            queryMemberInfo(eq(mgm.toCorda()), eq(listOf(charlie.toCorda())), eq(listOf(MEMBER_STATUS_ACTIVE, MEMBER_STATUS_SUSPENDED)))
        } doReturn MembershipQueryResult.Success(
            emptyList()
        )
        on {
            queryMemberInfo(eq(mgm.toCorda()), eq(listOf(MEMBER_STATUS_ACTIVE, MEMBER_STATUS_SUSPENDED)))
        } doReturn MembershipQueryResult.Success(
            memberInfosWithoutMgm
        )
    }

    private val signer = mock<Signer>()
    private val signerFactory = mock<SignerFactory> {
        on { createSigner(mgmInfo) } doReturn signer
    }

    private val allMembershipPackage = mock<MembershipPackage>()
    private val bobMembershipPackage = mock<MembershipPackage>()
    private val simonMembershipPackage = mock<MembershipPackage>()
    private val membershipPackageFactory = mock<MembershipPackageFactory> {
        on {
            createMembershipPackage(
                eq(signer),
                eq(memberInfosWithoutMgm),
                any(),
                any(),
            )
        } doReturn allMembershipPackage
        on {
            createMembershipPackage(
                eq(signer),
                eq(listOf(bobInfo)),
                any(),
                any(),
            )
        } doReturn bobMembershipPackage
        on {
            createMembershipPackage(
                eq(signer),
                eq(listOf(simonInfo)),
                any(),
                any(),
            )
        } doReturn simonMembershipPackage
    }

    private val allMembershipPackageRecord = mock<Record<String, AppMessage>>()
    private val bobMembershipPackageRecord = mock<Record<String, AppMessage>>()
    private val simonMembershipPackageRecord = mock<Record<String, AppMessage>>()
    private val membershipP2PRecordsFactory = mock<P2pRecordsFactory> {
        on {
            createAuthenticatedMessageRecord(
                any(),
                any(),
                eq(allMembershipPackage),
                eq(Subsystem.MEMBERSHIP),
                any(),
                any(),
                any(),
                eq(MembershipStatusFilter.ACTIVE_OR_SUSPENDED),
            )
        } doReturn allMembershipPackageRecord
        on {
            createAuthenticatedMessageRecord(
                any(),
                any(),
                eq(bobMembershipPackage),
                eq(Subsystem.MEMBERSHIP),
                any(),
                any(),
                any(),
                eq(MembershipStatusFilter.ACTIVE_OR_SUSPENDED),
            )
        } doReturn bobMembershipPackageRecord
        on {
            createAuthenticatedMessageRecord(
                any(),
                any(),
                eq(simonMembershipPackage),
                eq(Subsystem.MEMBERSHIP),
                any(),
                any(),
                any(),
                eq(MembershipStatusFilter.ACTIVE_OR_SUSPENDED),
            )
        } doReturn simonMembershipPackageRecord
    }
    private val services = mock<MgmSynchronisationServiceImpl.InjectedServices> {
        on { coordinatorFactory } doReturn coordinatorFactory
        on { configurationReadService } doReturn configurationReadService
        on { membershipGroupReaderProvider } doReturn groupReaderProvider
        on { membershipQueryClient } doReturn membershipQueryClient
        on { merkleTreeGenerator } doReturn merkleTreeGenerator
        on { membershipPackageFactory } doReturn membershipPackageFactory
        on { signerFactory } doReturn signerFactory
        on { membershipP2PRecordsFactory } doReturn membershipP2PRecordsFactory
    }

    private val synchronisationService = MgmSynchronisationServiceImpl(
        services,
    )

    private fun String.toByteBuffer() = ByteBuffer.wrap(toByteArray())

    private fun createSecureHash(algorithm: String) = SecureHash(algorithm, byteBuffer)

    private fun createRequest(member: HoldingIdentity) = ProcessSyncRequest(
        SynchronisationMetaData(
            mgm,
            member
        ),
        MembershipSyncRequest(
            DistributionMetaData(
                syncId,
                clock.instant()
            ),
            secureHash,
            BloomFilter(1, 1, 1, byteBuffer),
            secureHash,
            secureHash
        )
    )

    private fun createSignedMemberInfo(memberName: String, membershipStatus: String = MEMBER_STATUS_ACTIVE): SelfSignedMemberInfo {
        val memberBytes = "member-$memberName".toByteArray()
        val mgmBytes = "mgm-$memberName".toByteArray()
        return mock {
            on { memberProvidedContext } doReturn mock()
            on { mgmProvidedContext } doReturn mock()
            on { groupId } doReturn GROUP
            on { name } doReturn MemberX500Name.parse(memberName)
            on { status } doReturn membershipStatus
            on { platformVersion } doReturn 5000
            on { softwareVersion } doReturn "5.0.0"
            on { memberContextBytes } doReturn memberBytes
            on { mgmContextBytes } doReturn mgmBytes
            on { memberSignature } doReturn CryptoSignatureWithKey(
                ByteBuffer.wrap("pk-$memberName".toByteArray()),
                ByteBuffer.wrap("sig-$memberName".toByteArray()),
            )
            on { memberSignatureSpec } doReturn CryptoSignatureSpec("dummy", null, null)
        }
    }

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
                setOf(MEMBERSHIP_CONFIG),
                mapOf(
                    MEMBERSHIP_CONFIG to membershipConfig,
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
            .isEqualTo(setOf(MEMBERSHIP_CONFIG))

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
    fun `processing requests fails when component is not running`() {
        val ex = assertFailsWith<IllegalStateException> { synchronisationService.processSyncRequest(mock()) }
        assertThat(ex.message).isEqualTo("MgmSynchronisationService is currently inactive.")
    }

    @Test
    fun `all members are sent when member hash is matching`() {
        postConfigChangedEvent()
        synchronisationService.start()
        val capturedList = argumentCaptor<List<SelfSignedMemberInfo>>()
        val request = createRequest(alice)
        val records = synchronisationService.processSyncRequest(request)
        verify(membershipPackageFactory, times(1)).createMembershipPackage(
            any(),
            capturedList.capture(),
            any(),
            eq(groupParameters)
        )
        assertThat(records).containsExactly(allMembershipPackageRecord)
        val membersPublished = capturedList.firstValue
        assertThat(membersPublished.size).isEqualTo(3)
        assertThat(membersPublished).isEqualTo(memberInfosWithoutMgm)
        synchronisationService.stop()
    }

    @Test
    fun `only the requesting member's info is sent when member hash is not matching`() {
        postConfigChangedEvent()
        synchronisationService.start()
        val capturedList = argumentCaptor<List<SelfSignedMemberInfo>>()
        val request = createRequest(bob)
        val records = synchronisationService.processSyncRequest(request)
        verify(membershipPackageFactory, times(1)).createMembershipPackage(
            any(),
            capturedList.capture(),
            any(),
            eq(groupParameters)
        )
        assertThat(records).containsExactly(bobMembershipPackageRecord)
        val membersPublished = capturedList.firstValue
        assertThat(membersPublished.size).isEqualTo(1)
        assertThat(membersPublished.first()).isEqualTo(bobInfo)
        synchronisationService.stop()
    }

    @Test
    fun `only the requesting member's info is sent when member is suspended`() {
        postConfigChangedEvent()
        synchronisationService.start()
        val capturedList = argumentCaptor<List<SelfSignedMemberInfo>>()
        val request = createRequest(simon)
        val records = synchronisationService.processSyncRequest(request)
        verify(membershipPackageFactory, times(1)).createMembershipPackage(
            any(),
            capturedList.capture(),
            any(),
            eq(groupParameters)
        )
        assertThat(records).containsExactly(simonMembershipPackageRecord)
        val membersPublished = capturedList.firstValue
        assertThat(membersPublished.size).isEqualTo(1)
        assertThat(membersPublished.first()).isEqualTo(simonInfo)
        synchronisationService.stop()
    }

    @Test
    fun `exception is thrown when requester is not part of the group`() {
        postConfigChangedEvent()
        synchronisationService.start()
        val ex = assertFailsWith<CordaRuntimeException> {
            synchronisationService.processSyncRequest(createRequest(charlie))
        }
        assertThat(ex.message).isEqualTo("Requester $charlieName is not part of the membership group!")
        synchronisationService.stop()
    }

    @Test
    fun `exception is thrown when member signatures cannot be found`() {
        postConfigChangedEvent()
        synchronisationService.start()
        val ex = assertFailsWith<QueryException> {
            synchronisationService.processSyncRequest(createRequest(daisy))
        }
        assertThat(ex.message).isEqualTo(PERSISTENCE_EXCEPTION)
        synchronisationService.stop()
    }
}
