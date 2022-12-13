package net.corda.membership.impl.registration.staticnetwork

import net.corda.configuration.read.ConfigChangedEvent
import net.corda.configuration.read.ConfigurationReadService
import net.corda.crypto.cipher.suite.KeyEncodingService
import net.corda.crypto.client.CryptoOpsClient
import net.corda.crypto.client.hsm.HSMRegistrationClient
import net.corda.crypto.core.CryptoConsts
import net.corda.crypto.core.CryptoConsts.Categories.LEDGER
import net.corda.crypto.core.CryptoConsts.Categories.SESSION_INIT
import net.corda.crypto.impl.converter.PublicKeyConverter
import net.corda.crypto.impl.converter.PublicKeyHashConverter
import net.corda.data.CordaAvroSerializationFactory
import net.corda.data.CordaAvroSerializer
import net.corda.data.KeyValuePairList
import net.corda.data.crypto.wire.CryptoSigningKey
import net.corda.data.membership.PersistentMemberInfo
import net.corda.data.membership.common.RegistrationStatus
import net.corda.layeredpropertymap.testkit.LayeredPropertyMapMocks
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleStatus
import net.corda.membership.groupparams.writer.service.GroupParametersWriterService
import net.corda.membership.grouppolicy.GroupPolicyProvider
import net.corda.membership.impl.registration.TEST_CPI_NAME
import net.corda.membership.impl.registration.TEST_CPI_VERSION
import net.corda.membership.impl.registration.TEST_PLATFORM_VERSION
import net.corda.membership.impl.registration.TEST_SOFTWARE_VERSION
import net.corda.membership.impl.registration.buildMockPlatformInfoProvider
import net.corda.membership.impl.registration.buildTestVirtualNodeInfo
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
import net.corda.membership.impl.registration.staticnetwork.TestUtils.Companion.groupPolicyWithStaticNetworkAndDistinctKeys
import net.corda.membership.impl.registration.staticnetwork.TestUtils.Companion.groupPolicyWithoutStaticNetwork
import net.corda.membership.impl.registration.testCpiSignerSummaryHash
import net.corda.membership.lib.EndpointInfoFactory
import net.corda.membership.lib.GroupParametersFactory
import net.corda.membership.lib.MemberInfoExtension.Companion.MEMBER_STATUS_ACTIVE
import net.corda.membership.lib.MemberInfoExtension.Companion.cpiInfo
import net.corda.membership.lib.MemberInfoExtension.Companion.endpoints
import net.corda.membership.lib.MemberInfoExtension.Companion.groupId
import net.corda.membership.lib.MemberInfoExtension.Companion.ledgerKeyHashes
import net.corda.membership.lib.MemberInfoExtension.Companion.modifiedTime
import net.corda.membership.lib.MemberInfoExtension.Companion.notaryDetails
import net.corda.membership.lib.MemberInfoExtension.Companion.softwareVersion
import net.corda.membership.lib.MemberInfoExtension.Companion.status
import net.corda.membership.lib.MemberInfoFactory
import net.corda.membership.lib.impl.MemberInfoFactoryImpl
import net.corda.membership.lib.impl.converter.EndpointInfoConverter
import net.corda.membership.lib.impl.converter.MemberNotaryDetailsConverter
import net.corda.membership.lib.registration.RegistrationRequest
import net.corda.membership.lib.schema.validation.MembershipSchemaValidationException
import net.corda.membership.lib.schema.validation.MembershipSchemaValidator
import net.corda.membership.lib.schema.validation.MembershipSchemaValidatorFactory
import net.corda.membership.lib.toSortedMap
import net.corda.membership.persistence.client.MembershipPersistenceClient
import net.corda.membership.persistence.client.MembershipPersistenceResult
import net.corda.membership.registration.MembershipRequestRegistrationOutcome.NOT_SUBMITTED
import net.corda.membership.registration.MembershipRequestRegistrationOutcome.SUBMITTED
import net.corda.membership.registration.MembershipRequestRegistrationResult
import net.corda.messaging.api.processor.CompactedProcessor
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.records.Record
import net.corda.messaging.api.subscription.CompactedSubscription
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.p2p.HostedIdentityEntry
import net.corda.schema.Schemas
import net.corda.schema.Schemas.P2P.Companion.P2P_HOSTED_IDENTITIES_TOPIC
import net.corda.schema.configuration.ConfigKeys
import net.corda.schema.membership.MembershipSchema
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.crypto.ECDSA_SECP256R1_CODE_NAME
import net.corda.v5.crypto.PublicKeyHash
import net.corda.v5.crypto.RSA_CODE_NAME
import net.corda.v5.crypto.SignatureSpec
import net.corda.v5.crypto.calculateHash
import net.corda.v5.membership.GroupParameters
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.SoftAssertions.assertSoftly
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doNothing
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.security.PublicKey
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class StaticMemberRegistrationServiceTest {
    private companion object {
        const val DEFAULT_KEY = "3456"
        const val ALICE_KEY = "1234"
        const val BOB_KEY = "2345"
        const val CHARLIE_KEY = "6789"
        const val KEY_SCHEME = "corda.key.scheme"
    }

    private val alice = HoldingIdentity(aliceName, DUMMY_GROUP_ID)
    private val bob = HoldingIdentity(bobName, DUMMY_GROUP_ID)
    private val charlie = HoldingIdentity(charlieName, DUMMY_GROUP_ID)
    private val daisy = HoldingIdentity(daisyName, DUMMY_GROUP_ID)
    private val eric = HoldingIdentity(ericName, DUMMY_GROUP_ID)

    private val aliceId = alice.shortHash
    private val bobId = bob.shortHash
    private val charlieId = charlie.shortHash

    private val registrationId = UUID(10, 20)
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

    private val mockPublisher: Publisher = mock()

    private val publisherFactory: PublisherFactory = mock {
        on { createPublisher(any(), any()) } doReturn mockPublisher
    }

    private val mockSubscription: CompactedSubscription<String, KeyValuePairList> = mock()

    private val subscriptionFactory: SubscriptionFactory = mock {
        on { createCompactedSubscription(any(), any<CompactedProcessor<String, KeyValuePairList>>(), any()) } doReturn mockSubscription
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
    private val cryptoSigningKey = mock<CryptoSigningKey> {
        on { schemeCodeName } doReturn RSA_CODE_NAME
    }

    private val cryptoOpsClient: CryptoOpsClient = mock {
        on { generateKeyPair(any(), any(), any(), any(), any<Map<String, String>>()) } doReturn defaultKey
        on {
            generateKeyPair(any(), any(), eq("${aliceId.value}-LEDGER"), any(), any<Map<String, String>>())
        } doReturn aliceKey
        on {
            generateKeyPair(any(), any(), eq("${bobId.value}-LEDGER"), any(), any<Map<String, String>>())
        } doReturn bobKey
        on {
            generateKeyPair(any(), any(), eq("${charlieId.value}-LEDGER"), any(), any<Map<String, String>>())
        } doReturn charlieKey
        on { lookup(any(), any()) } doReturn listOf(cryptoSigningKey)
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

    private val layeredPropertyMapFactory = LayeredPropertyMapMocks.createFactory(
        listOf(
            EndpointInfoConverter(),
            MemberNotaryDetailsConverter(keyEncodingService),
            PublicKeyConverter(keyEncodingService),
            PublicKeyHashConverter()
        )
    )
    private val memberInfoFactory: MemberInfoFactory = MemberInfoFactoryImpl(layeredPropertyMapFactory)

    private val hsmRegistrationClient: HSMRegistrationClient = mock()

    private val mockContext: Map<String, String> = mock {
        on { get(KEY_SCHEME) } doReturn ECDSA_SECP256R1_CODE_NAME
    }
    private val persistenceClient = mock<MembershipPersistenceClient>()
    private val keyValuePairListSerializer: CordaAvroSerializer<KeyValuePairList> = mock {
        on { serialize(any()) } doReturn byteArrayOf(1, 2, 3)
    }
    private val cordaAvroSerializationFactory = mock<CordaAvroSerializationFactory> {
        on { createAvroSerializer<KeyValuePairList>(any()) } doReturn keyValuePairListSerializer
    }

    private val membershipSchemaValidator: MembershipSchemaValidator = mock()
    private val membershipSchemaValidatorFactory: MembershipSchemaValidatorFactory = mock {
        on { createValidator() } doReturn membershipSchemaValidator
    }
    private val endpointInfoFactory: EndpointInfoFactory = mock {
        on { create(any(), any()) } doAnswer { invocation ->
            mock {
                on { this.url } doReturn invocation.getArgument(0)
                on { this.protocolVersion } doReturn invocation.getArgument(1)
            }
        }
    }
    private val platformInfoProvider = buildMockPlatformInfoProvider()

    private val virtualNodeInfo = buildTestVirtualNodeInfo(alice)
    private val virtualNodeInfoReadService: VirtualNodeInfoReadService = mock {
        on { get(eq(alice)) } doReturn virtualNodeInfo
    }
    private val mockGroupParameters: GroupParameters = mock()
    private val groupParametersFactory: GroupParametersFactory = mock {
        on { create(any()) } doReturn mockGroupParameters
    }
    private val groupParametersWriterService: GroupParametersWriterService = mock()

    private val registrationService = StaticMemberRegistrationService(
        groupPolicyProvider,
        publisherFactory,
        subscriptionFactory,
        keyEncodingService,
        cryptoOpsClient,
        configurationReadService,
        lifecycleCoordinatorFactory,
        hsmRegistrationClient,
        memberInfoFactory,
        persistenceClient,
        cordaAvroSerializationFactory,
        membershipSchemaValidatorFactory,
        endpointInfoFactory,
        platformInfoProvider,
        groupParametersFactory,
        virtualNodeInfoReadService,
        groupParametersWriterService,
    )

    private fun setUpPublisher() {
        // kicks off the MessagingConfigurationReceived event to be able to mock the Publisher
        registrationServiceLifecycleHandler?.processEvent(
            ConfigChangedEvent(setOf(ConfigKeys.BOOT_CONFIG, ConfigKeys.MESSAGING_CONFIG), configs),
            coordinator
        )
    }

    @Nested
    inner class SuccessfulRegistrationTests {
        @Test
        fun `during registration, the registering static member inside the GroupPolicy file gets parsed and published`() {
            setUpPublisher()
            registrationService.start()
            val capturedPublishedList = argumentCaptor<List<Record<String, Any>>>()
            val registrationResult = registrationService.register(registrationId, alice, mockContext)
            verify(mockPublisher, times(2)).publish(capturedPublishedList.capture())
            verify(hsmRegistrationClient).assignSoftHSM(aliceId.value, LEDGER)
            verify(cryptoOpsClient).generateKeyPair(any(), eq(LEDGER), any(), any(), any<Map<String, String>>())

            (CryptoConsts.Categories.all.minus(listOf(LEDGER))).forEach {
                verify(hsmRegistrationClient, never()).assignSoftHSM(aliceId.value, it)
                verify(cryptoOpsClient, never()).generateKeyPair(any(), eq(it), any(), any(), any<Map<String, String>>())
            }
            registrationService.stop()

            val publishedList = capturedPublishedList.firstValue
            assertEquals(4, publishedList.size)

            publishedList.take(3).forEach {
                assertTrue(it.key.startsWith(aliceId.value) || it.key.startsWith(bobId.value)
                        || it.key.startsWith(charlieId.value))
                assertTrue(it.key.endsWith(aliceId.value))
            }

            val publishedInfo = publishedList.first()

            assertEquals(Schemas.Membership.MEMBER_LIST_TOPIC, publishedInfo.topic)
            val persistentMemberPublished = publishedInfo.value as PersistentMemberInfo
            val memberPublished = memberInfoFactory.create(
                persistentMemberPublished.memberContext.toSortedMap(),
                persistentMemberPublished.mgmContext.toSortedMap()
            )
            assertEquals(DUMMY_GROUP_ID, memberPublished.groupId)
            assertEquals(TEST_SOFTWARE_VERSION, memberPublished.softwareVersion)
            assertEquals(TEST_PLATFORM_VERSION, memberPublished.platformVersion)
            assertEquals(TEST_CPI_NAME, memberPublished.cpiInfo.name)
            assertEquals(TEST_CPI_VERSION, memberPublished.cpiInfo.version)
            assertEquals(testCpiSignerSummaryHash, memberPublished.cpiInfo.signerSummaryHash)
            assertNotNull(memberPublished.serial)
            assertNotNull(memberPublished.modifiedTime)

            assertEquals(aliceKey, memberPublished.sessionInitiationKey)
            assertEquals(1, memberPublished.ledgerKeys.size)
            assertEquals(1, memberPublished.ledgerKeyHashes.size)
            assertEquals(aliceKey.calculateHash(), memberPublished.ledgerKeyHashes.first())
            assertEquals(MEMBER_STATUS_ACTIVE, memberPublished.status)
            assertEquals(1, memberPublished.endpoints.size)

            // we publish the hosted identity as the last item
            val publishedHostedIdentity = publishedList.last()

            assertEquals(alice.shortHash.value, publishedHostedIdentity.key)
            assertEquals(P2P_HOSTED_IDENTITIES_TOPIC, publishedHostedIdentity.topic)
            val hostedIdentityPublished = publishedHostedIdentity.value as HostedIdentityEntry
            assertEquals(alice.groupId, hostedIdentityPublished.holdingIdentity.groupId)
            assertEquals(alice.x500Name.toString(), hostedIdentityPublished.holdingIdentity.x500Name)

            assertEquals(MembershipRequestRegistrationResult(SUBMITTED), registrationResult)
        }

        @Test
        fun `during registration, distinct keys are generated for session and ledger if configured that way in the group policy`() {
            whenever(groupPolicyProvider.getGroupPolicy(eq(alice)))
                .doReturn(groupPolicyWithStaticNetworkAndDistinctKeys)
            setUpPublisher()
            registrationService.start()
            registrationService.register(registrationId, alice, mockContext)
            verify(hsmRegistrationClient).assignSoftHSM(aliceId.value, LEDGER)
            verify(hsmRegistrationClient).assignSoftHSM(aliceId.value, SESSION_INIT)
            verify(cryptoOpsClient).generateKeyPair(any(), eq(LEDGER), any(), any(), any<Map<String, String>>())
            verify(cryptoOpsClient).generateKeyPair(any(), eq(SESSION_INIT), any(), any(), any<Map<String, String>>())

            (CryptoConsts.Categories.all.minus(listOf(SESSION_INIT, LEDGER))).forEach {
                verify(hsmRegistrationClient, never()).assignSoftHSM(aliceId.value, it)
                verify(cryptoOpsClient, never()).generateKeyPair(any(), eq(it), any(), any(), any<Map<String, String>>())
            }
            registrationService.stop()
        }

        @Test
        fun `registration persist the status`() {
            val status = argumentCaptor<RegistrationRequest>()
            whenever(
                persistenceClient.persistRegistrationRequest(
                    eq(alice),
                    status.capture()
                )
            ).doReturn(MembershipPersistenceResult.success())
            setUpPublisher()
            registrationService.start()

            registrationService.register(registrationId, alice, mockContext)

            assertThat(status.firstValue.status).isEqualTo(RegistrationStatus.APPROVED)
        }

        @Test
        fun `registration persists group parameters for registering member`() {
            val knownIdentity = HoldingIdentity(aliceName, "test-group")
            val status = argumentCaptor<GroupParameters>()
            whenever(
                persistenceClient.persistGroupParameters(
                    any(),
                    status.capture()
                )
            ).doReturn(MembershipPersistenceResult.Success(mock()))
            whenever(groupPolicyProvider.getGroupPolicy(knownIdentity)).thenReturn(groupPolicyWithStaticNetwork)
            whenever(virtualNodeInfoReadService.get(knownIdentity)).thenReturn(buildTestVirtualNodeInfo(knownIdentity))
            setUpPublisher()
            registrationService.start()

            registrationService.register(registrationId, knownIdentity, mockContext)

            assertThat(status.firstValue).isEqualTo(mockGroupParameters)
        }

        @Test
        fun `registration publishes group parameters to Kafka for registering member`() {
            val knownIdentity = HoldingIdentity(aliceName, "test-group")
            val status = argumentCaptor<GroupParameters>()
            doNothing().whenever(groupParametersWriterService).put(any(), status.capture())
            whenever(groupPolicyProvider.getGroupPolicy(knownIdentity)).thenReturn(groupPolicyWithStaticNetwork)
            whenever(virtualNodeInfoReadService.get(knownIdentity)).thenReturn(buildTestVirtualNodeInfo(knownIdentity))
            setUpPublisher()
            registrationService.start()

            registrationService.register(registrationId, knownIdentity, mockContext)

            assertThat(status.firstValue).isEqualTo(mockGroupParameters)
        }
    }

    @Nested
    inner class FailedRegistrationTests {
        @Test
        fun `registration fails when name field is empty in the GroupPolicy file`() {
            setUpPublisher()
            registrationService.start()
            val registrationResult = registrationService.register(registrationId, bob, mockContext)
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
            val registrationResult = registrationService.register(registrationId, charlie, mockContext)
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
            val registrationResult = registrationService.register(registrationId, eric, mockContext)
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
            val registrationResult = registrationService.register(registrationId, alice, mockContext)
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
            val registrationResult = registrationService.register(registrationId, daisy, mockContext)
            assertEquals(
                MembershipRequestRegistrationResult(
                    NOT_SUBMITTED,
                    "Registration failed. Reason: Our membership O=Daisy, L=London, C=GB " +
                            "is not listed in the static member list."
                ),
                registrationResult
            )
            registrationService.stop()
        }

        @Test
        fun `registration fails when key scheme is not provided in context`() {
            setUpPublisher()
            registrationService.start()
            val registrationResult = registrationService.register(registrationId, alice, mock())
            assertEquals(
                MembershipRequestRegistrationResult(
                    NOT_SUBMITTED,
                    "Registration failed. Reason: Key scheme must be specified."
                ),
                registrationResult
            )
            registrationService.stop()
        }

        @Test
        fun `registration fails when notary role has missing information`() {
            setUpPublisher()
            registrationService.start()
            val context = mapOf(
                KEY_SCHEME to ECDSA_SECP256R1_CODE_NAME,
                "corda.roles.0" to "notary",
            )

            val registrationResult = registrationService.register(registrationId, alice, context)

            assertThat(registrationResult.outcome).isEqualTo(NOT_SUBMITTED)
        }

        @Test
        fun `registration fails if the registration context doesn't match the schema`() {
            setUpPublisher()
            val err = "ERROR-MESSAGE"
            val errReason = "ERROR-REASON"
            whenever(
                membershipSchemaValidator.validateRegistrationContext(
                    eq(MembershipSchema.RegistrationContextSchema.StaticMember),
                    any(),
                    any()
                )
            ).doThrow(
                MembershipSchemaValidationException(
                    err,
                    null,
                    MembershipSchema.RegistrationContextSchema.DynamicMember,
                    listOf(errReason)
                )
            )

            registrationService.start()
            val result = registrationService.register(registrationId, alice, mockContext)
            assertSoftly {
                it.assertThat(result.outcome).isEqualTo(NOT_SUBMITTED)
                it.assertThat(result.message).contains(err)
                it.assertThat(result.message).contains(errReason)
            }
            registrationService.stop()
        }

        @Test
        fun `registration fails when virtual node info is unavailable`() {
            setUpPublisher()
            registrationService.start()
            whenever(virtualNodeInfoReadService.get((alice))).thenReturn(null)

            val registrationResult = registrationService.register(registrationId, alice, mockContext)

            assertThat(registrationResult.outcome).isEqualTo(NOT_SUBMITTED)
            assertThat(registrationResult.message).isNotNull.contains("Could not find virtual node")
        }
    }

    @Nested
    inner class NotaryRoleTests {
        @Test
        fun `registration submitted when context has notary role`() {
            setUpPublisher()
            registrationService.start()
            val context = mapOf(
                KEY_SCHEME to ECDSA_SECP256R1_CODE_NAME,
                "corda.roles.0" to "notary",
                "corda.notary.service.name" to "O=MyNotaryService, L=London, C=GB",
                "corda.notary.service.plugin" to "net.corda.notary.MyNotaryService",
            )

            val registrationResult = registrationService.register(registrationId, alice, context)

            assertThat(registrationResult.outcome).isEqualTo(SUBMITTED)
        }

        @Test
        fun `registration not submitted when context has un known role`() {
            setUpPublisher()
            registrationService.start()
            val context = mapOf(
                KEY_SCHEME to ECDSA_SECP256R1_CODE_NAME,
                "corda.roles.0" to "nop",
            )

            val registrationResult = registrationService.register(registrationId, alice, context)

            assertThat(registrationResult.outcome).isEqualTo(NOT_SUBMITTED)
        }

        @Test
        fun `registration adds notary info to member info`() {
            val capturedPublishedList = argumentCaptor<List<Record<String, Any>>>()
            whenever(mockPublisher.publish(capturedPublishedList.capture())).doReturn(emptyList())
            setUpPublisher()
            registrationService.start()
            val context = mapOf(
                KEY_SCHEME to ECDSA_SECP256R1_CODE_NAME,
                "corda.roles.0" to "notary",
                "corda.notary.service.name" to "O=MyNotaryService, L=London, C=GB",
                "corda.notary.service.plugin" to "net.corda.notary.MyNotaryService",
            )

            registrationService.register(registrationId, alice, context)

            val persistentMemberPublished =
                capturedPublishedList.firstValue.firstOrNull()?.value as PersistentMemberInfo
            val memberInfo = memberInfoFactory.create(
                persistentMemberPublished.memberContext.toSortedMap(),
                persistentMemberPublished.mgmContext.toSortedMap()
            )
            val notaryDetails = memberInfo.notaryDetails
            assertSoftly {
                assertThat(notaryDetails).isNotNull
                assertThat(notaryDetails?.serviceName)
                    .isEqualTo(MemberX500Name.parse("O=MyNotaryService, L=London, C=GB"))
                assertThat(notaryDetails?.servicePlugin).isEqualTo("net.corda.notary.MyNotaryService")

                assertThat(notaryDetails?.keys?.toList())
                    .hasSize(1)
                    .allMatch {
                        it.publicKey == defaultKey
                    }
                    .allMatch {
                        it.publicKeyHash == PublicKeyHash.calculate(defaultKey)
                    }
                    .allMatch {
                        it.spec.signatureName == SignatureSpec.RSA_SHA512.signatureName
                    }
            }
        }

        @Test
        fun `registration without notary will not add notary to member info`() {
            val capturedPublishedList = argumentCaptor<List<Record<String, Any>>>()
            whenever(mockPublisher.publish(capturedPublishedList.capture())).doReturn(emptyList())
            setUpPublisher()
            registrationService.start()
            val context = mapOf(
                KEY_SCHEME to ECDSA_SECP256R1_CODE_NAME,
            )

            registrationService.register(registrationId, alice, context)

            val persistentMemberPublished =
                capturedPublishedList.firstValue.firstOrNull()?.value as PersistentMemberInfo
            val memberInfo = memberInfoFactory.create(
                persistentMemberPublished.memberContext.toSortedMap(),
                persistentMemberPublished.mgmContext.toSortedMap()
            )
            val notaryDetails = memberInfo.notaryDetails
            assertThat(notaryDetails)
                .isNull()
        }

        @Test
        fun `registration with notary role persists group parameters for all members who have vnodes set up`() {
            val context = mapOf(
                KEY_SCHEME to ECDSA_SECP256R1_CODE_NAME,
                "corda.roles.0" to "notary",
                "corda.notary.service.name" to "O=MyNotaryService, L=London, C=GB",
                "corda.notary.service.plugin" to "net.corda.notary.MyNotaryService",
            )
            whenever(
                persistenceClient.persistGroupParameters(
                    any(),
                    any()
                )
            ).doReturn(MembershipPersistenceResult.Success(mock()))
            whenever(groupPolicyProvider.getGroupPolicy(bob)).thenReturn(groupPolicyWithStaticNetwork)
            whenever(virtualNodeInfoReadService.get(bob)).thenReturn(buildTestVirtualNodeInfo(bob))
            setUpPublisher()
            registrationService.start()

            registrationService.register(registrationId, bob, context)

            verify(persistenceClient, times(1)).persistGroupParameters(eq(bob), eq(mockGroupParameters))
            verify(persistenceClient, times(1)).persistGroupParameters(eq(alice), eq(mockGroupParameters))
            verify(persistenceClient, never()).persistGroupParameters(eq(charlie), eq(mockGroupParameters))
        }

        @Test
        fun `registration with notary role publishes group parameters to Kafka for all members who have vnodes set up`() {
            val context = mapOf(
                KEY_SCHEME to ECDSA_SECP256R1_CODE_NAME,
                "corda.roles.0" to "notary",
                "corda.notary.service.name" to "O=MyNotaryService, L=London, C=GB",
                "corda.notary.service.plugin" to "net.corda.notary.MyNotaryService",
            )
            whenever(groupPolicyProvider.getGroupPolicy(bob)).thenReturn(groupPolicyWithStaticNetwork)
            whenever(virtualNodeInfoReadService.get(bob)).thenReturn(buildTestVirtualNodeInfo(bob))
            setUpPublisher()
            registrationService.start()

            registrationService.register(registrationId, bob, context)

            verify(groupParametersWriterService).put(eq(bob), eq(mockGroupParameters))
            verify(groupParametersWriterService).put(eq(alice), eq(mockGroupParameters))
            verify(groupParametersWriterService, never()).put(eq(charlie), eq(mockGroupParameters))
        }
    }

    @Nested
    inner class LifecycleTests {
        @Test
        fun `starting and stopping the service succeeds`() {
            registrationService.start()
            assertTrue(registrationService.isRunning)
            registrationService.stop()
            assertFalse(registrationService.isRunning)
        }
    }
}