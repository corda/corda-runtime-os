package net.corda.membership.impl.registration.staticnetwork

import net.corda.configuration.read.ConfigChangedEvent
import net.corda.configuration.read.ConfigurationReadService
import net.corda.crypto.client.CryptoOpsClient
import net.corda.data.KeyValuePairList
import net.corda.data.membership.PersistentMemberInfo
import net.corda.layeredpropertymap.LayeredPropertyMapFactory
import net.corda.layeredpropertymap.create
import net.corda.layeredpropertymap.impl.LayeredPropertyMapFactoryImpl
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.membership.grouppolicy.GroupPolicyProvider
import net.corda.membership.impl.MGMContextImpl
import net.corda.membership.impl.MemberContextImpl
import net.corda.membership.impl.MemberInfoExtension.Companion.MEMBER_STATUS_ACTIVE
import net.corda.membership.impl.MemberInfoExtension.Companion.MEMBER_STATUS_SUSPENDED
import net.corda.membership.impl.MemberInfoExtension.Companion.endpoints
import net.corda.membership.impl.MemberInfoExtension.Companion.groupId
import net.corda.membership.impl.MemberInfoExtension.Companion.identityKeyHashes
import net.corda.membership.impl.MemberInfoExtension.Companion.modifiedTime
import net.corda.membership.impl.MemberInfoExtension.Companion.softwareVersion
import net.corda.membership.impl.MemberInfoExtension.Companion.status
import net.corda.membership.impl.converter.EndpointInfoConverter
import net.corda.membership.impl.converter.PublicKeyConverter
import net.corda.membership.impl.converter.PublicKeyHashConverter
import net.corda.membership.impl.registration.staticnetwork.TestUtils.Companion.DUMMY_GROUP_ID
import net.corda.membership.impl.registration.staticnetwork.TestUtils.Companion.aliceName
import net.corda.membership.impl.registration.staticnetwork.TestUtils.Companion.bobName
import net.corda.membership.impl.registration.staticnetwork.TestUtils.Companion.charlieName
import net.corda.membership.impl.registration.staticnetwork.TestUtils.Companion.configs
import net.corda.membership.impl.registration.staticnetwork.TestUtils.Companion.daisyName
import net.corda.membership.impl.registration.staticnetwork.TestUtils.Companion.ericName
import net.corda.membership.impl.registration.staticnetwork.TestUtils.Companion.frankieName
import net.corda.membership.impl.registration.staticnetwork.TestUtils.Companion.groupPolicyWithDuplicateMembers
import net.corda.membership.impl.registration.staticnetwork.TestUtils.Companion.groupPolicyWithInvalidStaticNetworkTemplate
import net.corda.membership.impl.registration.staticnetwork.TestUtils.Companion.groupPolicyWithStaticNetwork
import net.corda.membership.impl.registration.staticnetwork.TestUtils.Companion.groupPolicyWithoutMgm
import net.corda.membership.impl.registration.staticnetwork.TestUtils.Companion.groupPolicyWithoutMgmKeyAlias
import net.corda.membership.impl.registration.staticnetwork.TestUtils.Companion.groupPolicyWithoutStaticNetwork
import net.corda.membership.impl.toMemberInfo
import net.corda.membership.impl.toSortedMap
import net.corda.membership.registration.MembershipRequestRegistrationOutcome.NOT_SUBMITTED
import net.corda.membership.registration.MembershipRequestRegistrationOutcome.SUBMITTED
import net.corda.membership.registration.MembershipRequestRegistrationResult
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.records.Record
import net.corda.schema.Schemas
import net.corda.schema.configuration.ConfigKeys
import net.corda.v5.cipher.suite.KeyEncodingService
import net.corda.v5.crypto.DigestService
import net.corda.v5.crypto.DigitalSignature
import net.corda.v5.crypto.SecureHash
import net.corda.v5.crypto.calculateHash
import net.corda.virtualnode.HoldingIdentity
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import java.security.PublicKey
import java.util.concurrent.CompletableFuture
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
    }

    private val alice = HoldingIdentity(aliceName.toString(), DUMMY_GROUP_ID)
    private val bob = HoldingIdentity(bobName.toString(), DUMMY_GROUP_ID)
    private val charlie = HoldingIdentity(charlieName.toString(), DUMMY_GROUP_ID)
    private val daisy = HoldingIdentity(daisyName.toString(), DUMMY_GROUP_ID)
    private val eric = HoldingIdentity(ericName.toString(), DUMMY_GROUP_ID)
    private val frankie = HoldingIdentity(frankieName.toString(), DUMMY_GROUP_ID)
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
        on { getGroupPolicy(daisy) } doReturn groupPolicyWithDuplicateMembers
        on { getGroupPolicy(eric) } doReturn groupPolicyWithoutMgm
        on { getGroupPolicy(frankie) } doReturn groupPolicyWithoutMgmKeyAlias
    }

    @Suppress("UNCHECKED_CAST")
    private val mockPublisher: Publisher = mock {
        on { publish(any()) } doAnswer {
            publishedList.addAll(it.arguments.first() as List<Record<String, PersistentMemberInfo>>)
            listOf(CompletableFuture.completedFuture(Unit))
        }
    }

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

    private val signature: DigitalSignature.WithKey = DigitalSignature.WithKey(mock(), ByteArray(1))

    private val cryptoOpsClient: CryptoOpsClient = mock {
        on { generateKeyPair(any(), any(), any(), any<Map<String, String>>()) } doReturn defaultKey
        on { generateKeyPair(any(), any(), eq("alice-alias"), any<Map<String, String>>()) } doReturn aliceKey
        // when no keyAlias is defined in static template, we are using the HoldingIdentity's id
        on { generateKeyPair(any(), any(), eq(bob.id), any<Map<String, String>>()) } doReturn bobKey
        on { generateKeyPair(any(), any(), eq(charlie.id), any<Map<String, String>>()) } doReturn charlieKey
        on { sign(any(), any<PublicKey>(), any<ByteArray>(), any()) } doReturn signature
    }

    private val configurationReadService: ConfigurationReadService = mock()

    private val publishedList = mutableListOf<Record<String, PersistentMemberInfo>>()

    private var coordinatorIsRunning = false
    private val coordinator: LifecycleCoordinator = mock {
        on { isRunning } doAnswer { coordinatorIsRunning }
        on { start() } doAnswer { coordinatorIsRunning = true }
        on { stop() } doAnswer { coordinatorIsRunning = false }
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

    private val digestService: DigestService = mock {
        on { hash(any<ByteArray>(), any()) } doReturn SecureHash("SHA256", "1234ABCD".toByteArray())
    }

    private val registrationService = StaticMemberRegistrationService(
        groupPolicyProvider,
        publisherFactory,
        keyEncodingService,
        cryptoOpsClient,
        configurationReadService,
        lifecycleCoordinatorFactory,
        layeredPropertyMapFactory,
        digestService
    )

    @Suppress("UNCHECKED_CAST")
    private fun setUpPublisher() {
        // clears the list before the test runs as we are using one list for the test cases
        publishedList.clear()
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
    fun `during registration, the static network inside the GroupPolicy file gets parsed and published`() {
        setUpPublisher()
        registrationService.start()
        val registrationResult = registrationService.register(alice)
        registrationService.stop()

        assertEquals(3, publishedList.size)

        publishedList.forEach {
            assertEquals(Schemas.Membership.MEMBER_LIST_TOPIC, it.topic)
            assertTrue { it.key.startsWith(alice.id) }
            val signedMemberPublished = it.value as PersistentMemberInfo
            val memberPublished = toMemberInfo(
                layeredPropertyMapFactory.create<MemberContextImpl>(
                    KeyValuePairList.fromByteBuffer(signedMemberPublished.signedMemberInfo.memberContext).toSortedMap()
                ),
                layeredPropertyMapFactory.create<MGMContextImpl>(
                    KeyValuePairList.fromByteBuffer(signedMemberPublished.signedMemberInfo.mgmContext).toSortedMap()
                )
            )
            assertEquals(DUMMY_GROUP_ID, memberPublished.groupId)
            assertNotNull(memberPublished.softwareVersion)
            assertNotNull(memberPublished.platformVersion)
            assertNotNull(memberPublished.serial)
            assertNotNull(memberPublished.modifiedTime)

            when (memberPublished.name) {
                aliceName -> {
                    assertEquals(aliceKey, memberPublished.owningKey)
                    assertEquals(1, memberPublished.identityKeys.size)
                    assertEquals(1, memberPublished.identityKeyHashes.size)
                    assertEquals(aliceKey.calculateHash(), memberPublished.identityKeyHashes.first())
                    assertEquals(MEMBER_STATUS_ACTIVE, memberPublished.status)
                    assertEquals(1, memberPublished.endpoints.size)
                }
                bobName -> {
                    assertEquals(bobKey, memberPublished.owningKey)
                    assertEquals(1, memberPublished.identityKeys.size)
                    assertEquals(1, memberPublished.identityKeyHashes.size)
                    assertEquals(bobKey.calculateHash(), memberPublished.identityKeyHashes.first())
                    assertNotNull(memberPublished.status)
                    assertNotNull(memberPublished.endpoints)
                }
                charlieName -> {
                    assertEquals(1, memberPublished.identityKeys.size)
                    assertEquals(1, memberPublished.identityKeyHashes.size)
                    assertEquals(MEMBER_STATUS_SUSPENDED, memberPublished.status)
                    assertEquals(2, memberPublished.endpoints.size)
                }
            }
        }
        assertEquals(MembershipRequestRegistrationResult(SUBMITTED), registrationResult)
    }

    @Test
    fun `parsing of MemberInfo list fails when name field is empty in the GroupPolicy file`() {
        setUpPublisher()
        registrationService.start()
        val registrationResult = registrationService.register(bob)
        assertEquals(
            MembershipRequestRegistrationResult(
                NOT_SUBMITTED,
                "Registration failed. Reason: Member's name is not provided."
            ),
            registrationResult
        )
        registrationService.stop()
    }

    @Test
    fun `registration fails when static network is empty`() {
        setUpPublisher()
        registrationService.start()
        val registrationResult = registrationService.register(charlie)
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
    fun `registration fails when we have duplicated members defined`() {
        setUpPublisher()
        registrationService.start()
        val registrationResult = registrationService.register(daisy)
        assertEquals(
            MembershipRequestRegistrationResult(
                NOT_SUBMITTED,
                "Registration failed. Reason: Duplicated static member declaration."
            ),
            registrationResult
        )
        registrationService.stop()
    }

    @Test
    fun `registration fails when coordinator is not running`() {
        setUpPublisher()
        val registrationResult = registrationService.register(daisy)
        assertEquals(
            MembershipRequestRegistrationResult(
                NOT_SUBMITTED,
                "Registration failed. Reason: StaticMemberRegistrationService is not running/down."
            ),
            registrationResult
        )
    }

    @Test
    fun `registration fails when mgm is not defined`() {
        setUpPublisher()
        registrationService.start()
        val registrationResult = registrationService.register(eric)
        assertEquals(
            MembershipRequestRegistrationResult(
                NOT_SUBMITTED,
                "Registration failed. Reason: Static mgm inside the group policy file should be defined."
            ),
            registrationResult
        )
        registrationService.stop()
    }

    @Test
    fun `registration fails when mgm's key alias is not defined`() {
        setUpPublisher()
        registrationService.start()
        val registrationResult = registrationService.register(frankie)
        assertEquals(
            MembershipRequestRegistrationResult(
                NOT_SUBMITTED,
                "Registration failed. Reason: MGM's key alias is not provided."
            ),
            registrationResult
        )
        registrationService.stop()
    }
}