package net.corda.membership.staticnetwork

import net.corda.configuration.read.ConfigurationReadService
import net.corda.crypto.CryptoLibraryClientsFactory
import net.corda.crypto.CryptoLibraryClientsFactoryProvider
import net.corda.crypto.CryptoLibraryFactory
import net.corda.crypto.SigningService
import net.corda.crypto.SigningService.Companion.EMPTY_CONTEXT
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.membership.grouppolicy.GroupPolicyProvider
import net.corda.membership.identity.MemberInfoExtension.Companion.MEMBER_STATUS_ACTIVE
import net.corda.membership.identity.MemberInfoExtension.Companion.MEMBER_STATUS_SUSPENDED
import net.corda.membership.identity.MemberInfoExtension.Companion.endpoints
import net.corda.membership.identity.MemberInfoExtension.Companion.groupId
import net.corda.membership.identity.MemberInfoExtension.Companion.modifiedTime
import net.corda.membership.identity.MemberInfoExtension.Companion.softwareVersion
import net.corda.membership.identity.MemberInfoExtension.Companion.status
import net.corda.membership.registration.MembershipRequestRegistrationResult
import net.corda.membership.registration.MembershipRequestRegistrationOutcome.SUBMITTED
import net.corda.membership.registration.MembershipRequestRegistrationOutcome.NOT_SUBMITTED
import net.corda.membership.staticnetwork.TestUtils.Companion.DUMMY_GROUP_ID
import net.corda.membership.staticnetwork.TestUtils.Companion.aliceName
import net.corda.membership.staticnetwork.TestUtils.Companion.bobName
import net.corda.membership.staticnetwork.TestUtils.Companion.charlieName
import net.corda.membership.staticnetwork.TestUtils.Companion.daisyName
import net.corda.membership.staticnetwork.TestUtils.Companion.groupPolicyWithInvalidStaticNetworkTemplate
import net.corda.membership.staticnetwork.TestUtils.Companion.groupPolicyWithStaticNetwork
import net.corda.membership.staticnetwork.TestUtils.Companion.groupPolicyWithoutStaticNetwork
import net.corda.membership.staticnetwork.TestUtils.Companion.groupPolicyWithDuplicateMembers
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.records.Record
import net.corda.schema.Schemas
import net.corda.v5.cipher.suite.KeyEncodingService
import net.corda.v5.membership.identity.MemberInfo
import net.corda.virtualnode.HoldingIdentity
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.whenever
import java.security.PublicKey
import java.util.concurrent.CompletableFuture
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class StaticMemberRegistrationServiceTest {
    companion object {
        private const val ALICE_KEY = "1234"
        private const val BOB_KEY = "2345"
    }

    private val groupPolicyProvider: GroupPolicyProvider = mock()
    private val publisherFactory: PublisherFactory = mock()
    private val cryptoLibraryFactory: CryptoLibraryFactory = mock()
    private val cryptoLibraryClientsFactoryProvider: CryptoLibraryClientsFactoryProvider = mock()
    private val cryptoLibraryClientsFactory: CryptoLibraryClientsFactory = mock()
    private val configurationReadService: ConfigurationReadService = mock()
    private val keyEncodingService: KeyEncodingService = mock()
    private val signingService: SigningService = mock()
    private val  lifecycleHandler: RegistrationServiceLifecycleHandler = mock()
    private val mockPublisher: Publisher = mock()
    private lateinit var registrationService: StaticMemberRegistrationService

    private val publishedList = mutableListOf<Record<String, MemberInfo>>()
    private val alice = HoldingIdentity(aliceName.toString(), DUMMY_GROUP_ID)
    private val bob = HoldingIdentity(bobName.toString(), DUMMY_GROUP_ID)
    private val charlie = HoldingIdentity(charlieName.toString(), DUMMY_GROUP_ID)
    private val daisy = HoldingIdentity(daisyName.toString(), DUMMY_GROUP_ID)
    private val aliceKey: PublicKey = mock()
    private val bobKey: PublicKey = mock()

    private var coordinatorIsRunning = false
    private val coordinator: LifecycleCoordinator = mock<LifecycleCoordinator>().apply {
        doAnswer { coordinatorIsRunning }.whenever(this).isRunning
        doAnswer { coordinatorIsRunning = true }.whenever(this).start()
        doAnswer { coordinatorIsRunning = false }.whenever(this).stop()
    }

    private val lifecycleCoordinatorFactory: LifecycleCoordinatorFactory = mock<LifecycleCoordinatorFactory>().apply {
        doReturn(coordinator).whenever(this).createCoordinator(any(), any())
    }

    @BeforeEach
    @Suppress("UNCHECKED_CAST")
    fun setUp() {
        // clears the list before the test runs as we are using one list for the test cases
        publishedList.clear()
        whenever(mockPublisher.publish(any())).thenAnswer {
            publishedList.addAll(it.arguments.first() as List<Record<String, MemberInfo>>)
            listOf(CompletableFuture.completedFuture(Unit))
        }
        whenever(lifecycleHandler.publisher).thenReturn(mockPublisher)
        whenever(groupPolicyProvider.getGroupPolicy(alice)).thenReturn(groupPolicyWithStaticNetwork)
        whenever(groupPolicyProvider.getGroupPolicy(bob)).thenReturn(groupPolicyWithInvalidStaticNetworkTemplate)
        whenever(groupPolicyProvider.getGroupPolicy(charlie)).thenReturn(groupPolicyWithoutStaticNetwork)
        whenever(groupPolicyProvider.getGroupPolicy(daisy)).thenReturn(groupPolicyWithDuplicateMembers)

        whenever(cryptoLibraryFactory.getKeyEncodingService()).thenReturn(keyEncodingService)
        whenever(cryptoLibraryClientsFactoryProvider.get(any(), any())).thenReturn(cryptoLibraryClientsFactory)
        whenever(cryptoLibraryClientsFactory.getSigningService(any())).thenReturn(signingService)

        whenever(
            keyEncodingService.decodePublicKey(any<String>())
        ).thenReturn(mock())
        whenever(
            keyEncodingService.decodePublicKey(eq(ALICE_KEY))
        ).thenReturn(aliceKey)
        whenever(
            keyEncodingService.decodePublicKey(eq(BOB_KEY))
        ).thenReturn(bobKey)

        whenever(
            keyEncodingService.encodeAsString(any())
        ).thenReturn("1")
        whenever(
            keyEncodingService.encodeAsString(aliceKey)
        ).thenReturn(ALICE_KEY)
        whenever(
            keyEncodingService.encodeAsString(bobKey)
        ).thenReturn(BOB_KEY)

        whenever(
            signingService.generateKeyPair(any(), eq(EMPTY_CONTEXT))
        ).thenReturn(mock())
        whenever(
            signingService.generateKeyPair(eq("alice-alias"), eq(EMPTY_CONTEXT))
        ).thenReturn(aliceKey)
        // when no keyAlias is defined in static template, we are using the HoldingIdentity's id
        whenever(
            signingService.generateKeyPair(eq(bob.id), eq(EMPTY_CONTEXT))
        ).thenReturn(bobKey)

        registrationService = StaticMemberRegistrationService(
            groupPolicyProvider,
            publisherFactory,
            cryptoLibraryFactory,
            cryptoLibraryClientsFactoryProvider,
            configurationReadService,
            lifecycleCoordinatorFactory,
            lifecycleHandler
        )
    }

    @Test
    fun `starting and stopping the service succeeds`() {
        registrationService.start()
        assertEquals(true, registrationService.isRunning)
        registrationService.stop()
        assertEquals(false, registrationService.isRunning)
    }

    @Test
    fun `during registration, the static network inside the GroupPolicy file gets parsed and published`() {
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
}