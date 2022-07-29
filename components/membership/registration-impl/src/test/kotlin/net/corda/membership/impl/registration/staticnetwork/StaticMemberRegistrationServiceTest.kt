package net.corda.membership.impl.registration.staticnetwork

import net.corda.configuration.read.ConfigChangedEvent
import net.corda.configuration.read.ConfigurationReadService
import net.corda.crypto.client.CryptoOpsClient
import net.corda.crypto.client.hsm.HSMRegistrationClient
import net.corda.crypto.core.CryptoConsts
import net.corda.data.membership.PersistentMemberInfo
import net.corda.layeredpropertymap.LayeredPropertyMapFactory
import net.corda.layeredpropertymap.impl.LayeredPropertyMapFactoryImpl
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleStatus
import net.corda.membership.grouppolicy.GroupPolicyProvider
import net.corda.membership.impl.registration.staticnetwork.TestUtils.Companion.DUMMY_GROUP_ID
import net.corda.membership.impl.registration.staticnetwork.TestUtils.Companion.aliceName
import net.corda.membership.impl.registration.staticnetwork.TestUtils.Companion.bobName
import net.corda.membership.impl.registration.staticnetwork.TestUtils.Companion.charlieName
import net.corda.membership.impl.registration.staticnetwork.TestUtils.Companion.configs
import net.corda.membership.impl.registration.staticnetwork.TestUtils.Companion.daisyName
import net.corda.membership.impl.registration.staticnetwork.TestUtils.Companion.ericName
import net.corda.membership.impl.registration.staticnetwork.TestUtils.Companion.groupPolicyWithEmptyStaticNetwork
import net.corda.membership.impl.registration.staticnetwork.TestUtils.Companion.groupPolicyWithInvalidStaticNetworkTemplate
import net.corda.membership.impl.registration.staticnetwork.TestUtils.Companion.groupPolicyWithStaticNetwork
import net.corda.membership.impl.registration.staticnetwork.TestUtils.Companion.groupPolicyWithoutStaticNetwork
import net.corda.membership.lib.MemberInfoFactory
import net.corda.membership.lib.MemberInfoExtension.Companion.MEMBER_STATUS_ACTIVE
import net.corda.membership.lib.MemberInfoExtension.Companion.endpoints
import net.corda.membership.lib.MemberInfoExtension.Companion.groupId
import net.corda.membership.lib.MemberInfoExtension.Companion.ledgerKeyHashes
import net.corda.membership.lib.MemberInfoExtension.Companion.modifiedTime
import net.corda.membership.lib.MemberInfoExtension.Companion.softwareVersion
import net.corda.membership.lib.MemberInfoExtension.Companion.status
import net.corda.membership.lib.impl.MemberInfoFactoryImpl
import net.corda.membership.lib.impl.converter.EndpointInfoConverter
import net.corda.crypto.impl.converter.PublicKeyConverter
import net.corda.crypto.impl.converter.PublicKeyHashConverter
import net.corda.membership.lib.toSortedMap
import net.corda.membership.registration.MembershipRequestRegistrationOutcome.NOT_SUBMITTED
import net.corda.membership.registration.MembershipRequestRegistrationOutcome.SUBMITTED
import net.corda.membership.registration.MembershipRequestRegistrationResult
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.records.Record
import net.corda.p2p.HostedIdentityEntry
import net.corda.schema.Schemas
import net.corda.schema.Schemas.P2P.Companion.P2P_HOSTED_IDENTITIES_TOPIC
import net.corda.schema.configuration.ConfigKeys
import net.corda.v5.cipher.suite.KeyEncodingService
import net.corda.v5.crypto.ECDSA_SECP256R1_CODE_NAME
import net.corda.v5.crypto.calculateHash
import net.corda.virtualnode.HoldingIdentity
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import java.security.PublicKey
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class StaticMemberRegistrationServiceTest {
    companion object {
        private const val DEFAULT_KEY = "3456"
        private const val ALICE_KEY = "1234"
        private const val BOB_KEY = "2345"
        private const val CHARLIE_KEY = "6789"
        private const val KEY_SCHEME = "corda.key.scheme"
    }

    private val alice = HoldingIdentity(aliceName.toString(), DUMMY_GROUP_ID)
    private val bob = HoldingIdentity(bobName.toString(), DUMMY_GROUP_ID)
    private val charlie = HoldingIdentity(charlieName.toString(), DUMMY_GROUP_ID)
    private val daisy = HoldingIdentity(daisyName.toString(), DUMMY_GROUP_ID)
    private val eric = HoldingIdentity(ericName.toString(), DUMMY_GROUP_ID)

    private val aliceId = alice.shortHash
    private val bobId = bob.shortHash
    private val charlieId = charlie.shortHash

    private val defaultKey: PublicKey = mock {
        on { encoded } doReturn DEFAULT_KEY.toByteArray()
    }
    private val aliceKey: PublicKey = mock {
        on { encoded } doReturn ALICE_KEY.toByteArray()
    }
    private val bobKey: PublicKey = mock {
        on { encoded } doReturn BOB_KEY.toByteArray()
    }
    private val charlieKey: PublicKey = mock {
        on { encoded } doReturn CHARLIE_KEY.toByteArray()
    }

    private val groupPolicyProvider: GroupPolicyProvider = mock {
        on { getGroupPolicy(alice) } doReturn groupPolicyWithStaticNetwork
        on { getGroupPolicy(bob) } doReturn groupPolicyWithInvalidStaticNetworkTemplate
        on { getGroupPolicy(charlie) } doReturn groupPolicyWithoutStaticNetwork
        on { getGroupPolicy(daisy) } doReturn groupPolicyWithStaticNetwork
        on { getGroupPolicy(eric) } doReturn groupPolicyWithEmptyStaticNetwork
    }

    @Suppress("UNCHECKED_CAST")
    private val mockPublisher: Publisher = mock()

    private val publisherFactory: PublisherFactory = mock {
        on { createPublisher(any(), any()) } doReturn mockPublisher
    }

    private val keyEncodingService: KeyEncodingService = mock {
        on { decodePublicKey(any<String>()) } doReturn defaultKey
        on { decodePublicKey(ALICE_KEY) } doReturn aliceKey
        on { decodePublicKey(BOB_KEY) } doReturn bobKey

        on { encodeAsString(any()) } doReturn DEFAULT_KEY
        on { encodeAsString(aliceKey) } doReturn ALICE_KEY
        on { encodeAsString(bobKey) } doReturn BOB_KEY
        on { encodeAsString(charlieKey) } doReturn CHARLIE_KEY

        on { encodeAsByteArray(any()) } doReturn ByteArray(1)
    }

    private val cryptoOpsClient: CryptoOpsClient = mock {
        on { generateKeyPair(any(), any(), any(), any(), any<Map<String, String>>()) } doReturn defaultKey
        on { generateKeyPair(any(), any(), eq(aliceId), any(), any<Map<String, String>>()) } doReturn aliceKey
        on { generateKeyPair(any(), any(), eq(bobId), any(), any<Map<String, String>>()) } doReturn bobKey
        on { generateKeyPair(any(), any(), eq(charlieId), any(), any<Map<String, String>>()) } doReturn charlieKey
    }

    private val configurationReadService: ConfigurationReadService = mock()

    private var coordinatorIsRunning = false
    private var coordinatorStatus = LifecycleStatus.DOWN
    private val coordinator: LifecycleCoordinator = mock {
        on { isRunning } doAnswer { coordinatorIsRunning }
        on { start() } doAnswer { coordinatorIsRunning = true }
        on { stop() } doAnswer { coordinatorIsRunning = false }
        on { updateStatus(any(), any()) } doAnswer {
            coordinatorStatus = it.arguments[0] as LifecycleStatus
        }
        on { status } doAnswer { coordinatorStatus }
    }

    private var registrationServiceLifecycleHandler: RegistrationServiceLifecycleHandler? = null

    private val lifecycleCoordinatorFactory: LifecycleCoordinatorFactory = mock {
        on { createCoordinator(any(), any()) } doReturn coordinator
        on { createCoordinator(any(), any()) } doAnswer {
            registrationServiceLifecycleHandler = it.arguments[1] as RegistrationServiceLifecycleHandler
            coordinator
        }
    }

    private val layeredPropertyMapFactory: LayeredPropertyMapFactory = LayeredPropertyMapFactoryImpl(
        listOf(EndpointInfoConverter(), PublicKeyConverter(keyEncodingService), PublicKeyHashConverter())
    )
    private val memberInfoFactory: MemberInfoFactory = MemberInfoFactoryImpl(layeredPropertyMapFactory)

    private val hsmRegistrationClient: HSMRegistrationClient = mock()

    private val mockContext: Map<String, String> = mock {
        on { get(KEY_SCHEME) } doReturn ECDSA_SECP256R1_CODE_NAME
    }

    private val registrationService = StaticMemberRegistrationService(
        groupPolicyProvider,
        publisherFactory,
        keyEncodingService,
        cryptoOpsClient,
        configurationReadService,
        lifecycleCoordinatorFactory,
        hsmRegistrationClient,
        memberInfoFactory
    )

    @Suppress("UNCHECKED_CAST")
    private fun setUpPublisher() {
        // kicks off the MessagingConfigurationReceived event to be able to mock the Publisher
        registrationServiceLifecycleHandler?.processEvent(
            ConfigChangedEvent(setOf(ConfigKeys.BOOT_CONFIG, ConfigKeys.MESSAGING_CONFIG), configs),
            coordinator
        )
    }

    @Test
    fun `starting and stopping the service succeeds`() {
        registrationService.start()
        assertTrue(registrationService.isRunning)
        registrationService.stop()
        assertFalse(registrationService.isRunning)
    }

    @Test
    fun `during registration, the registering static member inside the GroupPolicy file gets parsed and published`() {
        setUpPublisher()
        registrationService.start()
        val capturedPublishedList = argumentCaptor<List<Record<String, Any>>>()
        val registrationResult = registrationService.register(alice, mockContext)
        Mockito.verify(mockPublisher, times(2)).publish(capturedPublishedList.capture())
        CryptoConsts.Categories.all.forEach {
            Mockito.verify(hsmRegistrationClient, times(1)).findHSM(aliceId, it)
            Mockito.verify(hsmRegistrationClient, times(1)).assignSoftHSM(aliceId, it)
        }
        registrationService.stop()

        val memberList = capturedPublishedList.firstValue
        assertEquals(3, memberList.size)

        val hostedIdentityList = capturedPublishedList.secondValue
        assertEquals(1, hostedIdentityList.size)

        memberList.forEach {
            assertTrue(it.key.startsWith(aliceId) || it.key.startsWith(bobId) || it.key.startsWith(charlieId))
            assertTrue(it.key.endsWith(aliceId))
        }

        val publishedInfo = memberList.first()

        assertEquals(Schemas.Membership.MEMBER_LIST_TOPIC, publishedInfo.topic)
        val persistentMemberPublished = publishedInfo.value as PersistentMemberInfo
        val memberPublished = memberInfoFactory.create(
            persistentMemberPublished.memberContext.toSortedMap(),
            persistentMemberPublished.mgmContext.toSortedMap()
        )
        assertEquals(DUMMY_GROUP_ID, memberPublished.groupId)
        assertNotNull(memberPublished.softwareVersion)
        assertNotNull(memberPublished.platformVersion)
        assertNotNull(memberPublished.serial)
        assertNotNull(memberPublished.modifiedTime)

        assertEquals(aliceKey, memberPublished.sessionInitiationKey)
        assertEquals(1, memberPublished.ledgerKeys.size)
        assertEquals(1, memberPublished.ledgerKeyHashes.size)
        assertEquals(aliceKey.calculateHash(), memberPublished.ledgerKeyHashes.first())
        assertEquals(MEMBER_STATUS_ACTIVE, memberPublished.status)
        assertEquals(1, memberPublished.endpoints.size)

        val publishedHostedIdentity = hostedIdentityList.first()

        assertEquals(alice.shortHash, publishedHostedIdentity.key)
        assertEquals(P2P_HOSTED_IDENTITIES_TOPIC, publishedHostedIdentity.topic)
        val hostedIdentityPublished = publishedHostedIdentity.value as HostedIdentityEntry
        assertEquals(alice.groupId, hostedIdentityPublished.holdingIdentity.groupId)
        assertEquals(alice.x500Name, hostedIdentityPublished.holdingIdentity.x500Name)

        assertEquals(MembershipRequestRegistrationResult(SUBMITTED), registrationResult)
    }

    @Test
    fun `registration fails when name field is empty in the GroupPolicy file`() {
        setUpPublisher()
        registrationService.start()
        val registrationResult = registrationService.register(bob, mockContext)
        assertEquals(
            MembershipRequestRegistrationResult(
                NOT_SUBMITTED,
                "Registration failed. Reason: Member's name is not provided in static member list."
            ),
            registrationResult
        )
        registrationService.stop()
    }

    @Test
    fun `registration fails when static network is missing`() {
        setUpPublisher()
        registrationService.start()
        val registrationResult = registrationService.register(charlie, mockContext)
        assertEquals(
            MembershipRequestRegistrationResult(
                NOT_SUBMITTED,
                "Registration failed. Reason: Could not find static member list in group policy file."
            ),
            registrationResult
        )
        registrationService.stop()
    }

    @Test
    fun `registration fails when static network is empty`() {
        setUpPublisher()
        registrationService.start()
        val registrationResult = registrationService.register(eric, mockContext)
        assertEquals(
            MembershipRequestRegistrationResult(
                NOT_SUBMITTED,
                "Registration failed. Reason: Static member list inside the group policy file cannot be empty."
            ),
            registrationResult
        )
        registrationService.stop()
    }

    @Test
    fun `registration fails when coordinator is not running`() {
        setUpPublisher()
        val registrationResult = registrationService.register(alice, mockContext)
        assertEquals(
            MembershipRequestRegistrationResult(
                NOT_SUBMITTED,
                "Registration failed. Reason: StaticMemberRegistrationService is not running/down."
            ),
            registrationResult
        )
    }

    @Test
    fun `registration fails when registering member is not in the static member list`() {
        setUpPublisher()
        registrationService.start()
        val registrationResult = registrationService.register(daisy, mockContext)
        assertEquals(
            MembershipRequestRegistrationResult(
                NOT_SUBMITTED,
                "Registration failed. Reason: Our membership O=Daisy, L=London, C=GB is not listed in the static member list."
            ),
            registrationResult
        )
        registrationService.stop()
    }

    @Test
    fun `registration fails when key scheme is not provided in context`() {
        setUpPublisher()
        registrationService.start()
        val registrationResult = registrationService.register(alice, mock())
        assertEquals(
            MembershipRequestRegistrationResult(
                NOT_SUBMITTED,
                "Registration failed. Reason: Key scheme must be specified."
            ),
            registrationResult
        )
        registrationService.stop()
    }
}
