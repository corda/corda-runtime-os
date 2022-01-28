package net.corda.membership.staticnetwork

import net.corda.configuration.read.ConfigChangedEvent
import net.corda.configuration.read.ConfigurationReadService
import net.corda.crypto.CryptoOpsClient
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.membership.conversion.PropertyConverterImpl
import net.corda.membership.grouppolicy.GroupPolicyProvider
import net.corda.membership.identity.MemberInfoExtension.Companion.MEMBER_STATUS_ACTIVE
import net.corda.membership.identity.MemberInfoExtension.Companion.MEMBER_STATUS_SUSPENDED
import net.corda.membership.identity.MemberInfoExtension.Companion.endpoints
import net.corda.membership.identity.MemberInfoExtension.Companion.groupId
import net.corda.membership.identity.MemberInfoExtension.Companion.modifiedTime
import net.corda.membership.identity.MemberInfoExtension.Companion.softwareVersion
import net.corda.membership.identity.MemberInfoExtension.Companion.status
import net.corda.membership.identity.converter.EndpointInfoConverter
import net.corda.membership.identity.converter.PublicKeyConverter
import net.corda.membership.registration.MembershipRequestRegistrationResult
import net.corda.membership.registration.MembershipRequestRegistrationOutcome.SUBMITTED
import net.corda.membership.registration.MembershipRequestRegistrationOutcome.NOT_SUBMITTED
import net.corda.membership.staticnetwork.TestUtils.Companion.DUMMY_GROUP_ID
import net.corda.membership.staticnetwork.TestUtils.Companion.aliceName
import net.corda.membership.staticnetwork.TestUtils.Companion.bobName
import net.corda.membership.staticnetwork.TestUtils.Companion.charlieName
import net.corda.membership.staticnetwork.TestUtils.Companion.configs
import net.corda.membership.staticnetwork.TestUtils.Companion.daisyName
import net.corda.membership.staticnetwork.TestUtils.Companion.groupPolicyWithInvalidStaticNetworkTemplate
import net.corda.membership.staticnetwork.TestUtils.Companion.groupPolicyWithStaticNetwork
import net.corda.membership.staticnetwork.TestUtils.Companion.groupPolicyWithoutStaticNetwork
import net.corda.membership.staticnetwork.TestUtils.Companion.groupPolicyWithDuplicateMembers
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.records.Record
import net.corda.schema.Schemas
import net.corda.schema.configuration.ConfigKeys
import net.corda.v5.cipher.suite.KeyEncodingService
import net.corda.v5.membership.identity.MemberInfo
import net.corda.virtualnode.HoldingIdentity
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import java.security.PublicKey
import java.util.concurrent.CompletableFuture
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class StaticMemberRegistrationServiceTest {
    companion object {
        private const val ALICE_KEY = "1234"
        private const val BOB_KEY = "2345"
    }

    private val alice = HoldingIdentity(aliceName.toString(), DUMMY_GROUP_ID)
    private val bob = HoldingIdentity(bobName.toString(), DUMMY_GROUP_ID)
    private val charlie = HoldingIdentity(charlieName.toString(), DUMMY_GROUP_ID)
    private val daisy = HoldingIdentity(daisyName.toString(), DUMMY_GROUP_ID)
    private val aliceKey: PublicKey = mock()
    private val bobKey: PublicKey = mock()

    private val groupPolicyProvider: GroupPolicyProvider = mock {
        on { getGroupPolicy(alice) } doReturn groupPolicyWithStaticNetwork
        on { getGroupPolicy(bob) } doReturn groupPolicyWithInvalidStaticNetworkTemplate
        on { getGroupPolicy(charlie) } doReturn groupPolicyWithoutStaticNetwork
        on { getGroupPolicy(daisy) } doReturn groupPolicyWithDuplicateMembers
    }

    @Suppress("UNCHECKED_CAST")
    private val mockPublisher: Publisher = mock {
        on { publish(any()) } doAnswer {
            publishedList.addAll(it.arguments.first() as List<Record<String, MemberInfo>>)
            listOf(CompletableFuture.completedFuture(Unit))
        }
    }

    private val publisherFactory: PublisherFactory = mock {
        on { createPublisher(any(), any()) } doReturn mockPublisher
    }

    private val keyEncodingService: KeyEncodingService = mock {
        on { decodePublicKey(any<String>()) } doReturn mock()
        on { decodePublicKey(ALICE_KEY) } doReturn aliceKey
        on { decodePublicKey(BOB_KEY) } doReturn bobKey

        on { encodeAsString(any()) } doReturn "1"
        on { encodeAsString(aliceKey) } doReturn ALICE_KEY
        on { encodeAsString(bobKey) } doReturn BOB_KEY
    }

    private val cryptoOpsClient: CryptoOpsClient = mock {
        on { generateKeyPair(any(), any(), any(), any()) } doReturn mock()
        on { generateKeyPair(any(), any(), eq("alice-alias"), any()) } doReturn aliceKey
        // when no keyAlias is defined in static template, we are using the HoldingIdentity's id
        on { generateKeyPair(any(), any(), eq(bob.id), any()) } doReturn bobKey
    }

    private val configurationReadService: ConfigurationReadService = mock()

    private val publishedList = mutableListOf<Record<String, MemberInfo>>()

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

    private val converter: PropertyConverterImpl = PropertyConverterImpl(
        listOf(EndpointInfoConverter(), PublicKeyConverter(keyEncodingService))
    )

    private val registrationService = StaticMemberRegistrationService(
        groupPolicyProvider,
        publisherFactory,
        keyEncodingService,
        cryptoOpsClient,
        configurationReadService,
        lifecycleCoordinatorFactory,
        converter
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
            val memberPublished = it.value as MemberInfo
            assertEquals(DUMMY_GROUP_ID, memberPublished.groupId)
            assertNotNull(memberPublished.softwareVersion)
            assertNotNull(memberPublished.platformVersion)
            assertNotNull(memberPublished.serial)
            assertNotNull(memberPublished.modifiedTime)

            when(memberPublished.name) {
                aliceName -> {
                    assertEquals(aliceKey, memberPublished.owningKey)
                    assertEquals(1, memberPublished.identityKeys.size)
                    assertEquals(MEMBER_STATUS_ACTIVE, memberPublished.status)
                    assertEquals(1, memberPublished.endpoints.size)
                }
                bobName -> {
                    assertEquals(bobKey, memberPublished.owningKey)
                    assertEquals(1, memberPublished.identityKeys.size)
                    assertNotNull(memberPublished.status)
                    assertNotNull(memberPublished.endpoints)
                }
                charlieName -> {
                    assertEquals(1, memberPublished.identityKeys.size)
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
}