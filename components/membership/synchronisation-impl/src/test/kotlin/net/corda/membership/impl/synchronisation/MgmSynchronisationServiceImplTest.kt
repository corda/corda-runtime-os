package net.corda.membership.impl.synchronisation

import com.typesafe.config.ConfigFactory
import net.corda.crypto.core.toCorda
import net.corda.configuration.read.ConfigChangedEvent
import net.corda.configuration.read.ConfigurationReadService
import net.corda.crypto.client.CryptoOpsClient
import net.corda.data.KeyValuePair
import net.corda.data.KeyValuePairList
import net.corda.data.crypto.SecureHash
import net.corda.data.crypto.wire.CryptoSignatureSpec
import net.corda.data.crypto.wire.CryptoSignatureWithKey
import net.corda.data.identity.HoldingIdentity
import net.corda.data.membership.command.synchronisation.SynchronisationMetaData
import net.corda.data.membership.command.synchronisation.mgm.ProcessSyncRequest
import net.corda.data.membership.p2p.DistributionMetaData
import net.corda.data.membership.p2p.MembershipPackage
import net.corda.data.membership.p2p.MembershipSyncRequest
import net.corda.data.sync.BloomFilter
import net.corda.layeredpropertymap.testkit.LayeredPropertyMapMocks
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
import net.corda.membership.lib.MemberInfoExtension.Companion.GROUP_ID
import net.corda.membership.lib.MemberInfoExtension.Companion.MEMBER_STATUS_ACTIVE
import net.corda.membership.lib.MemberInfoExtension.Companion.PARTY_NAME
import net.corda.membership.lib.MemberInfoExtension.Companion.PLATFORM_VERSION
import net.corda.membership.lib.MemberInfoExtension.Companion.PROTOCOL_VERSION
import net.corda.membership.lib.MemberInfoExtension.Companion.SOFTWARE_VERSION
import net.corda.membership.lib.MemberInfoExtension.Companion.STATUS
import net.corda.membership.lib.MemberInfoExtension.Companion.URL_KEY
import net.corda.membership.lib.MemberInfoExtension.Companion.holdingIdentity
import net.corda.membership.lib.impl.MemberInfoFactoryImpl
import net.corda.membership.lib.impl.converter.EndpointInfoConverter
import net.corda.membership.p2p.helpers.MembershipPackageFactory
import net.corda.membership.p2p.helpers.MerkleTreeGenerator
import net.corda.membership.p2p.helpers.P2pRecordsFactory
import net.corda.membership.p2p.helpers.Signer
import net.corda.membership.p2p.helpers.SignerFactory
import net.corda.membership.persistence.client.MembershipQueryClient
import net.corda.membership.persistence.client.MembershipQueryResult
import net.corda.membership.persistence.client.MembershipQueryResult.QueryException
import net.corda.membership.read.MembershipGroupReader
import net.corda.membership.read.MembershipGroupReaderProvider
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.records.Record
import net.corda.data.p2p.app.AppMessage
import net.corda.data.p2p.app.MembershipStatusFilter
import net.corda.schema.configuration.ConfigKeys.BOOT_CONFIG
import net.corda.schema.configuration.ConfigKeys.MEMBERSHIP_CONFIG
import net.corda.schema.configuration.ConfigKeys.MESSAGING_CONFIG
import net.corda.test.util.time.TestClock
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.crypto.merkle.MerkleTree
import net.corda.v5.membership.GroupParameters
import net.corda.v5.membership.MemberInfo
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
import java.util.*
import java.util.concurrent.CompletableFuture
import kotlin.test.assertFailsWith

class MgmSynchronisationServiceImplTest {
    private companion object {
        const val GROUP = "dummy_group"
        const val PUBLISHER_CLIENT_ID = "mgm-synchronisation-service"
        const val PERSISTENCE_EXCEPTION = "Persistence exception happened."
        val clock = TestClock(Instant.ofEpochSecond(100))
    }
    private val mockPublisher = mock<Publisher>().apply {
        whenever(publish(any())).thenReturn(listOf(CompletableFuture.completedFuture(Unit)))
    }
    private val publisherFactory: PublisherFactory = mock {
        on { createPublisher(any(), any()) } doReturn mockPublisher
    }

    private val componentHandle: RegistrationHandle = mock()
    private val dependentComponents = setOf(
        LifecycleCoordinatorName.forComponent<ConfigurationReadService>(),
        LifecycleCoordinatorName.forComponent<CryptoOpsClient>(),
        LifecycleCoordinatorName.forComponent<MembershipQueryClient>(),
        LifecycleCoordinatorName.forComponent<MembershipGroupReaderProvider>(),
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

    private val testConfig =
        SmartConfigFactory.createWithoutSecurityServices().create(ConfigFactory.parseString("instanceId=1"))
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

    private val memberInfoFactory = MemberInfoFactoryImpl(
        LayeredPropertyMapMocks.createFactory(listOf(EndpointInfoConverter()))
    )

    private val mgmInfo = createMemberInfo(mgmName)
    private val aliceInfo = createMemberInfo(aliceName)
    private val bobInfo = createMemberInfo(bobName)
    private val daisyInfo = createMemberInfo(daisyName)

    private val memberInfos = listOf(mgmInfo, aliceInfo, bobInfo, daisyInfo)
    private val memberInfosWithoutMgm = listOf(aliceInfo, bobInfo, daisyInfo)
    private val groupParameters: GroupParameters = mock()
    private val groupReader: MembershipGroupReader = mock {
        on { lookup() } doReturn memberInfos
        on { lookup(eq(MemberX500Name.parse(mgmName)), any()) } doReturn mgmInfo
        on { lookup(eq(MemberX500Name.parse(aliceName)), any()) } doReturn aliceInfo
        on { lookup(eq(MemberX500Name.parse(bobName)), any()) } doReturn bobInfo
        on { lookup(eq(MemberX500Name.parse(daisyName)), any()) } doReturn daisyInfo
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
        on { generateTree(argThat { contains(aliceInfo) && size == 1 }) } doReturn matchingMerkleTree
        on { generateTree(argThat { contains(bobInfo) && size == 1 }) } doReturn nonMatchingMerkleTree
        on { generateTree(argThat { contains(daisyInfo) && size == 1 }) } doReturn nonMatchingMerkleTree
        on { generateTree(
            argThat { containsAll(memberInfosWithoutMgm) && size == memberInfosWithoutMgm.size }
        ) } doReturn matchingMerkleTree
    }
    private val signatures = createSignatures(memberInfosWithoutMgm)
    private val signature = createSignatures(listOf(bobInfo))
    private val membershipQueryClient: MembershipQueryClient = mock {
        on {
            queryMembersSignatures(eq(mgm.toCorda()), eq(listOf(bob.toCorda())))
        } doReturn MembershipQueryResult.Success(
            signature
        )
        on {
            queryMembersSignatures(eq(mgm.toCorda()), eq(listOf(daisy.toCorda())))
        } doReturn MembershipQueryResult.Failure(
            PERSISTENCE_EXCEPTION
        )
        on {
            queryMembersSignatures(eq(mgm.toCorda()), eq(memberInfosWithoutMgm.map { it.holdingIdentity } ))
        } doReturn MembershipQueryResult.Success(
            signatures
        )
    }

    private val signer = mock<Signer>()
    private val signerFactory = mock<SignerFactory> {
        on { createSigner(mgmInfo) } doReturn signer
    }

    private val membershipPackage1 = mock<MembershipPackage>()
    private val membershipPackage2 = mock<MembershipPackage>()
    private val membershipPackageFactory = mock<MembershipPackageFactory> {
        on {
            createMembershipPackage(
                eq(signer),
                eq(signatures),
                eq(memberInfosWithoutMgm),
                any(),
                any(),
            )
        } doReturn membershipPackage1
        on {
            createMembershipPackage(
                eq(signer),
                eq(signature),
                eq(listOf(bobInfo)),
                any(),
                any(),
            )
        } doReturn membershipPackage2
    }

    private val record1 = mock<Record<String, AppMessage>>()
    private val record2 = mock<Record<String, AppMessage>>()
    private val p2pRecordsFactory = mock<P2pRecordsFactory> {
        on {
            createAuthenticatedMessageRecord(
                any(),
                any(),
                eq(membershipPackage1),
                any(),
                any(),
                eq(MembershipStatusFilter.ACTIVE),
            )
        } doReturn record1
        on {
            createAuthenticatedMessageRecord(
                any(),
                any(),
                eq(membershipPackage2),
                any(),
                any(),
                eq(MembershipStatusFilter.ACTIVE),
            )
        } doReturn record2
    }
    private val services = mock<MgmSynchronisationServiceImpl.InjectedServices> {
        on { publisherFactory } doReturn publisherFactory
        on { coordinatorFactory } doReturn coordinatorFactory
        on { configurationReadService } doReturn configurationReadService
        on { membershipGroupReaderProvider } doReturn groupReaderProvider
        on { membershipQueryClient } doReturn membershipQueryClient
        on { merkleTreeGenerator } doReturn merkleTreeGenerator
        on { membershipPackageFactory } doReturn membershipPackageFactory
        on { signerFactory } doReturn signerFactory
        on { p2pRecordsFactory } doReturn p2pRecordsFactory
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
            secureHash, BloomFilter(1, 1, 1, byteBuffer), secureHash, secureHash
        )
    )

    private fun createMemberInfo(name: String) = memberInfoFactory.create(
        sortedMapOf(
            GROUP_ID to GROUP,
            PARTY_NAME to name,
            Pair(String.format(URL_KEY, "0"), "http://localhost:8080"),
            Pair(String.format(PROTOCOL_VERSION, "0"), "1"),
            PLATFORM_VERSION to "5000",
            SOFTWARE_VERSION to "5.0.0"
        ),
        sortedMapOf(
            STATUS to MEMBER_STATUS_ACTIVE
        )
    )

    private fun createSignatures(members: List<MemberInfo>) = members.associate {
        val name = it.name.toString()
        it.holdingIdentity to (CryptoSignatureWithKey(
            ByteBuffer.wrap("pk-$name".toByteArray()),
            ByteBuffer.wrap("sig-$name".toByteArray())
        ) to CryptoSignatureSpec("dummy", null, null))
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
                setOf(BOOT_CONFIG, MESSAGING_CONFIG, MEMBERSHIP_CONFIG),
                mapOf(
                    BOOT_CONFIG to testConfig,
                    MESSAGING_CONFIG to testConfig,
                    MEMBERSHIP_CONFIG to membershipConfig,
                )
            ), coordinator
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
            .isEqualTo(setOf(BOOT_CONFIG, MESSAGING_CONFIG, MEMBERSHIP_CONFIG))

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

    @Test
    fun `processing requests fails when component is not running`() {
        val ex = assertFailsWith<IllegalStateException>{ synchronisationService.processSyncRequest(mock()) }
        assertThat(ex.message).isEqualTo("MgmSynchronisationService is currently inactive.")
    }

    @Test
    fun `all members are sent when member hash is matching`() {
        postConfigChangedEvent()
        synchronisationService.start()
        val capturedList = argumentCaptor<List<MemberInfo>>()
        val request = createRequest(alice)
        synchronisationService.processSyncRequest(request)
        verify(membershipPackageFactory, times(1)).createMembershipPackage(any(), any(), capturedList.capture(), any(), eq(groupParameters))
        verify(mockPublisher, times(1)).publish(eq(listOf(record1)))
        val membersPublished = capturedList.firstValue
        assertThat(membersPublished.size).isEqualTo(3)
        //assertThat(membersPublished).isEqualTo(listOf(aliceInfo, bobInfo, daisyInfo))
        synchronisationService.stop()
    }

    @Test
    fun `only the requesting member's info is sent when member hash is not matching`() {
        postConfigChangedEvent()
        synchronisationService.start()
        val capturedList = argumentCaptor<List<MemberInfo>>()
        val request = createRequest(bob)
        synchronisationService.processSyncRequest(request)
        verify(membershipPackageFactory, times(1)).createMembershipPackage(any(), any(), capturedList.capture(), any(), eq(groupParameters))
        verify(mockPublisher, times(1)).publish(eq(listOf(record2)))
        val membersPublished = capturedList.firstValue
        assertThat(membersPublished.size).isEqualTo(1)
        assertThat(membersPublished).isEqualTo(listOf(bobInfo))
        synchronisationService.stop()
    }

    @Test
    fun `exception is thrown when requester is not part of the group`() {
        postConfigChangedEvent()
        synchronisationService.start()
        val ex = assertFailsWith<CordaRuntimeException> {
            synchronisationService.processSyncRequest(createRequest(charlie))
        }
        assertThat(ex.message).isEqualTo("Requester ${MemberX500Name.parse(charlieName)} is not part of the membership group!")
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
