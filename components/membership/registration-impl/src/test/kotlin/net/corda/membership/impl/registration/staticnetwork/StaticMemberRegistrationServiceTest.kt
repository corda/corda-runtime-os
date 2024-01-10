package net.corda.membership.impl.registration.staticnetwork

import net.corda.configuration.read.ConfigChangedEvent
import net.corda.configuration.read.ConfigurationReadService
import net.corda.crypto.cipher.suite.KeyEncodingService
import net.corda.crypto.cipher.suite.SignatureSpecs
import net.corda.crypto.client.CryptoOpsClient
import net.corda.crypto.client.hsm.HSMRegistrationClient
import net.corda.crypto.core.CryptoConsts
import net.corda.crypto.core.CryptoConsts.Categories.LEDGER
import net.corda.crypto.core.CryptoConsts.Categories.SESSION_INIT
import net.corda.crypto.core.fullIdHash
import net.corda.crypto.impl.converter.PublicKeyConverter
import net.corda.crypto.impl.converter.PublicKeyHashConverter
import net.corda.avro.serialization.CordaAvroDeserializer
import net.corda.avro.serialization.CordaAvroSerializationFactory
import net.corda.avro.serialization.CordaAvroSerializer
import net.corda.data.KeyValuePair
import net.corda.data.KeyValuePairList
import net.corda.data.crypto.wire.CryptoSigningKey
import net.corda.data.membership.PersistentMemberInfo
import net.corda.data.membership.StaticNetworkInfo
import net.corda.data.membership.common.RegistrationRequestDetails
import net.corda.data.membership.common.v2.RegistrationStatus
import net.corda.data.p2p.HostedIdentityEntry
import net.corda.layeredpropertymap.testkit.LayeredPropertyMapMocks
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleStatus
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
import net.corda.membership.impl.registration.staticnetwork.TestUtils.Companion.groupPolicyWithStaticNetworkAndDuplicatedVNodeName
import net.corda.membership.impl.registration.staticnetwork.TestUtils.Companion.groupPolicyWithoutStaticNetwork
import net.corda.membership.impl.registration.testCpiSignerSummaryHash
import net.corda.membership.lib.EndpointInfoFactory
import net.corda.membership.lib.GroupParametersFactory
import net.corda.membership.lib.GroupParametersNotaryUpdater.Companion.EPOCH_KEY
import net.corda.membership.lib.MemberInfoExtension
import net.corda.membership.lib.MemberInfoExtension.Companion.CUSTOM_KEY_PREFIX
import net.corda.membership.lib.MemberInfoExtension.Companion.MEMBER_STATUS_ACTIVE
import net.corda.membership.lib.MemberInfoExtension.Companion.NOTARY_SERVICE_NAME
import net.corda.membership.lib.MemberInfoExtension.Companion.NOTARY_SERVICE_PROTOCOL
import net.corda.membership.lib.MemberInfoExtension.Companion.NOTARY_SERVICE_PROTOCOL_VERSIONS
import net.corda.membership.lib.MemberInfoExtension.Companion.ROLES_PREFIX
import net.corda.membership.lib.MemberInfoExtension.Companion.cpiInfo
import net.corda.membership.lib.MemberInfoExtension.Companion.endpoints
import net.corda.membership.lib.MemberInfoExtension.Companion.groupId
import net.corda.membership.lib.MemberInfoExtension.Companion.ledgerKeyHashes
import net.corda.membership.lib.MemberInfoExtension.Companion.modifiedTime
import net.corda.membership.lib.MemberInfoExtension.Companion.notaryDetails
import net.corda.membership.lib.MemberInfoExtension.Companion.sessionInitiationKeys
import net.corda.membership.lib.MemberInfoExtension.Companion.softwareVersion
import net.corda.membership.lib.MemberInfoExtension.Companion.status
import net.corda.membership.lib.MemberInfoFactory
import net.corda.membership.lib.SignedGroupParameters
import net.corda.membership.lib.UnsignedGroupParameters
import net.corda.membership.lib.impl.MemberInfoFactoryImpl
import net.corda.membership.lib.impl.converter.EndpointInfoConverter
import net.corda.membership.lib.impl.converter.MemberNotaryDetailsConverter
import net.corda.membership.lib.notary.MemberNotaryDetails
import net.corda.membership.lib.registration.RegistrationRequest
import net.corda.membership.lib.schema.validation.MembershipSchemaValidationException
import net.corda.membership.lib.schema.validation.MembershipSchemaValidator
import net.corda.membership.lib.schema.validation.MembershipSchemaValidatorFactory
import net.corda.membership.lib.toMap
import net.corda.membership.network.writer.staticnetwork.StaticNetworkUtils
import net.corda.membership.persistence.client.MembershipPersistenceClient
import net.corda.membership.persistence.client.MembershipPersistenceOperation
import net.corda.membership.persistence.client.MembershipPersistenceResult
import net.corda.membership.persistence.client.MembershipQueryClient
import net.corda.membership.persistence.client.MembershipQueryResult
import net.corda.membership.read.MembershipGroupReader
import net.corda.membership.read.MembershipGroupReaderProvider
import net.corda.membership.read.NotaryVirtualNodeLookup
import net.corda.membership.registration.InvalidMembershipRegistrationException
import net.corda.membership.registration.MembershipRegistrationException
import net.corda.membership.registration.NotReadyMembershipRegistrationException
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.records.Record
import net.corda.schema.Schemas
import net.corda.schema.Schemas.P2P.P2P_HOSTED_IDENTITIES_TOPIC
import net.corda.schema.configuration.ConfigKeys
import net.corda.schema.membership.MembershipSchema
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.crypto.KeySchemeCodes.ECDSA_SECP256R1_CODE_NAME
import net.corda.v5.crypto.KeySchemeCodes.RSA_CODE_NAME
import net.corda.v5.membership.MemberContext
import net.corda.v5.membership.MemberInfo
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.SoftAssertions.assertSoftly
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.spy
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.lang.StringBuilder
import java.nio.ByteBuffer
import java.security.KeyPairGenerator
import java.security.PublicKey
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import net.corda.data.membership.SignedGroupParameters as AvroGroupParameters

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

    private val notary = MemberX500Name.parse("O=MyNotaryService, L=London, C=GB")

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
    private class SuccessOperation<T>(
        private val result: T,
    ) : MembershipPersistenceOperation<T> {
        override fun execute() = MembershipPersistenceResult.Success(result)

        override fun createAsyncCommands() = emptyList<Record<*, *>>()
    }
    private class FailedOperation<T> : MembershipPersistenceOperation<T> {
        override fun execute() = MembershipPersistenceResult.Failure<T>("Failed")

        override fun createAsyncCommands() = emptyList<Record<*, *>>()
    }

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
        on { lookupKeysByIds(any(), any()) } doReturn listOf(cryptoSigningKey)
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

    private val hsmRegistrationClient: HSMRegistrationClient = mock()

    private val mockContext = mapOf(KEY_SCHEME to ECDSA_SECP256R1_CODE_NAME)
    private val persistRegistrationRequestOperation = mock<MembershipPersistenceOperation<Unit>> {
        on { execute() } doReturn MembershipPersistenceResult.success()
    }
    private val persistenceClient = mock<MembershipPersistenceClient> {
        on { persistGroupParameters(any(), any()) } doReturn SuccessOperation(mockSignedGroupParameters)
        on { persistRegistrationRequest(any(), any()) } doReturn persistRegistrationRequestOperation
        on { updateStaticNetworkInfo(any()) } doAnswer { SuccessOperation(it.getArgument(0)) }
    }
    private val keyValuePairListSerializer: CordaAvroSerializer<KeyValuePairList> = mock {
        on { serialize(any()) } doReturn byteArrayOf(1, 2, 3)
    }
    private val keyValuePairListDeserializer: CordaAvroDeserializer<KeyValuePairList> = mock {
        on { deserialize(any()) } doReturn KeyValuePairList(
            listOf(
                KeyValuePair(EPOCH_KEY, "1")
            )
        )
    }
    private val cordaAvroSerializationFactory = mock<CordaAvroSerializationFactory> {
        on { createAvroSerializer<KeyValuePairList>(any()) } doReturn keyValuePairListSerializer
        on { createAvroDeserializer(any(), eq(KeyValuePairList::class.java)) } doReturn keyValuePairListDeserializer
    }
    private val memberInfoFactory: MemberInfoFactory = spy(
        MemberInfoFactoryImpl(
            layeredPropertyMapFactory,
            cordaAvroSerializationFactory,
        )
    )

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
    private val serializedGroupParameters = "group-params".toByteArray()
    private val mockSignedGroupParameters: SignedGroupParameters = mock {
        on { groupParameters } doReturn serializedGroupParameters
    }
    private val mockUnsignedGroupParameters: UnsignedGroupParameters = mock {
        on { groupParameters } doReturn serializedGroupParameters
    }
    private val groupParametersFactory: GroupParametersFactory = mock {
        on { create(any<AvroGroupParameters>()) } doReturn mockSignedGroupParameters
        on { create(any<KeyValuePairList>()) } doReturn mockUnsignedGroupParameters
    }
    private val staticNetworkMgmKeyPair = KeyPairGenerator
        .getInstance(StaticNetworkUtils.mgmSigningKeyAlgorithm, StaticNetworkUtils.mgmSigningKeyProvider)
        .genKeyPair()
    private val currentStaticNetworkInfo = mock<StaticNetworkInfo> {
        on { groupParameters } doReturn KeyValuePairList(listOf(KeyValuePair(EPOCH_KEY, "1")))
        on { groupId } doReturn DUMMY_GROUP_ID
        on { version } doReturn 1
        on { mgmPublicSigningKey } doReturn ByteBuffer.wrap(staticNetworkMgmKeyPair.public.encoded)
        on { mgmPrivateSigningKey } doReturn ByteBuffer.wrap(staticNetworkMgmKeyPair.private.encoded)
    }

    private val membershipQueryClient = mock<MembershipQueryClient> {
        on {
            queryRegistrationRequests(
                any(),
                any(),
                any(),
                anyOrNull(),
            )
        } doReturn MembershipQueryResult.Success(emptyList())

        on { queryStaticNetworkInfo(any()) } doReturn MembershipQueryResult.Success(currentStaticNetworkInfo)
    }
    private val notaryVirtualNodeLookup = mock<NotaryVirtualNodeLookup> {
        on { getNotaryVirtualNodes(any()) } doReturn emptyList()
    }
    private val groupReader: MembershipGroupReader = mock {
        on { notaryVirtualNodeLookup } doReturn notaryVirtualNodeLookup
    }
    private val membershipGroupReaderProvider: MembershipGroupReaderProvider = mock {
        on { getGroupReader(any()) } doReturn groupReader
    }

    private val registrationService = StaticMemberRegistrationService(
        groupPolicyProvider,
        publisherFactory,
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
        membershipGroupReaderProvider,
        membershipQueryClient,
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
            val capturedMemberInfos = argumentCaptor<MemberInfo>()
            val capturedPublishedList = argumentCaptor<List<Record<String, Any>>>()
            registrationService.register(registrationId, alice, mockContext)
            verify(mockPublisher).publish(capturedPublishedList.capture())
            verify(hsmRegistrationClient).assignSoftHSM(aliceId.value, LEDGER)
            verify(cryptoOpsClient).generateKeyPair(any(), eq(LEDGER), any(), any(), any<Map<String, String>>())
            verify(memberInfoFactory, times(3))
                .createMgmOrStaticPersistentMemberInfo(any(), capturedMemberInfos.capture(), any(), any())

            (CryptoConsts.Categories.all.minus(listOf(LEDGER))).forEach {
                verify(hsmRegistrationClient, never()).assignSoftHSM(aliceId.value, it)
                verify(cryptoOpsClient, never()).generateKeyPair(
                    any(),
                    eq(it),
                    any(),
                    any(),
                    any<Map<String, String>>()
                )
            }
            registrationService.stop()

            val publishedList = capturedPublishedList.firstValue
            assertEquals(4, publishedList.size)

            publishedList.take(3).forEach {
                assertTrue(
                    it.key.startsWith(aliceId.value) || it.key.startsWith(bobId.value)
                            || it.key.startsWith(charlieId.value)
                )
                assertTrue(it.key.endsWith(aliceId.value))
            }

            val publishedInfo = publishedList.first()

            assertEquals(Schemas.Membership.MEMBER_LIST_TOPIC, publishedInfo.topic)
            assertThat(publishedInfo.value).isInstanceOf(PersistentMemberInfo::class.java)
            val aliceInfo = capturedMemberInfos.allValues.first {
                it.name == aliceName
            }
            with(aliceInfo) {
                assertEquals(DUMMY_GROUP_ID, groupId)
                assertEquals(TEST_SOFTWARE_VERSION, softwareVersion)
                assertEquals(TEST_PLATFORM_VERSION, platformVersion)
                assertEquals(TEST_CPI_NAME, cpiInfo.name)
                assertEquals(TEST_CPI_VERSION, cpiInfo.version)
                assertEquals(testCpiSignerSummaryHash, cpiInfo.signerSummaryHash)
                assertNotNull(serial)
                assertNotNull(modifiedTime)

                assertEquals(aliceKey, sessionInitiationKeys.first())
                assertEquals(1, ledgerKeys.size)
                assertEquals(1, ledgerKeyHashes.size)
                assertEquals(aliceKey.fullIdHash(), ledgerKeyHashes.first())
                assertEquals(MEMBER_STATUS_ACTIVE, status)
                assertEquals(1, endpoints.size)
            }

            // we publish the hosted identity as the last item
            val publishedHostedIdentity = publishedList.last()

            assertEquals(alice.shortHash.value, publishedHostedIdentity.key)
            assertEquals(P2P_HOSTED_IDENTITIES_TOPIC, publishedHostedIdentity.topic)
            val hostedIdentityPublished = publishedHostedIdentity.value as HostedIdentityEntry
            assertEquals(alice.groupId, hostedIdentityPublished.holdingIdentity.groupId)
            assertEquals(alice.x500Name.toString(), hostedIdentityPublished.holdingIdentity.x500Name)
            assertEquals(ALICE_KEY, hostedIdentityPublished.preferredSessionKeyAndCert.sessionPublicKey)
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
                verify(cryptoOpsClient, never()).generateKeyPair(
                    any(),
                    eq(it),
                    any(),
                    any(),
                    any<Map<String, String>>()
                )
            }
            registrationService.stop()
        }

        @Test
        fun `registration persist the status`() {
            val capturedRequest = argumentCaptor<RegistrationRequest>()
            whenever(
                persistenceClient.persistRegistrationRequest(
                    eq(alice),
                    capturedRequest.capture()
                )
            ).doReturn(persistRegistrationRequestOperation)
            setUpPublisher()
            registrationService.start()

            registrationService.register(registrationId, alice, mockContext)

            val registrationRequest = capturedRequest.firstValue
            assertThat(registrationRequest.status).isEqualTo(RegistrationStatus.APPROVED)
            assertThat(registrationRequest.serial).isEqualTo(0L)
        }

        @Test
        fun `registration persists group parameters for registering member`() {
            val knownIdentity = HoldingIdentity(aliceName, "test-group")
            val status = argumentCaptor<SignedGroupParameters>()
            whenever(
                persistenceClient.persistGroupParameters(
                    any(),
                    status.capture()
                )
            ).doReturn(SuccessOperation(mock()))
            whenever(groupPolicyProvider.getGroupPolicy(knownIdentity)).thenReturn(groupPolicyWithStaticNetwork)
            whenever(virtualNodeInfoReadService.get(knownIdentity)).thenReturn(buildTestVirtualNodeInfo(knownIdentity))
            setUpPublisher()
            registrationService.start()

            registrationService.register(registrationId, knownIdentity, mockContext)

            assertThat(status.firstValue).isEqualTo(mockSignedGroupParameters)
        }

        @Test
        fun `registration pass when the member is not found`() {
            whenever(
                membershipQueryClient.queryRegistrationRequests(
                    alice,
                    aliceName,
                    listOf(RegistrationStatus.APPROVED),
                )
            ).doReturn(MembershipQueryResult.Success(emptyList()))
            setUpPublisher()
            registrationService.start()

            assertDoesNotThrow {
                registrationService.register(registrationId, alice, mockContext)
            }
        }

        @Test
        fun `registration pass when role is set to notary and notary service name already exists with the same name`() {
            setUpPublisher()
            registrationService.start()
            val context = mapOf(
                KEY_SCHEME to ECDSA_SECP256R1_CODE_NAME,
                "${ROLES_PREFIX}.0" to "notary",
                NOTARY_SERVICE_NAME to notary.toString(),
                NOTARY_SERVICE_PROTOCOL to "net.corda.notary.MyNotaryService",
                String.format(NOTARY_SERVICE_PROTOCOL_VERSIONS, 0) to "1",
            )
            val mockNotaryDetails = MemberNotaryDetails(
                notary,
                null,
                emptyList(),
                emptyList(),
                true
            )
            val mockMemberContext: MemberContext = mock {
                on { entries } doReturn mapOf(
                    String.format(ROLES_PREFIX, 0) to MemberInfoExtension.NOTARY_ROLE
                ).entries
                on { parse(eq("corda.notary"), eq(MemberNotaryDetails::class.java)) } doReturn mockNotaryDetails
            }
            val mockNotaryMember: MemberInfo = mock {
                on { memberProvidedContext } doReturn mockMemberContext
                on { name } doReturn aliceName
            }
            whenever(groupReader.lookup()).thenReturn(listOf(mockNotaryMember))

            assertDoesNotThrow {
                registrationService.register(registrationId, alice, context)
            }
        }

        @Test
        fun `registration successfully adds custom fields from context to member info`() {
            val mockContextWithCustomFields = mockContext + mapOf(
                "$CUSTOM_KEY_PREFIX.key1" to "value1",
                "$CUSTOM_KEY_PREFIX.key2" to "value2",
            )
            val expectedContextEntries = mockContextWithCustomFields.filterNot { it.key == KEY_SCHEME }
            setUpPublisher()
            registrationService.start()

            registrationService.register(registrationId, alice, mockContextWithCustomFields)

            val capturedMemberInfos = argumentCaptor<MemberInfo>()
            verify(memberInfoFactory, times(3))
                .createMgmOrStaticPersistentMemberInfo(any(), capturedMemberInfos.capture(), any(), any())
            assertThat(
                capturedMemberInfos.allValues.first { it.name == aliceName }.memberProvidedContext.toMap()
            ).containsAllEntriesOf(expectedContextEntries)
        }
    }

    @Nested
    inner class FailedRegistrationTests {
        @Test
        fun `it fails when the member is active in DB`() {
            val status = mock<RegistrationRequestDetails> {
                on { registrationId } doReturn "ID"
            }
            whenever(
                membershipQueryClient.queryRegistrationRequests(
                    alice,
                    aliceName,
                    listOf(RegistrationStatus.APPROVED),
                )
            ).doReturn(MembershipQueryResult.Success(listOf(status)))
            setUpPublisher()
            registrationService.start()

            assertThrows<InvalidMembershipRegistrationException> {
                registrationService.register(registrationId, alice, mockContext)
            }
        }

        @Test
        fun `it fails when the member is active in group reader`() {
            val activeMemberInfo = mock<MemberInfo> {
                on { isActive } doReturn true
            }
            whenever(groupReader.lookup(aliceName)).doReturn(activeMemberInfo)
            setUpPublisher()
            registrationService.start()

            assertThrows<InvalidMembershipRegistrationException> {
                registrationService.register(registrationId, alice, mockContext)
            }
        }

        @Test
        fun `registration fails when name field is empty in the GroupPolicy file`() {
            setUpPublisher()
            registrationService.start()

            val exception = assertThrows<InvalidMembershipRegistrationException> {
                registrationService.register(registrationId, bob, mockContext)
            }

            assertThat(exception).hasMessageContaining(
                "Registration failed. Reason: Member's name is not provided in static member list."
            )
            registrationService.stop()
        }

        @Test
        fun `registration fails when static network is missing`() {
            setUpPublisher()
            registrationService.start()

            val exception = assertThrows<InvalidMembershipRegistrationException> {
                registrationService.register(registrationId, charlie, mockContext)
            }

            assertThat(exception)
                .hasMessage("Registration failed. Reason: Could not find static member list in group policy file.")
            registrationService.stop()
        }

        @Test
        fun `registration fails when static network is empty`() {
            setUpPublisher()
            registrationService.start()

            val exception = assertThrows<InvalidMembershipRegistrationException> {
                registrationService.register(registrationId, eric, mockContext)
            }

            assertThat(exception)
                .hasMessage("Registration failed. Reason: Static member list inside the group policy file cannot be empty.")
            registrationService.stop()
        }

        @Test
        fun `registration fails when coordinator is not running`() {
            setUpPublisher()

            val exception = assertThrows<MembershipRegistrationException> {
                registrationService.register(registrationId, alice, mockContext)
            }

            assertThat(exception).hasMessage(
                "Registration failed. Reason: StaticMemberRegistrationService is not running/down."
            )
        }

        @Test
        fun `registration fails when registering member is not in the static member list`() {
            setUpPublisher()
            registrationService.start()

            val exception = assertThrows<InvalidMembershipRegistrationException> {
                registrationService.register(registrationId, daisy, mockContext)
            }

            assertThat(exception)
                .hasMessage(
                    "Registration failed. Reason: Our membership O=Daisy, L=London, C=GB is either not " +
                            "listed in the static member list or there is another member with the same name."
                )
            registrationService.stop()
        }

        @Test
        fun `registration fails when key scheme is not provided in context`() {
            setUpPublisher()
            registrationService.start()

            val exception = assertThrows<InvalidMembershipRegistrationException> {
                registrationService.register(registrationId, alice, mock())
            }

            assertThat(exception).hasMessage(
                "Registration failed. Reason: Key scheme must be specified."
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

            assertThrows<InvalidMembershipRegistrationException> {
                registrationService.register(registrationId, alice, context)
            }
        }

        @Test
        fun `registration fails when role is set to notary and notary service name already exists`() {
            setUpPublisher()
            registrationService.start()
            val context = mapOf(
                KEY_SCHEME to ECDSA_SECP256R1_CODE_NAME,
                "${ROLES_PREFIX}.0" to "notary",
                NOTARY_SERVICE_NAME to notary.toString(),
                NOTARY_SERVICE_PROTOCOL to "net.corda.notary.MyNotaryService",
            )
            val mockNotaryDetails = MemberNotaryDetails(
                notary,
                null,
                emptyList(),
                emptyList(),
                true
            )
            val mockMemberContext: MemberContext = mock {
                on { entries } doReturn mapOf(
                    String.format(ROLES_PREFIX, 0) to MemberInfoExtension.NOTARY_ROLE
                ).entries
                on { parse(eq("corda.notary"), eq(MemberNotaryDetails::class.java)) } doReturn mockNotaryDetails
            }
            val mockNotaryMember: MemberInfo = mock {
                on { memberProvidedContext } doReturn mockMemberContext
            }
            whenever(groupReader.lookup()).thenReturn(listOf(mockNotaryMember))

            assertThrows<InvalidMembershipRegistrationException> {
                registrationService.register(registrationId, alice, context)
            }
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

            val exception = assertThrows<InvalidMembershipRegistrationException> {
                registrationService.register(registrationId, alice, mockContext)
            }

            assertThat(exception)
                .hasMessageContaining(err)
                .hasMessageContaining(errReason)
            registrationService.stop()
        }

        @Test
        fun `registration fails when virtual node info is unavailable`() {
            setUpPublisher()
            registrationService.start()
            whenever(virtualNodeInfoReadService.get((alice))).thenReturn(null)

            val exception = assertThrows<NotReadyMembershipRegistrationException> {
                registrationService.register(registrationId, alice, mockContext)
            }

            assertThat(exception).hasMessageContaining("Could not find virtual node")
        }

        @Test
        fun `registration fails when context has invalid entries`() {
            val longString = StringBuilder().apply { for(i in 0..256){ this.append("a") } }.toString()
            setUpPublisher()
            registrationService.start()
            val invalidContext = mockContext + mapOf(
                "$CUSTOM_KEY_PREFIX.$longString" to "value",
            )

            val exception = assertThrows<InvalidMembershipRegistrationException> {
                registrationService.register(registrationId, alice, invalidContext)
            }

            assertThat(exception).hasMessageContaining("Failed to validate the registration context")
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
                "${ROLES_PREFIX}.0" to "notary",
                NOTARY_SERVICE_NAME to notary.toString(),
                NOTARY_SERVICE_PROTOCOL to "net.corda.notary.MyNotaryService",
                String.format(NOTARY_SERVICE_PROTOCOL_VERSIONS, 0) to "1"
            )

            assertDoesNotThrow {
                registrationService.register(registrationId, alice, context)
            }
        }

        @Test
        fun `registration not submitted when context has un known role`() {
            setUpPublisher()
            registrationService.start()
            val context = mapOf(
                KEY_SCHEME to ECDSA_SECP256R1_CODE_NAME,
                "${ROLES_PREFIX}.0" to "nop",
            )

            assertThrows<InvalidMembershipRegistrationException> {
                registrationService.register(registrationId, alice, context)
            }
        }

        @Test
        fun `registration adds notary info to member info`() {
            val capturedMemberInfos = argumentCaptor<MemberInfo>()
            val capturedPublishedList = argumentCaptor<List<Record<String, Any>>>()
            whenever(mockPublisher.publish(capturedPublishedList.capture())).doReturn(emptyList())
            setUpPublisher()
            registrationService.start()
            val context = mapOf(
                KEY_SCHEME to ECDSA_SECP256R1_CODE_NAME,
                "${ROLES_PREFIX}.0" to "notary",
                NOTARY_SERVICE_NAME to notary.toString(),
                NOTARY_SERVICE_PROTOCOL to "net.corda.notary.MyNotaryService",
                String.format(NOTARY_SERVICE_PROTOCOL_VERSIONS, 0) to "1"
            )

            registrationService.register(registrationId, alice, context)

            verify(memberInfoFactory, times(3))
                .createMgmOrStaticPersistentMemberInfo(any(), capturedMemberInfos.capture(), any(), any())
            val notaryMemberInfo = capturedMemberInfos.allValues.first {
                it.notaryDetails != null
            }
            val notaryDetails = notaryMemberInfo.notaryDetails!!
            assertSoftly {
                assertThat(capturedPublishedList.firstValue.firstOrNull()?.value)
                    .isInstanceOf(PersistentMemberInfo::class.java)
                assertThat(notaryDetails.serviceName)
                    .isEqualTo(MemberX500Name.parse(notary.toString()))
                assertThat(notaryDetails.serviceProtocol).isEqualTo("net.corda.notary.MyNotaryService")
                assertThat(notaryDetails.serviceProtocolVersions).containsExactlyInAnyOrder(1)

                assertThat(notaryDetails.keys.toList())
                    .hasSize(1)
                    .allMatch {
                        it.publicKey == defaultKey
                    }
                    .allMatch {
                        it.publicKeyHash == defaultKey.fullIdHash()
                    }
                    .allMatch {
                        it.spec.signatureName == SignatureSpecs.RSA_SHA512.signatureName
                    }

                assertThat(notaryMemberInfo.ledgerKeys).isEmpty()
            }
        }

        @Test
        fun `registration without notary will not add notary to member info`() {
            val capturedMemberInfos = argumentCaptor<MemberInfo>()
            val capturedPublishedList = argumentCaptor<List<Record<String, Any>>>()
            whenever(mockPublisher.publish(capturedPublishedList.capture())).doReturn(emptyList())
            setUpPublisher()
            registrationService.start()
            val context = mapOf(
                KEY_SCHEME to ECDSA_SECP256R1_CODE_NAME,
            )

            registrationService.register(registrationId, alice, context)

            verify(memberInfoFactory, times(3))
                .createMgmOrStaticPersistentMemberInfo(any(), capturedMemberInfos.capture(), any(), any())

            assertThat(capturedPublishedList.firstValue.firstOrNull()?.value)
                .isInstanceOf(PersistentMemberInfo::class.java)
            val notary = capturedMemberInfos.allValues.firstOrNull {
                it.notaryDetails != null
            }
            assertThat(notary).isNull()
        }

        @Test
        fun `registration with notary role persists group parameters for all members who have vnodes set up`() {
            val context = mapOf(
                KEY_SCHEME to ECDSA_SECP256R1_CODE_NAME,
                "${ROLES_PREFIX}.0" to "notary",
                NOTARY_SERVICE_NAME to notary.toString(),
                NOTARY_SERVICE_PROTOCOL to "net.corda.notary.MyNotaryService",
                String.format(NOTARY_SERVICE_PROTOCOL_VERSIONS, 0) to "1"
            )
            whenever(
                persistenceClient.persistGroupParameters(
                    any(),
                    any()
                )
            ).doReturn(SuccessOperation(mockSignedGroupParameters))
            whenever(groupPolicyProvider.getGroupPolicy(bob)).thenReturn(groupPolicyWithStaticNetwork)
            whenever(virtualNodeInfoReadService.get(bob)).thenReturn(buildTestVirtualNodeInfo(bob))
            setUpPublisher()
            registrationService.start()

            registrationService.register(registrationId, bob, context)

            verify(persistenceClient, times(1)).persistGroupParameters(eq(bob), eq(mockSignedGroupParameters))
            verify(persistenceClient, times(1)).persistGroupParameters(eq(alice), eq(mockSignedGroupParameters))
            verify(persistenceClient, never()).persistGroupParameters(eq(charlie), eq(mockSignedGroupParameters))
        }

        @Test
        fun `registration fails when there is a virtual node having the same name as the notary service`() {
            val context = mapOf(
                KEY_SCHEME to ECDSA_SECP256R1_CODE_NAME,
                "${ROLES_PREFIX}.0" to "notary",
                NOTARY_SERVICE_NAME to aliceName.toString(),
                NOTARY_SERVICE_PROTOCOL to "net.corda.notary.MyNotaryService",
                String.format(NOTARY_SERVICE_PROTOCOL_VERSIONS, 0) to "1"
            )

            whenever(groupPolicyProvider.getGroupPolicy(bob)).thenReturn(groupPolicyWithStaticNetwork)
            whenever(virtualNodeInfoReadService.get(bob)).thenReturn(buildTestVirtualNodeInfo(bob))
            setUpPublisher()
            registrationService.start()

            val message = assertFailsWith<InvalidMembershipRegistrationException> {
                registrationService.register(registrationId, bob, context)
            }
            assertThat(message.message).contains("There is a virtual node having the same name")
        }

        @Test
        fun `registration fails when notary service name is blank`() {
            val context = mapOf(
                KEY_SCHEME to ECDSA_SECP256R1_CODE_NAME,
                "${ROLES_PREFIX}.0" to "notary",
                NOTARY_SERVICE_NAME to "",
                NOTARY_SERVICE_PROTOCOL to "net.corda.notary.MyNotaryService",
                String.format(NOTARY_SERVICE_PROTOCOL_VERSIONS, 0) to "1"
            )

            whenever(groupPolicyProvider.getGroupPolicy(bob)).thenReturn(groupPolicyWithStaticNetwork)
            whenever(virtualNodeInfoReadService.get(bob)).thenReturn(buildTestVirtualNodeInfo(bob))
            setUpPublisher()
            registrationService.start()

            val message = assertFailsWith<InvalidMembershipRegistrationException> {
                registrationService.register(registrationId, bob, context)
            }
            assertThat(message.message).contains("Notary must have a non-empty service name.")
        }

        @Test
        fun `registration fails when the virtual node and notary service name is the same`() {
            val context = mapOf(
                KEY_SCHEME to ECDSA_SECP256R1_CODE_NAME,
                "${ROLES_PREFIX}.0" to "notary",
                NOTARY_SERVICE_NAME to bobName.toString(),
                NOTARY_SERVICE_PROTOCOL to "net.corda.notary.MyNotaryService",
                String.format(NOTARY_SERVICE_PROTOCOL_VERSIONS, 0) to "1"
            )

            whenever(groupPolicyProvider.getGroupPolicy(bob)).thenReturn(groupPolicyWithStaticNetwork)
            whenever(virtualNodeInfoReadService.get(bob)).thenReturn(buildTestVirtualNodeInfo(bob))
            setUpPublisher()
            registrationService.start()

            val message = assertFailsWith<InvalidMembershipRegistrationException> {
                registrationService.register(registrationId, bob, context)
            }
            assertThat(message.message).contains("and virtual node name cannot be the same")
        }

        @Test
        fun `registration fails when there are two virtual nodes in the static list having the same name`() {
            val context = mapOf(
                KEY_SCHEME to ECDSA_SECP256R1_CODE_NAME,
                "${ROLES_PREFIX}.0" to "notary",
                NOTARY_SERVICE_NAME to "O=MyNotaryService, L=London, C=GB",
                NOTARY_SERVICE_PROTOCOL to "net.corda.notary.MyNotaryService",
                String.format(NOTARY_SERVICE_PROTOCOL_VERSIONS, 0) to "1"
            )

            whenever(groupPolicyProvider.getGroupPolicy(alice))
                .thenReturn(groupPolicyWithStaticNetworkAndDuplicatedVNodeName)
            whenever(virtualNodeInfoReadService.get(alice)).thenReturn(buildTestVirtualNodeInfo(alice))
            setUpPublisher()
            registrationService.start()

            val message = assertFailsWith<InvalidMembershipRegistrationException> {
                registrationService.register(registrationId, alice, context)
            }
            assertThat(message.message).contains("or there is another member with the same name.")
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

    @Nested
    inner class StaticNetworkInfoTests {

        @Test
        fun `Failure to update static network info will be retried`() {
            whenever(persistenceClient.updateStaticNetworkInfo(any()))
                .doAnswer {
                    // Overwrite the behaviour for next call so it succeeds
                    whenever(persistenceClient.updateStaticNetworkInfo(any())).doAnswer {
                        SuccessOperation(it.getArgument(0))
                    }
                    FailedOperation()
                }

            setUpPublisher()
            registrationService.start()
            val context = mapOf(
                KEY_SCHEME to ECDSA_SECP256R1_CODE_NAME,
                "${ROLES_PREFIX}.0" to "notary",
                NOTARY_SERVICE_NAME to notary.toString(),
                NOTARY_SERVICE_PROTOCOL to "net.corda.notary.MyNotaryService",
                String.format(NOTARY_SERVICE_PROTOCOL_VERSIONS, 0) to "1"
            )

            assertDoesNotThrow {
                registrationService.register(registrationId, alice, context)
            }

            verify(membershipQueryClient, times(2)).queryStaticNetworkInfo(any())
            verify(persistenceClient, times(2)).updateStaticNetworkInfo(any())
        }

        @Test
        fun `Failure to update static network info will be retried up to the configured max times and can succeed on a later retry`() {
            val maxRetries = 10
            var attempts = 0
            whenever(persistenceClient.updateStaticNetworkInfo(any()))
                .doAnswer {
                    if(++attempts == maxRetries-1 ) {
                        // Overwrite the behaviour for next call so it succeeds
                        whenever(persistenceClient.updateStaticNetworkInfo(any())).doAnswer {
                            SuccessOperation(it.getArgument(0))
                        }
                    }
                    FailedOperation()
                }

            setUpPublisher()
            registrationService.start()
            val context = mapOf(
                KEY_SCHEME to ECDSA_SECP256R1_CODE_NAME,
                "${ROLES_PREFIX}.0" to "notary",
                NOTARY_SERVICE_NAME to notary.toString(),
                NOTARY_SERVICE_PROTOCOL to "net.corda.notary.MyNotaryService",
                String.format(NOTARY_SERVICE_PROTOCOL_VERSIONS, 0) to "1"
            )

            assertDoesNotThrow {
                registrationService.register(registrationId, alice, context)
            }

            verify(membershipQueryClient, times(maxRetries)).queryStaticNetworkInfo(any())
            verify(persistenceClient, times(maxRetries)).updateStaticNetworkInfo(any())
        }

        @Test
        fun `Failure to update static network info will be retried up to the configured max times and then fail`() {
            val maxRetries = 10
            whenever(persistenceClient.updateStaticNetworkInfo(any()))
                .doAnswer {
                    FailedOperation()
                }

            setUpPublisher()
            registrationService.start()
            val context = mapOf(
                KEY_SCHEME to ECDSA_SECP256R1_CODE_NAME,
                "${ROLES_PREFIX}.0" to "notary",
                NOTARY_SERVICE_NAME to notary.toString(),
                NOTARY_SERVICE_PROTOCOL to "net.corda.notary.MyNotaryService",
                String.format(NOTARY_SERVICE_PROTOCOL_VERSIONS, 0) to "1"
            )

            assertThrows<NotReadyMembershipRegistrationException> {
                registrationService.register(registrationId, alice, context)
            }.also {
                assertThat(it.message).contains("Failed")
            }

            verify(membershipQueryClient, times(maxRetries)).queryStaticNetworkInfo(any())
            verify(persistenceClient, times(maxRetries)).updateStaticNetworkInfo(any())
        }
    }
}