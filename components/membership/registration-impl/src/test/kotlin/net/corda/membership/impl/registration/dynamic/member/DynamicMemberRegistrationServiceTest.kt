package net.corda.membership.impl.registration.dynamic.member

import com.typesafe.config.ConfigFactory
import net.corda.avro.serialization.CordaAvroSerializationFactory
import net.corda.avro.serialization.CordaAvroSerializer
import net.corda.configuration.read.ConfigChangedEvent
import net.corda.configuration.read.ConfigurationGetService
import net.corda.configuration.read.ConfigurationReadService
import net.corda.crypto.cipher.suite.KeyEncodingService
import net.corda.crypto.cipher.suite.SignatureSpecs
import net.corda.crypto.client.CryptoOpsClient
import net.corda.crypto.core.CryptoConsts.Categories.LEDGER
import net.corda.crypto.core.CryptoConsts.Categories.NOTARY
import net.corda.crypto.core.CryptoConsts.Categories.PRE_AUTH
import net.corda.crypto.core.CryptoConsts.Categories.SESSION_INIT
import net.corda.crypto.core.DigitalSignatureWithKey
import net.corda.crypto.core.ShortHash
import net.corda.crypto.core.fullIdHash
import net.corda.crypto.hes.EncryptedDataWithKey
import net.corda.crypto.hes.EphemeralKeyPairEncryptor
import net.corda.data.KeyValuePair
import net.corda.data.KeyValuePairList
import net.corda.data.crypto.wire.CryptoSignatureWithKey
import net.corda.data.crypto.wire.CryptoSigningKey
import net.corda.data.membership.common.v2.RegistrationStatus
import net.corda.data.membership.p2p.MembershipRegistrationRequest
import net.corda.data.p2p.app.AppMessage
import net.corda.data.p2p.app.OutboundUnauthenticatedMessage
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.SmartConfigFactory
import net.corda.libs.platform.PlatformInfoProvider
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
import net.corda.membership.impl.registration.TEST_CPI_NAME
import net.corda.membership.impl.registration.TEST_CPI_VERSION
import net.corda.membership.impl.registration.TEST_PLATFORM_VERSION
import net.corda.membership.impl.registration.TEST_SOFTWARE_VERSION
import net.corda.membership.impl.registration.buildTestVirtualNodeInfo
import net.corda.membership.impl.registration.testCpiSignerSummaryHash
import net.corda.membership.impl.registration.verifiers.RegistrationContextCustomFieldsVerifier
import net.corda.membership.lib.MemberInfoExtension.Companion.CUSTOM_KEY_PREFIX
import net.corda.membership.lib.MemberInfoExtension.Companion.ECDH_KEY
import net.corda.membership.lib.MemberInfoExtension.Companion.GROUP_ID
import net.corda.membership.lib.MemberInfoExtension.Companion.LEDGER_KEYS
import net.corda.membership.lib.MemberInfoExtension.Companion.LEDGER_KEYS_ID
import net.corda.membership.lib.MemberInfoExtension.Companion.LEDGER_KEYS_KEY
import net.corda.membership.lib.MemberInfoExtension.Companion.LEDGER_KEY_HASHES_KEY
import net.corda.membership.lib.MemberInfoExtension.Companion.LEDGER_KEY_SIGNATURE_SPEC
import net.corda.membership.lib.MemberInfoExtension.Companion.MEMBER_CPI_NAME
import net.corda.membership.lib.MemberInfoExtension.Companion.MEMBER_CPI_SIGNER_HASH
import net.corda.membership.lib.MemberInfoExtension.Companion.MEMBER_CPI_VERSION
import net.corda.membership.lib.MemberInfoExtension.Companion.NOTARY_KEY_SPEC
import net.corda.membership.lib.MemberInfoExtension.Companion.NOTARY_SERVICE_BACKCHAIN_REQUIRED
import net.corda.membership.lib.MemberInfoExtension.Companion.NOTARY_SERVICE_NAME
import net.corda.membership.lib.MemberInfoExtension.Companion.NOTARY_SERVICE_PROTOCOL
import net.corda.membership.lib.MemberInfoExtension.Companion.NOTARY_SERVICE_PROTOCOL_VERSIONS
import net.corda.membership.lib.MemberInfoExtension.Companion.PARTY_NAME
import net.corda.membership.lib.MemberInfoExtension.Companion.PARTY_SESSION_KEYS_ID
import net.corda.membership.lib.MemberInfoExtension.Companion.PARTY_SESSION_KEYS_PEM
import net.corda.membership.lib.MemberInfoExtension.Companion.PLATFORM_VERSION
import net.corda.membership.lib.MemberInfoExtension.Companion.PROTOCOL_VERSION
import net.corda.membership.lib.MemberInfoExtension.Companion.REGISTRATION_ID
import net.corda.membership.lib.MemberInfoExtension.Companion.ROLES_PREFIX
import net.corda.membership.lib.MemberInfoExtension.Companion.SERIAL
import net.corda.membership.lib.MemberInfoExtension.Companion.SESSION_KEYS_HASH
import net.corda.membership.lib.MemberInfoExtension.Companion.SESSION_KEYS_SIGNATURE_SPEC
import net.corda.membership.lib.MemberInfoExtension.Companion.SOFTWARE_VERSION
import net.corda.membership.lib.MemberInfoExtension.Companion.TLS_CERTIFICATE_SUBJECT
import net.corda.membership.lib.MemberInfoExtension.Companion.URL_KEY
import net.corda.membership.lib.MemberInfoExtension.Companion.ecdhKey
import net.corda.membership.lib.MemberInfoExtension.Companion.groupId
import net.corda.membership.lib.MemberInfoExtension.Companion.isMgm
import net.corda.membership.lib.registration.PRE_AUTH_TOKEN
import net.corda.membership.lib.registration.RegistrationRequest
import net.corda.membership.lib.schema.validation.MembershipSchemaValidationException
import net.corda.membership.lib.schema.validation.MembershipSchemaValidator
import net.corda.membership.lib.schema.validation.MembershipSchemaValidatorFactory
import net.corda.membership.lib.toMap
import net.corda.membership.lib.toWire
import net.corda.membership.locally.hosted.identities.IdentityInfo
import net.corda.membership.locally.hosted.identities.LocallyHostedIdentitiesService
import net.corda.membership.persistence.client.MembershipPersistenceClient
import net.corda.membership.persistence.client.MembershipPersistenceOperation
import net.corda.membership.read.MembershipGroupReader
import net.corda.membership.read.MembershipGroupReaderProvider
import net.corda.membership.registration.InvalidMembershipRegistrationException
import net.corda.membership.registration.NotReadyMembershipRegistrationException
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.records.Record
import net.corda.schema.Schemas
import net.corda.schema.configuration.ConfigKeys
import net.corda.schema.membership.MembershipSchema
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.crypto.KeySchemeCodes.ECDSA_SECP256R1_CODE_NAME
import net.corda.v5.crypto.KeySchemeCodes.EDDSA_ED25519_CODE_NAME
import net.corda.v5.crypto.SignatureSpec
import net.corda.v5.membership.MGMContext
import net.corda.v5.membership.MemberContext
import net.corda.v5.membership.MemberInfo
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import net.corda.virtualnode.toAvro
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.SoftAssertions
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.Mockito
import org.mockito.kotlin.KArgumentCaptor
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doNothing
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.same
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.nio.ByteBuffer
import java.security.PublicKey
import java.security.cert.X509Certificate
import java.util.UUID
import java.util.concurrent.CompletableFuture
import javax.security.auth.x500.X500Principal

class DynamicMemberRegistrationServiceTest {
    private companion object {
        const val SESSION_KEY = "1234"
        const val SESSION_KEY_ID = "ABC123456789"
        const val LEDGER_KEY = "5678"
        const val LEDGER_KEY_ID = "BBC123456789"
        const val NOTARY_KEY = "2020"
        const val NOTARY_KEY_ID = "CBC123456789"
        const val TEST_KEY = "9999"
        const val TEST_KEY_ID = "DDD123456789"
        const val PUBLISHER_CLIENT_ID = "dynamic-member-registration-service"
        const val GROUP_NAME = "dummy_group"
        const val NOTARY_KEY_PEM = "1234"
        const val NOTARY_KEY_HASH = "SHA-256:73A2AF8864FC500FA49048BF3003776C19938F360E56BD03663866FB3087884A"
        const val NOTARY_KEY_SIG_SPEC = "SHA256withECDSA"

        val MEMBER_CONTEXT_BYTES = "2222".toByteArray()
        val REQUEST_BYTES = "3333".toByteArray()
        val UNAUTH_REQUEST_BYTES = "4444".toByteArray()
        const val SESSION_KEY_ID_KEY = "corda.session.keys.0.id"
        const val LEDGER_KEY_ID_KEY = "corda.ledger.keys.0.id"

        const val NOTARY_KEY_ID_KEY = "corda.notary.keys.0.id"
        const val NOTARY_KEY_PEM_KEY = "corda.notary.keys.0.pem"
        const val NOTARY_KEY_HASH_KEY = "corda.notary.keys.0.hash"
        const val NOTARY_KEY_SIG_SPEC_KEY = "corda.notary.keys.0.signature.spec"
    }

    private val ecdhKey: PublicKey = mock()
    private val memberProvidedContext: MemberContext = mock {
        on { parseOrNull(ECDH_KEY, PublicKey::class.java) } doReturn ecdhKey
    }
    private val registrationResultId = UUID(3, 4)
    private val mgmProvidedContext: MGMContext = mock()
    private val mgmName = MemberX500Name("Corda MGM", "London", "GB")
    private val mgm = HoldingIdentity(mgmName, GROUP_NAME)
    private val mgmInfo: MemberInfo = mock {
        on { memberProvidedContext } doReturn memberProvidedContext
        on { mgmProvidedContext } doReturn mgmProvidedContext
        on { name } doReturn mgmName
        on { groupId } doReturn GROUP_NAME
        on { isMgm } doReturn true
        on { platformVersion } doReturn 50100
    }
    private val memberName = MemberX500Name("Alice", "London", "GB")
    private val member = HoldingIdentity(memberName, GROUP_NAME)
    private val memberInfo = mock<MemberInfo> {
        on { memberProvidedContext } doReturn mock()
        on { mgmProvidedContext } doReturn mock()
        on { name } doReturn memberName
        on { groupId } doReturn GROUP_NAME
    }
    private val memberId = member.shortHash
    private val sessionKey: PublicKey = mock {
        on { encoded } doReturn SESSION_KEY.toByteArray()
        on { algorithm } doReturn "EC"
    }
    private val sessionCryptoSigningKey: CryptoSigningKey = mock {
        on { publicKey } doReturn ByteBuffer.wrap(SESSION_KEY.toByteArray())
        on { id } doReturn SESSION_KEY_ID
        on { schemeCodeName } doReturn ECDSA_SECP256R1_CODE_NAME
        on { category } doReturn SESSION_INIT
    }
    private val ledgerKey: PublicKey = mock {
        on { encoded } doReturn LEDGER_KEY.toByteArray()
    }
    private val ledgerCryptoSigningKey: CryptoSigningKey = mock {
        on { publicKey } doReturn ByteBuffer.wrap(LEDGER_KEY.toByteArray())
        on { id } doReturn LEDGER_KEY_ID
        on { schemeCodeName } doReturn ECDSA_SECP256R1_CODE_NAME
        on { category } doReturn LEDGER
    }
    private val notaryKey: PublicKey = mock {
        on { encoded } doReturn NOTARY_KEY.toByteArray()
    }
    private val notaryCryptoSigningKey: CryptoSigningKey = mock {
        on { publicKey } doReturn ByteBuffer.wrap(NOTARY_KEY.toByteArray())
        on { id } doReturn NOTARY_KEY_ID
        on { schemeCodeName } doReturn ECDSA_SECP256R1_CODE_NAME
        on { category } doReturn NOTARY
    }
    private val mockPublisher = mock<Publisher>().apply {
        whenever(publish(any())).thenReturn(listOf(CompletableFuture.completedFuture(Unit)))
    }

    private val publisherFactory: PublisherFactory = mock {
        on { createPublisher(any(), any()) } doReturn mockPublisher
    }
    private val testKey: PublicKey = mock {
        on { encoded } doReturn TEST_KEY.toByteArray()
    }
    private val keyEncodingService: KeyEncodingService = mock {
        on { decodePublicKey(SESSION_KEY.toByteArray()) } doReturn sessionKey
        on { decodePublicKey(SESSION_KEY) } doReturn sessionKey
        on { decodePublicKey(LEDGER_KEY.toByteArray()) } doReturn ledgerKey
        on { decodePublicKey(NOTARY_KEY.toByteArray()) } doReturn notaryKey
        on { decodePublicKey(TEST_KEY.toByteArray()) } doReturn testKey

        on { encodeAsString(any()) } doReturn SESSION_KEY
        on { encodeAsString(ledgerKey) } doReturn LEDGER_KEY
        on { encodeAsByteArray(sessionKey) } doReturn SESSION_KEY.toByteArray()
        on { encodeAsByteArray(testKey) } doReturn TEST_KEY.toByteArray()
    }
    private val mockSignature: DigitalSignatureWithKey =
        DigitalSignatureWithKey(
            sessionKey,
            byteArrayOf(1)
        )
    private val cryptoOpsClient: CryptoOpsClient = mock {
        on { lookupKeysByIds(memberId.value, listOf(ShortHash.of(SESSION_KEY_ID))) } doReturn listOf(sessionCryptoSigningKey)
        on { lookupKeysByIds(memberId.value, listOf(ShortHash.of(LEDGER_KEY_ID))) } doReturn listOf(ledgerCryptoSigningKey)
        on { lookupKeysByIds(memberId.value, listOf(ShortHash.of(NOTARY_KEY_ID))) } doReturn listOf(notaryCryptoSigningKey)
        on {
            sign(
                any(),
                any(),
                any<SignatureSpec>(),
                any(),
                eq(emptyMap())
            )
        }.doReturn(mockSignature)
    }
    private fun createTestCryptoSigningKey(keyCategory: String): CryptoSigningKey = mock {
        on { publicKey } doReturn ByteBuffer.wrap(TEST_KEY.toByteArray())
        on { id } doReturn TEST_KEY_ID
        on { schemeCodeName } doReturn ECDSA_SECP256R1_CODE_NAME
        on { category } doReturn keyCategory
    }

    private val componentHandle: RegistrationHandle = mock()
    private val configHandle: Resource = mock()
    private val testConfig =
        SmartConfigFactory.createWithoutSecurityServices().create(ConfigFactory.parseString("instanceId=1"))
    private val dependentComponents = setOf(
        LifecycleCoordinatorName.forComponent<ConfigurationReadService>(),
        LifecycleCoordinatorName.forComponent<CryptoOpsClient>(),
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
    private val lifecycleCoordinatorFactory: LifecycleCoordinatorFactory = mock {
        on { createCoordinator(any(), lifecycleHandlerCaptor.capture()) } doReturn coordinator
    }
    private val configurationReadService: ConfigurationReadService = mock {
        on { registerComponentForUpdates(eq(coordinator), any()) } doReturn configHandle
    }
    private var contextCaptor: KArgumentCaptor<KeyValuePairList> = argumentCaptor()
    private val keyValuePairListSerializer: CordaAvroSerializer<Any> = mock {
        on { serialize(contextCaptor.capture()) } doReturn MEMBER_CONTEXT_BYTES
    }
    private val registrationRequestSerializer: CordaAvroSerializer<Any> = mock {
        on { serialize(any()) } doReturn REQUEST_BYTES
    }
    private val unauthenticatedRegistrationRequestSerializer: CordaAvroSerializer<Any> = mock {
        on { serialize(any()) } doReturn UNAUTH_REQUEST_BYTES
    }
    private val serializationFactory: CordaAvroSerializationFactory = mock {
        on { createAvroSerializer<Any>(any()) }.thenReturn(
            registrationRequestSerializer,
            keyValuePairListSerializer,
            unauthenticatedRegistrationRequestSerializer
        )
    }
    private val groupReader: MembershipGroupReader = mock {
        on { lookup() } doReturn listOf(mgmInfo)
    }
    private val membershipGroupReaderProvider: MembershipGroupReaderProvider = mock {
        on { getGroupReader(any()) } doReturn groupReader
    }
    private val command = Record(
        "topic",
        "key",
        "value"
    )
    private val persistenceOperation = mock<MembershipPersistenceOperation<Unit>> {
        on { createAsyncCommands() } doReturn listOf(command)
    }
    private val membershipPersistenceClient = mock<MembershipPersistenceClient> {
        on {
            persistRegistrationRequest(
                any(),
                any(),
                any(),
            )
        } doReturn persistenceOperation
    }
    private val membershipSchemaValidator: MembershipSchemaValidator = mock()
    private val membershipSchemaValidatorFactory: MembershipSchemaValidatorFactory = mock {
        on { createValidator() } doReturn membershipSchemaValidator
    }
    private val platformInfoProvider: PlatformInfoProvider = mock {
        on { activePlatformVersion } doReturn TEST_PLATFORM_VERSION
        on { localWorkerSoftwareVersion } doReturn TEST_SOFTWARE_VERSION
    }
    private val datawithKey: EncryptedDataWithKey = mock {
        on { cipherText } doReturn "1234".toByteArray()
    }
    private val ephemeralKeyPairEncryptor: EphemeralKeyPairEncryptor = mock {
        on { encrypt(eq(ecdhKey), any(), any()) } doReturn datawithKey
    }
    private val virtualNodeInfo = buildTestVirtualNodeInfo(member)
    private val virtualNodeInfoReadService: VirtualNodeInfoReadService = mock {
        on { get(eq(member)) } doReturn virtualNodeInfo
    }
    private val gatewayConfiguration = mock<SmartConfig> {
        on { getConfig("sslConfig") } doReturn mock
        on { getString("tlsType") } doReturn "ONE_WAY"
    }
    private val configurationGetService = mock<ConfigurationGetService> {
        on { getSmartConfig(ConfigKeys.P2P_GATEWAY_CONFIG) } doReturn gatewayConfiguration
    }
    private val locallyHostedIdentitiesService = mock<LocallyHostedIdentitiesService>()
    private val registrationContextCustomFieldsVerifier = Mockito.mockConstruction(
        RegistrationContextCustomFieldsVerifier::class.java
    ) {
            mock, _ ->
        whenever(mock.verify(context)).doReturn(RegistrationContextCustomFieldsVerifier.Result.Success)
    }
    private val registrationService = DynamicMemberRegistrationService(
        publisherFactory,
        configurationReadService,
        lifecycleCoordinatorFactory,
        cryptoOpsClient,
        keyEncodingService,
        serializationFactory,
        membershipGroupReaderProvider,
        membershipPersistenceClient,
        membershipSchemaValidatorFactory,
        platformInfoProvider,
        ephemeralKeyPairEncryptor,
        virtualNodeInfoReadService,
        locallyHostedIdentitiesService,
        configurationGetService
    )

    private val context = mapOf(
        SESSION_KEY_ID_KEY to SESSION_KEY_ID,
        SESSION_KEYS_SIGNATURE_SPEC.format(0) to SignatureSpecs.ECDSA_SHA512.signatureName,
        URL_KEY.format(0) to "https://localhost:1080",
        PROTOCOL_VERSION.format(0) to "1",
        LEDGER_KEY_ID_KEY to LEDGER_KEY_ID,
        LEDGER_KEY_SIGNATURE_SPEC.format(0) to SignatureSpecs.ECDSA_SHA512.signatureName,
        PRE_AUTH_TOKEN to UUID(0, 1).toString(),
        "$CUSTOM_KEY_PREFIX.0" to "test",
        "corda.test" to "dummy"
    )
    private val contextWithoutLedgerKey = context.filter { !it.key.contains(LEDGER_KEYS) }

    private val previousRegistrationContext = mapOf(
        "$CUSTOM_KEY_PREFIX.0" to "test",
        "corda.test" to "dummy",
        URL_KEY.format(0) to "https://localhost:1080",
        PROTOCOL_VERSION.format(0) to "1",
        PARTY_SESSION_KEYS_PEM.format(0) to SESSION_KEY,
        SESSION_KEYS_HASH.format(0) to sessionKey.fullIdHash().toString(),
        SESSION_KEYS_SIGNATURE_SPEC.format(0) to SignatureSpecs.ECDSA_SHA512.signatureName,
        LEDGER_KEYS_KEY.format(0) to LEDGER_KEY,
        LEDGER_KEY_HASHES_KEY.format(0) to ledgerKey.fullIdHash().toString(),
        LEDGER_KEY_SIGNATURE_SPEC.format(0) to SignatureSpecs.ECDSA_SHA512.signatureName,
        REGISTRATION_ID to registrationResultId.toString(),
        PARTY_NAME to memberName.toString(),
        GROUP_ID to GROUP_NAME,
        PLATFORM_VERSION to TEST_PLATFORM_VERSION.toString(),
        SOFTWARE_VERSION to TEST_SOFTWARE_VERSION,
        MEMBER_CPI_NAME to TEST_CPI_NAME,
        MEMBER_CPI_VERSION to TEST_CPI_VERSION,
        MEMBER_CPI_SIGNER_HASH to testCpiSignerSummaryHash.toString(),
    )

    private val previousNotaryRegistrationContext = previousRegistrationContext.filter {
        !it.key.contains(LEDGER_KEYS)
    } + mapOf(
        "$ROLES_PREFIX.0" to "notary",
        NOTARY_SERVICE_PROTOCOL to "net.corda.notary.MyNotaryService",
        NOTARY_SERVICE_NAME to "O=MyNotaryService, L=London, C=GB",
        NOTARY_KEY_PEM_KEY to NOTARY_KEY_PEM,
        NOTARY_KEY_HASH_KEY to NOTARY_KEY_HASH,
        NOTARY_KEY_SIG_SPEC_KEY to NOTARY_KEY_SIG_SPEC,
    )

    @AfterEach
    fun cleanUp() {
        registrationContextCustomFieldsVerifier.close()
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
                setOf(ConfigKeys.BOOT_CONFIG, ConfigKeys.MESSAGING_CONFIG),
                mapOf(
                    ConfigKeys.BOOT_CONFIG to testConfig,
                    ConfigKeys.MESSAGING_CONFIG to testConfig
                )
            ),
            coordinator
        )
    }

    private fun ByteBuffer.toBytes(): ByteArray {
        val bytes = ByteArray(this.remaining())
        this.get(bytes)
        return bytes
    }

    @Nested
    inner class SuccessfulRegistrationTests {
        @BeforeEach
        fun setup() {
            postConfigChangedEvent()
            registrationService.start()
        }

        @AfterEach
        fun cleanup() {
            registrationService.stop()
        }

        @Test
        fun `registration successfully builds unauthenticated message and publishes it`() {
            val publishedMessageList = registrationService.register(registrationResultId, member, context)

            SoftAssertions.assertSoftly {
                it.assertThat(publishedMessageList)
                    .contains(command)
                    .hasSize(2)
                val publishedMessage = publishedMessageList.first()
                it.assertThat(publishedMessage.topic).isEqualTo(Schemas.P2P.P2P_OUT_TOPIC)
                it.assertThat(publishedMessage.key).isEqualTo(memberId.value)
                val unauthenticatedMessagePublished =
                    (publishedMessage.value as AppMessage).message as OutboundUnauthenticatedMessage
                it.assertThat(unauthenticatedMessagePublished.header.source).isEqualTo(member.toAvro())
                it.assertThat(unauthenticatedMessagePublished.header.destination).isEqualTo(mgm.toAvro())
                it.assertThat(unauthenticatedMessagePublished.payload).isEqualTo(ByteBuffer.wrap(UNAUTH_REQUEST_BYTES))
            }
        }

        @Test
        fun `registration request contains default serial for first time registration`() {
            val capturedContext = argumentCaptor<KeyValuePairList>()
            val capturedRequest = argumentCaptor<MembershipRegistrationRequest>()
            registrationService.register(registrationResultId, member, context)
            verify(keyValuePairListSerializer, times(3)).serialize(capturedContext.capture())
            verify(registrationRequestSerializer).serialize(capturedRequest.capture())
            SoftAssertions.assertSoftly {
                it.assertThat(capturedContext.firstValue.toMap()).doesNotContainKey(SERIAL)
                it.assertThat(capturedContext.secondValue.toMap()).doesNotContainKey(SERIAL)
                it.assertThat(capturedContext.thirdValue.toMap()).doesNotContainKey(SERIAL)
                it.assertThat(capturedRequest.firstValue.serial).isEqualTo(0)
            }
        }

        @Test
        fun `registration request contains current serial for re-registration`() {
            val memberInfo = mock<MemberInfo> {
                on { mgmProvidedContext } doReturn mock()
                on { serial } doReturn 4
            }
            whenever(groupReader.lookup(eq(memberName), any())).doReturn(memberInfo)
            val capturedRequest = argumentCaptor<MembershipRegistrationRequest>()
            registrationService.register(registrationResultId, member, context)
            verify(registrationRequestSerializer).serialize(capturedRequest.capture())
            assertThat(capturedRequest.firstValue.serial).isEqualTo(4)
        }

        @Test
        fun `registration request contains serial from registration context when included`() {
            val context = mapOf(
                SESSION_KEY_ID_KEY to SESSION_KEY_ID,
                "corda.session.keys.0.signature.spec" to SignatureSpecs.ECDSA_SHA512.signatureName,
                "corda.endpoints.0.connectionURL" to "https://localhost:1080",
                "corda.endpoints.0.protocolVersion" to "1",
                LEDGER_KEY_ID_KEY to LEDGER_KEY_ID,
                "corda.ledger.keys.0.signature.spec" to SignatureSpecs.ECDSA_SHA512.signatureName,
                "corda.serial" to "12"
            )
            val capturedRequest = argumentCaptor<MembershipRegistrationRequest>()
            registrationService.register(registrationResultId, member, context)
            verify(registrationRequestSerializer).serialize(capturedRequest.capture())
            assertThat(capturedRequest.firstValue.serial).isEqualTo(12)
        }

        @Test
        fun `registration request contains the signature`() {
            val capturedRequest = argumentCaptor<RegistrationRequest>()
            registrationService.register(registrationResultId, member, context)
            verify(membershipPersistenceClient).persistRegistrationRequest(eq(member), capturedRequest.capture(), any())
            assertThat(capturedRequest.firstValue.memberContext.signature).isEqualTo(
                CryptoSignatureWithKey(
                    ByteBuffer.wrap(keyEncodingService.encodeAsByteArray(mockSignature.by)),
                    ByteBuffer.wrap(mockSignature.bytes)
                )
            )
            assertThat(capturedRequest.firstValue.memberContext.signatureSpec.signatureName)
                .isEqualTo(SignatureSpecs.ECDSA_SHA512.signatureName)
        }

        @Test
        fun `registration request contains the context submitted by member - without any additional information or key mapping`() {
            val contextBytes = byteArrayOf(1, 2, 3, 4, 5, 6)
            whenever(
                keyValuePairListSerializer.serialize(
                    context.filterNot { it.key == SERIAL || it.key == PRE_AUTH_TOKEN }.toWire()
                )
            ).thenReturn(contextBytes)
            val capturedRequest = argumentCaptor<RegistrationRequest>()
            registrationService.register(registrationResultId, member, context)
            verify(membershipPersistenceClient).persistRegistrationRequest(eq(member), capturedRequest.capture(), any())
            assertThat(capturedRequest.firstValue.memberContext.data.toBytes()).isEqualTo(
                contextBytes
            )
        }

        @Test
        fun `registration successfully persist the status to new`() {
            val status = argumentCaptor<RegistrationRequest>()
            whenever(
                membershipPersistenceClient.persistRegistrationRequest(
                    eq(member),
                    status.capture(),
                    any(),
                )
            ).doReturn(
                persistenceOperation
            )

            registrationService.register(registrationResultId, member, context)

            assertThat(status.firstValue.status).isEqualTo(RegistrationStatus.SENT_TO_MGM)
        }

        @Test
        fun `contexts are constructed as expected`() {
            registrationService.register(registrationResultId, member, context)

            val memberContext = assertDoesNotThrow { contextCaptor.firstValue }

            assertThat(memberContext.items.map { it.key }).containsExactlyInAnyOrder(
                GROUP_ID,
                PARTY_NAME,
                MEMBER_CPI_NAME,
                MEMBER_CPI_VERSION,
                MEMBER_CPI_SIGNER_HASH,
                SOFTWARE_VERSION,
                PLATFORM_VERSION,
                REGISTRATION_ID,
                URL_KEY.format(0),
                PROTOCOL_VERSION.format(0),
                PARTY_SESSION_KEYS_PEM.format(0),
                SESSION_KEYS_HASH.format(0),
                SESSION_KEYS_SIGNATURE_SPEC.format(0),
                LEDGER_KEYS_KEY.format(0),
                LEDGER_KEY_HASHES_KEY.format(0),
                LEDGER_KEY_SIGNATURE_SPEC.format(0),
                "$CUSTOM_KEY_PREFIX.0",
                "corda.test",
            )

            val registrationContext = assertDoesNotThrow { contextCaptor.secondValue }
            assertThat(registrationContext.items.map { it.key }).containsExactlyInAnyOrder(PRE_AUTH_TOKEN)
        }

        @Test
        fun `registration context with mutual TLS adds the certificate subject`() {
            whenever(gatewayConfiguration.getString("tlsType")).doReturn("MUTUAL")
            val certificate = mock<X509Certificate> {
                on { subjectX500Principal } doReturn X500Principal(mgm.x500Name.toString())
            }
            val identityInfo = mock<IdentityInfo> {
                on { tlsCertificates } doReturn listOf(certificate)
            }
            whenever(locallyHostedIdentitiesService.pollForIdentityInfo(member)).doReturn(identityInfo)

            registrationService.register(registrationResultId, member, context)

            val memberContext = assertDoesNotThrow { contextCaptor.firstValue }

            assertThat(memberContext.items).contains(
                KeyValuePair(
                    TLS_CERTIFICATE_SUBJECT,
                    mgm.x500Name.toString()
                )
            )
        }

        @Test
        fun `registration request keeps the session keys order`() {
            data class Key(
                val index: Int,
                val keyId: ShortHash,
                val key: PublicKey,
            )
            val keys = (0..6).map {
                val keyId = "ABC12345678$it"
                val encodedKey = keyId.toByteArray()
                val key = mock<PublicKey> {
                    on { encoded } doReturn encodedKey
                    on { algorithm } doReturn "EC"
                }
                val cryptoSigningKey = mock<CryptoSigningKey> {
                    on { publicKey } doReturn ByteBuffer.wrap(encodedKey)
                    on { id } doReturn keyId
                    on { schemeCodeName } doReturn ECDSA_SECP256R1_CODE_NAME
                    on { category } doReturn SESSION_INIT
                }
                whenever(keyEncodingService.decodePublicKey(keyId)).doReturn(key)
                whenever(keyEncodingService.decodePublicKey(encodedKey)).doReturn(key)
                whenever(keyEncodingService.encodeAsString(key)).doReturn(keyId)
                whenever(keyEncodingService.encodeAsByteArray(key)).doReturn(encodedKey)
                whenever(
                    cryptoOpsClient.lookupKeysByIds(
                        memberId.value,
                        listOf(ShortHash.of(keyId)),
                    )
                ).doReturn(listOf(cryptoSigningKey))
                Key(it, ShortHash.of(keyId), key)
            }.reversed()
            val signature = DigitalSignatureWithKey(
                keys.first().key,
                byteArrayOf(1)
            )
            whenever(
                cryptoOpsClient.sign(
                    any(),
                    same(keys.first().key),
                    any<SignatureSpec>(),
                    any(),
                    eq(emptyMap())
                )
            ).doReturn(signature)
            val context = mapOf(
                "corda.endpoints.0.connectionURL" to "https://localhost:1080",
                "corda.endpoints.0.protocolVersion" to "1",
                LEDGER_KEY_ID_KEY to LEDGER_KEY_ID,
                "corda.ledger.keys.0.signature.spec" to SignatureSpecs.ECDSA_SHA512.signatureName,
            ) +
                keys.map { "corda.session.keys.${it.index}.id" to it.keyId.value } +
                keys.map {
                    "corda.session.keys.${it.index}.signature.spec" to SignatureSpecs.ECDSA_SHA512.signatureName
                }

            registrationService.register(registrationResultId, member, context)

            assertThat(contextCaptor.firstValue.toMap())
                .containsAllEntriesOf(
                    keys.associate {
                        "corda.session.keys.${it.index}.pem" to it.keyId.value
                    }
                )
        }

        @Test
        fun `registration allows custom properties to be added`() {
            val previous = mock<MemberContext> {
                on { entries } doReturn previousRegistrationContext.entries
            }
            val newContext = mock<MemberContext> {
                on { entries } doReturn (context + mapOf("$CUSTOM_KEY_PREFIX.1" to "added")).entries
            }
            whenever(memberInfo.memberProvidedContext).doReturn(previous)
            whenever(groupReader.lookup(eq(memberName), any())).doReturn(memberInfo)

            assertDoesNotThrow {
                registrationService.register(registrationResultId, member, newContext.toMap())
            }
        }

        @Test
        fun `re-registration allows endpoints to be added`() {
            val previous = mock<MemberContext> {
                on { entries } doReturn previousRegistrationContext.entries
            }
            val newContextEntries = context.toMutableMap().apply {
                put(URL_KEY.format(1), "https://localhost:1234")
                put(PROTOCOL_VERSION.format(1), "5")
            }.entries
            val newContext = mock<MemberContext> {
                on { entries } doReturn newContextEntries
            }
            whenever(memberInfo.memberProvidedContext).doReturn(previous)
            whenever(groupReader.lookup(eq(memberName), any())).doReturn(memberInfo)

            assertDoesNotThrow {
                registrationService.register(registrationResultId, member, newContext.toMap())
            }
        }

        @Test
        fun `registration allows endpoints to be updated`() {
            val previous = mock<MemberContext> {
                on { entries } doReturn previousRegistrationContext.entries
            }
            val newContextEntries = context.toMutableMap().apply {
                put(URL_KEY.format(0), "https://localhost:1234")
                put(PROTOCOL_VERSION.format(0), "5")
            }.entries
            val newContext = mock<MemberContext> {
                on { entries } doReturn newContextEntries
            }
            whenever(memberInfo.memberProvidedContext).doReturn(previous)
            whenever(groupReader.lookup(eq(memberName), any())).doReturn(memberInfo)

            assertDoesNotThrow {
                registrationService.register(registrationResultId, member, newContext.toMap())
            }
        }

        @Test
        fun `re-registration allows endpoints to be removed`() {
            val previous = mock<MemberContext> {
                on { entries } doReturn previousRegistrationContext.toMutableMap().apply {
                    put(URL_KEY.format(1), "https://localhost:1234")
                    put(PROTOCOL_VERSION.format(1), "5")
                }.entries
            }
            val newContextEntries = context.filterNot {
                it.key == URL_KEY.format(1) || it.key == PROTOCOL_VERSION.format(1)
            }.entries
            val newContext = mock<MemberContext> {
                on { entries } doReturn newContextEntries
            }
            whenever(memberInfo.memberProvidedContext).doReturn(previous)
            whenever(groupReader.lookup(eq(memberName), any())).doReturn(memberInfo)

            assertDoesNotThrow {
                registrationService.register(registrationResultId, member, newContext.toMap())
            }
        }

        @Test
        fun `registration allows custom properties to be removed`() {
            val previous = mock<MemberContext> {
                on { entries } doReturn previousRegistrationContext.entries
            }
            val newContext = mock<MemberContext> {
                on { entries } doReturn context.filterNot { it.key == "$CUSTOM_KEY_PREFIX.0" }.entries
            }
            whenever(memberInfo.memberProvidedContext).doReturn(previous)
            whenever(groupReader.lookup(eq(memberName), any())).doReturn(memberInfo)

            assertDoesNotThrow {
                registrationService.register(registrationResultId, member, newContext.toMap())
            }
        }

        @Test
        fun `registration allows custom properties to be updated`() {
            val previous = mock<MemberContext> {
                on { entries } doReturn previousRegistrationContext.entries
            }
            val newContextEntries = context.toMutableMap().apply {
                put("$CUSTOM_KEY_PREFIX.0", "changed")
            }.entries
            val newContext = mock<MemberContext> {
                on { entries } doReturn newContextEntries
            }
            whenever(memberInfo.memberProvidedContext).doReturn(previous)
            whenever(groupReader.lookup(eq(memberName), any())).doReturn(memberInfo)

            assertDoesNotThrow {
                registrationService.register(registrationResultId, member, newContext.toMap())
            }
        }
    }

    @Nested
    inner class FailedRegistrationTests {
        @AfterEach
        fun cleanup() {
            registrationService.stop()
        }

        @Test
        fun `registration fails when coordinator is not running`() {
            val exception = assertThrows<NotReadyMembershipRegistrationException> {
                registrationService.register(registrationResultId, member, mock())
            }

            assertThat(exception)
                .hasMessageContaining("Registration failed. Reason: DynamicMemberRegistrationService is not running.")
        }

        @ParameterizedTest
        @ValueSource(
            strings = arrayOf(
                SESSION_KEY_ID_KEY,
                "corda.endpoints.0.connectionURL",
                "corda.endpoints.0.protocolVersion",
                LEDGER_KEY_ID_KEY,
            )
        )
        fun `registration fails when one context property is missing`(propertyName: String) {
            postConfigChangedEvent()
            registrationService.start()
            val testContext = context - propertyName

            assertThrows<InvalidMembershipRegistrationException> {
                registrationService.register(registrationResultId, member, testContext)
            }
        }

        @Test
        fun `registration fails when one or more context properties are numbered incorrectly`() {
            postConfigChangedEvent()
            val testProperties =
                context + mapOf(
                    "corda.ledger.keys.100.id" to "9999"
                )
            registrationService.start()

            val exception = assertThrows<InvalidMembershipRegistrationException> {
                registrationService.register(registrationResultId, member, testProperties)
            }
            assertThat(exception)
                .hasMessageContaining("The registration context is invalid: Provided ledger key IDs are incorrectly numbered.")
            registrationService.stop()
        }

        @Test
        fun `registration request fails when the session keys order is wrong`() {
            postConfigChangedEvent()
            registrationService.start()
            val badContext = context +
                ("corda.session.keys.3.id" to SESSION_KEY_ID)

            val exception = assertThrows<InvalidMembershipRegistrationException> {
                registrationService.register(registrationResultId, member, badContext)
            }

            assertThat(exception).hasMessageContaining("Provided session key IDs are incorrectly numbered")
        }

        @Test
        fun `registration request fails when the session keys are missing`() {
            postConfigChangedEvent()
            registrationService.start()
            val badContext = context - SESSION_KEY_ID_KEY

            val exception = assertThrows<InvalidMembershipRegistrationException> {
                registrationService.register(registrationResultId, member, badContext)
            }

            assertThat(exception).hasMessageContaining("No session key ID was provided")
        }

        @Test
        fun `registration request fails when the session keys are invalid`() {
            postConfigChangedEvent()
            registrationService.start()
            val contextWithInvalidSessionKey = context.plus(SESSION_KEY_ID_KEY to " ")

            val exception = assertThrows<InvalidMembershipRegistrationException> {
                registrationService.register(registrationResultId, member, contextWithInvalidSessionKey)
            }

            assertThat(exception).hasMessageContaining("Invalid value for key ID $SESSION_KEY_ID_KEY.")
            assertThat(exception).hasMessageContaining("Hex string has length of 1 but should be 12 characters")
        }

        @Test
        fun `registration request fails when the ledger key is invalid`() {
            postConfigChangedEvent()
            registrationService.start()
            val contextWithInvalidLedgerKey = context.plus(LEDGER_KEY_ID_KEY to " ")

            val exception = assertThrows<InvalidMembershipRegistrationException> {
                registrationService.register(registrationResultId, member, contextWithInvalidLedgerKey)
            }

            assertThat(exception).hasMessageContaining("Invalid value for key ID $LEDGER_KEY_ID_KEY.")
            assertThat(exception).hasMessageContaining("Hex string has length of 1 but should be 12 characters")
        }

        @Test
        fun `registration request fails when the notary key is invalid`() {
            postConfigChangedEvent()
            registrationService.start()
            val contextWithInvalidNotaryKey = contextWithoutLedgerKey + mapOf(
                String.format(ROLES_PREFIX, 0) to "notary",
                NOTARY_SERVICE_NAME to "O=MyNotaryService, L=London, C=GB",
                NOTARY_SERVICE_PROTOCOL to "net.corda.notary.MyNotaryService",
                NOTARY_KEY_ID_KEY to " ",
            )

            val exception = assertThrows<InvalidMembershipRegistrationException> {
                registrationService.register(registrationResultId, member, contextWithInvalidNotaryKey)
            }

            assertThat(exception).hasMessageContaining("Invalid value for key ID $NOTARY_KEY_ID_KEY.")
            assertThat(exception).hasMessageContaining("Hex string has length of 1 but should be 12 characters")
        }

        @Test
        fun `registration fails if custom field validation fails`() {
            whenever(registrationContextCustomFieldsVerifier.constructed().first().verify(context)).thenReturn(
                RegistrationContextCustomFieldsVerifier.Result.Failure("")
            )
            postConfigChangedEvent()
            registrationService.start()

            assertThrows<InvalidMembershipRegistrationException> {
                registrationService.register(registrationResultId, member, context)
            }
        }

        @Test
        fun `registration fails if ledger key has the wrong category`() {
            whenever(ledgerCryptoSigningKey.category).doReturn(PRE_AUTH)
            postConfigChangedEvent()
            registrationService.start()

            assertThrows<InvalidMembershipRegistrationException> {
                registrationService.register(registrationResultId, member, context)
            }
        }

        @Test
        fun `registration fails if session key has the wrong category`() {
            whenever(sessionCryptoSigningKey.category).doReturn(LEDGER)
            postConfigChangedEvent()
            registrationService.start()

            assertThrows<InvalidMembershipRegistrationException> {
                registrationService.register(registrationResultId, member, context)
            }
        }

        @Test
        fun `registration fails if the registration context doesn't match the schema`() {
            postConfigChangedEvent()
            val err = "ERROR-MESSAGE"
            val errReason = "ERROR-REASON"
            whenever(
                membershipSchemaValidator.validateRegistrationContext(
                    eq(MembershipSchema.RegistrationContextSchema.DynamicMember),
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
                registrationService.register(registrationResultId, member, context)
            }

            assertThat(exception)
                .hasMessageContaining(err)
                .hasMessageContaining(errReason)
            registrationService.stop()
        }

        @Test
        fun `registration fails if mgm's ecdh key is missing`() {
            postConfigChangedEvent()
            whenever(mgmInfo.ecdhKey).thenReturn(null)
            registrationService.start()

            val exception = assertThrows<InvalidMembershipRegistrationException> {
                registrationService.register(registrationResultId, member, context)
            }
            assertThat(exception).hasMessageContaining(
                "Registration failed. Reason: MGM's ECDH key is missing."
            )
            registrationService.stop()
        }

        @Test
        fun `registration fails if the session key spec is invalid`() {
            val registrationContext = context + ("corda.session.keys.0.signature.spec" to "Nop")
            postConfigChangedEvent()
            registrationService.start()

            assertThrows<InvalidMembershipRegistrationException> {
                registrationService.register(registrationResultId, member, registrationContext)
            }
        }

        @Test
        fun `registration fails if the ledger key spec is invalid`() {
            val registrationContext = context + ("corda.ledger.keys.0.signature.spec" to "Nop")
            postConfigChangedEvent()
            registrationService.start()

            assertThrows<InvalidMembershipRegistrationException> {
                registrationService.register(registrationResultId, member, registrationContext)
            }
        }

        @Test
        fun `registration fails if virtual node info cannot be found`() {
            postConfigChangedEvent()
            registrationService.start()
            val noVNodeMember = HoldingIdentity(
                MemberX500Name.parse("O=Bob, C=IE, L=DUB"),
                UUID(0, 1).toString()
            )
            whenever(virtualNodeInfoReadService.get(eq(noVNodeMember))).thenReturn(null)

            val exception = assertThrows<NotReadyMembershipRegistrationException> {
                registrationService.register(registrationResultId, noVNodeMember, context)
            }

            assertThat(exception).hasMessageContaining("Could not find virtual node")
            registrationService.stop()
        }

        @Test
        fun `registration context with mutual TLS will fail if the identity can not be found`() {
            whenever(gatewayConfiguration.getString("tlsType")).doReturn("MUTUAL")
            postConfigChangedEvent()
            registrationService.start()

            val exception = assertThrows<NotReadyMembershipRegistrationException> {
                registrationService.register(registrationResultId, member, context)
            }

            assertThat(exception).hasMessageContaining("is not locally hosted")
        }

        @Test
        fun `registration context with mutual TLS will fail if the identity has no certificates`() {
            whenever(gatewayConfiguration.getString("tlsType")).doReturn("MUTUAL")
            val identityInfo = mock<IdentityInfo> {
                on { tlsCertificates } doReturn emptyList()
            }
            whenever(locallyHostedIdentitiesService.pollForIdentityInfo(member)).doReturn(identityInfo)
            postConfigChangedEvent()
            registrationService.start()

            val exception = assertThrows<NotReadyMembershipRegistrationException> {
                registrationService.register(registrationResultId, member, context)
            }

            assertThat(exception).hasMessageContaining("is missing TLS certificates")
        }

        @Test
        fun `registration fails when MGM info cannot be found`() {
            // return non-MGM info on lookup
            val memberInfo = mock<MemberInfo> {
                on { mgmProvidedContext } doReturn mock()
            }
            whenever(groupReader.lookup()).doReturn(listOf(memberInfo))

            postConfigChangedEvent()
            registrationService.start()

            val exception = assertThrows<NotReadyMembershipRegistrationException> {
                registrationService.register(registrationResultId, member, context)
            }
            assertThat(exception).hasMessageContaining("MGM information")
        }

        @Test
        fun `registration fails when notary related properties are removed`() {
            val previous = mock<MemberContext> {
                on { entries } doReturn previousNotaryRegistrationContext.entries
            }
            val newContext = mock<MemberContext> {
                on { entries } doReturn context.entries
            }
            whenever(memberInfo.memberProvidedContext).doReturn(previous)
            whenever(groupReader.lookup(eq(memberName), any())).doReturn(memberInfo)

            postConfigChangedEvent()
            registrationService.start()

            val exception = assertThrows<InvalidMembershipRegistrationException> {
                registrationService.register(registrationResultId, member, newContext.toMap())
            }
            assertThat(exception).hasMessageContaining("cannot be added, removed or updated")
        }

        @Test
        fun `registration fails when ledger key related properties are updated`() {
            val previous = mock<MemberContext> {
                on { entries } doReturn previousRegistrationContext.entries
            }
            val changedLedgerKey = createTestCryptoSigningKey(LEDGER)
            val newContextEntries = context.toMutableMap().apply {
                put(LEDGER_KEY_ID_KEY, changedLedgerKey.id)
            }.entries
            val newContext = mock<MemberContext> {
                on { entries } doReturn newContextEntries
            }
            whenever(cryptoOpsClient.lookupKeysByIds(memberId.value, listOf(ShortHash.of(TEST_KEY_ID))))
                .doReturn(listOf(changedLedgerKey))
            whenever(memberInfo.memberProvidedContext).doReturn(previous)
            whenever(groupReader.lookup(eq(memberName), any())).doReturn(memberInfo)

            postConfigChangedEvent()
            registrationService.start()

            val exception = assertThrows<InvalidMembershipRegistrationException> {
                registrationService.register(registrationResultId, member, newContext.toMap())
            }
            assertThat(exception).hasMessageContaining("cannot be added, removed or updated")
        }

        @Test
        fun `registration fails when session key related properties are updated`() {
            val previous = mock<MemberContext> {
                on { entries } doReturn previousRegistrationContext.entries
            }
            val changedSessionKey = createTestCryptoSigningKey(SESSION_INIT)
            val newContextEntries = context.toMutableMap().apply {
                put(SESSION_KEY_ID_KEY, changedSessionKey.id)
            }.entries
            val newContext = mock<MemberContext> {
                on { entries } doReturn newContextEntries
            }
            whenever(cryptoOpsClient.lookupKeysByIds(memberId.value, listOf(ShortHash.of(TEST_KEY_ID))))
                .doReturn(listOf(changedSessionKey))
            whenever(memberInfo.memberProvidedContext).doReturn(previous)
            whenever(groupReader.lookup(eq(memberName), any())).doReturn(memberInfo)

            postConfigChangedEvent()
            registrationService.start()

            val exception = assertThrows<InvalidMembershipRegistrationException> {
                registrationService.register(registrationResultId, member, newContext.toMap())
            }
            assertThat(exception).hasMessageContaining("cannot be added, removed or updated")
        }

        @Test
        fun `registration fails when notary related properties are updated`() {
            val previous = mock<MemberContext> {
                on { entries } doReturn previousNotaryRegistrationContext.entries + mapOf(
                    NOTARY_SERVICE_NAME to "O=MyNotaryService, L=London, C=GB",
                ).entries
            }
            val newContext = mock<MemberContext> {
                on { entries } doReturn contextWithoutLedgerKey.entries + mapOf(
                    String.format(ROLES_PREFIX, 0) to "notary",
                    NOTARY_SERVICE_PROTOCOL to "net.corda.notary.MyNotaryService",
                    NOTARY_SERVICE_NAME to "O=ChangedNotaryService, L=London, C=GB",
                    NOTARY_KEY_ID_KEY to NOTARY_KEY_ID,
                ).entries
            }
            whenever(memberInfo.memberProvidedContext).doReturn(previous)
            whenever(groupReader.lookup(eq(memberName), any())).doReturn(memberInfo)

            postConfigChangedEvent()
            registrationService.start()

            val exception = assertThrows<InvalidMembershipRegistrationException> {
                registrationService.register(registrationResultId, member, newContext.toMap())
            }
            assertThat(exception).hasMessageContaining("cannot be added, removed or updated")
        }

        @Test
        fun `registration fails when ledger key related properties are added`() {
            val previous = mock<MemberContext> {
                on { entries } doReturn previousRegistrationContext.entries
            }
            val newLedgerKey = createTestCryptoSigningKey(LEDGER)
            val newContextEntries = context.toMutableMap().apply {
                put(LEDGER_KEYS_ID.format(1), newLedgerKey.id)
            }.entries
            val newContext = mock<MemberContext> {
                on { entries } doReturn newContextEntries
            }
            whenever(
                cryptoOpsClient.lookupKeysByIds(
                    memberId.value,
                    listOf(ShortHash.of(LEDGER_KEY_ID), ShortHash.of(TEST_KEY_ID))
                )
            ).doReturn(listOf(ledgerCryptoSigningKey, newLedgerKey))
            whenever(memberInfo.memberProvidedContext).doReturn(previous)
            whenever(groupReader.lookup(eq(memberName), any())).doReturn(memberInfo)

            postConfigChangedEvent()
            registrationService.start()

            val exception = assertThrows<InvalidMembershipRegistrationException> {
                registrationService.register(registrationResultId, member, newContext.toMap())
            }
            assertThat(exception).hasMessageContaining("cannot be added, removed or updated")
        }

        @Test
        fun `registration fails when session key related properties are added`() {
            val previous = mock<MemberContext> {
                on { entries } doReturn previousRegistrationContext.entries
            }
            val newSessionKey = createTestCryptoSigningKey(SESSION_INIT)
            val newContextEntries = context.toMutableMap().apply {
                put(PARTY_SESSION_KEYS_ID.format(1), newSessionKey.id)
            }.entries
            val newContext = mock<MemberContext> {
                on { entries } doReturn newContextEntries
            }
            whenever(cryptoOpsClient.lookupKeysByIds(memberId.value, listOf(ShortHash.of(TEST_KEY_ID))))
                .doReturn(listOf(newSessionKey))
            whenever(memberInfo.memberProvidedContext).doReturn(previous)
            whenever(groupReader.lookup(eq(memberName), any())).doReturn(memberInfo)

            postConfigChangedEvent()
            registrationService.start()

            val exception = assertThrows<InvalidMembershipRegistrationException> {
                registrationService.register(registrationResultId, member, newContext.toMap())
            }
            assertThat(exception).hasMessageContaining("cannot be added, removed or updated")
        }

        @Test
        fun `registration fails when invalid key scheme is used for session key`() {
            val sessionCryptoSigningKeyWithInvalidScheme: CryptoSigningKey = mock {
                on { publicKey } doReturn ByteBuffer.wrap(SESSION_KEY.toByteArray())
                on { id } doReturn SESSION_KEY_ID
                on { schemeCodeName } doReturn EDDSA_ED25519_CODE_NAME
                on { category } doReturn SESSION_INIT
            }
            whenever(cryptoOpsClient.lookupKeysByIds(memberId.value, listOf(ShortHash.of(SESSION_KEY_ID)))).doReturn(
                listOf(sessionCryptoSigningKeyWithInvalidScheme)
            )

            postConfigChangedEvent()
            registrationService.start()

            val exception = assertThrows<InvalidMembershipRegistrationException> {
                registrationService.register(registrationResultId, member, context.toMap())
            }
            assertThat(exception).hasMessageContaining("Invalid key scheme")
        }

        @Test
        fun `registration fails when invalid session key spec is used`() {
            val context = context.filterNot { it.key == SESSION_KEYS_SIGNATURE_SPEC.format(0) } +
                mapOf(SESSION_KEYS_SIGNATURE_SPEC.format(0) to SignatureSpecs.EDDSA_ED25519.signatureName)

            postConfigChangedEvent()
            registrationService.start()

            val exception = assertThrows<InvalidMembershipRegistrationException> {
                registrationService.register(registrationResultId, member, context.toMap())
            }
            assertThat(exception).hasMessageContaining("Invalid key spec ${SignatureSpecs.EDDSA_ED25519.signatureName}.")
        }

        @Test
        fun `registration fails when notary related properties are added`() {
            val previous = mock<MemberContext> {
                on { entries } doReturn previousRegistrationContext.entries
            }
            val newContextEntries = contextWithoutLedgerKey.toMutableMap().apply {
                put(String.format(ROLES_PREFIX, 0), "notary")
                put(NOTARY_SERVICE_PROTOCOL, "net.corda.notary.MyNotaryService")
                put(NOTARY_SERVICE_NAME, "O=MyNotaryService, L=London, C=GB")
                put(NOTARY_KEY_ID_KEY, NOTARY_KEY_ID)
            }.entries
            val newContext = mock<MemberContext> {
                on { entries } doReturn newContextEntries
            }
            whenever(memberInfo.memberProvidedContext).doReturn(previous)
            whenever(groupReader.lookup(eq(memberName), any())).doReturn(memberInfo)

            postConfigChangedEvent()
            registrationService.start()

            val exception = assertThrows<InvalidMembershipRegistrationException> {
                registrationService.register(registrationResultId, member, newContext.toMap())
            }
            assertThat(exception).hasMessageContaining("cannot be added, removed or updated")
        }

        @Test
        fun `re-registration fails when MGM is on 50000 platform - request contains serial`() {
            val mgmInfo = mock<MemberInfo> {
                on { mgmProvidedContext } doReturn mock()
                on { memberProvidedContext } doReturn mock()
                on { isMgm } doReturn true
                on { platformVersion } doReturn 50000
            }
            whenever(groupReader.lookup()).doReturn(listOf(mgmInfo))

            postConfigChangedEvent()
            registrationService.start()

            val contextWithInvalidSerial = context + mapOf(SERIAL to "2")
            val exception = assertThrows<InvalidMembershipRegistrationException> {
                registrationService.register(registrationResultId, member, contextWithInvalidSerial)
            }
            assertThat(exception).hasMessageContaining("re-registration is not supported.")
        }

        @Test
        fun `re-registration fails when MGM is on 50000 platform - serial is from existing info`() {
            val mgmInfo = mock<MemberInfo> {
                on { mgmProvidedContext } doReturn mock()
                on { memberProvidedContext } doReturn mock()
                on { isMgm } doReturn true
                on { platformVersion } doReturn 50000
            }
            whenever(groupReader.lookup()).doReturn(listOf(mgmInfo))

            val previous = mock<MemberContext> {
                on { entries } doReturn previousRegistrationContext.entries
            }
            val memberInfo = mock<MemberInfo> {
                on { memberProvidedContext } doReturn previous
                on { mgmProvidedContext } doReturn mock()
                on { name } doReturn memberName
                on { groupId } doReturn GROUP_NAME
                on { serial } doReturn 1
            }
            whenever(groupReader.lookup(eq(memberName), any())).doReturn(memberInfo)

            postConfigChangedEvent()
            registrationService.start()

            val exception = assertThrows<InvalidMembershipRegistrationException> {
                registrationService.register(registrationResultId, member, context)
            }
            assertThat(exception).hasMessageContaining("re-registration is not supported.")
        }

        @Test
        fun `re-registration fails when MGM is on 50000 platform - serial in request is incorrect and would let re-registration`() {
            val mgmInfo = mock<MemberInfo> {
                on { mgmProvidedContext } doReturn mock()
                on { memberProvidedContext } doReturn mock()
                on { isMgm } doReturn true
                on { platformVersion } doReturn 50000
            }
            whenever(groupReader.lookup()).doReturn(listOf(mgmInfo))

            val previous = mock<MemberContext> {
                on { entries } doReturn previousRegistrationContext.entries
            }
            val memberInfo = mock<MemberInfo> {
                on { memberProvidedContext } doReturn previous
                on { mgmProvidedContext } doReturn mock()
                on { name } doReturn memberName
                on { groupId } doReturn GROUP_NAME
                on { serial } doReturn 1
            }
            whenever(groupReader.lookup(eq(memberName), any())).doReturn(memberInfo)

            postConfigChangedEvent()
            registrationService.start()

            val contextSuggestingInitialRegistration = context + mapOf(SERIAL to "0")
            val exception = assertThrows<InvalidMembershipRegistrationException> {
                registrationService.register(registrationResultId, member, contextSuggestingInitialRegistration)
            }
            assertThat(exception).hasMessageContaining("re-registration is not supported.")
        }

        @Test
        fun `declined if invalid endpoint is provided during re-registration`() {
            val previous = mock<MemberContext> {
                on { entries } doReturn previousRegistrationContext.entries
            }
            val changedUrl = "invalidURL"
            val changedProtocolVersion = 2
            val newContextEntries = context.toMutableMap().apply {
                put(URL_KEY.format(0), changedUrl)
                put(PROTOCOL_VERSION.format(0), changedProtocolVersion.toString())
            }.entries
            val newContext = mock<MemberContext> {
                on { entries } doReturn newContextEntries
            }
            whenever(memberInfo.memberProvidedContext).doReturn(previous)
            whenever(groupReader.lookup(eq(memberName), any())).doReturn(memberInfo)

            postConfigChangedEvent()
            registrationService.start()

            val exception = assertThrows<InvalidMembershipRegistrationException> {
                registrationService.register(registrationResultId, member, newContext.toMap())
            }
            assertThat(exception).hasMessageContaining("endpoint URL")
        }
    }

    @Nested
    inner class NotaryRoleTests {
        @BeforeEach
        fun setup() {
            postConfigChangedEvent()
            registrationService.start()
        }

        @AfterEach
        fun cleanup() {
            registrationService.stop()
        }

        @Test
        fun `re-registration allows optional backchain flag to be set to true from null`() {
            val notaryKeyConvertedFields = mapOf(
                NOTARY_KEY_PEM_KEY to NOTARY_KEY_PEM,
                NOTARY_KEY_HASH_KEY to NOTARY_KEY_HASH,
                NOTARY_KEY_SIG_SPEC_KEY to NOTARY_KEY_SIG_SPEC
            )

            val previous = mock<MemberContext> {
                on { entries } doReturn (previousNotaryRegistrationContext + notaryKeyConvertedFields).entries
            }
            val newContextEntries = (previousNotaryRegistrationContext).toMutableMap().apply {
                put(SESSION_KEY_ID_KEY, SESSION_KEY_ID)
                put(NOTARY_KEY_ID_KEY, NOTARY_KEY_ID)
                put(NOTARY_SERVICE_BACKCHAIN_REQUIRED, "true")
            }.entries

            val newContext = mock<MemberContext> {
                on { entries } doReturn newContextEntries
            }
            whenever(memberInfo.memberProvidedContext).doReturn(previous)
            whenever(groupReader.lookup(eq(memberName), any())).doReturn(memberInfo)

            assertDoesNotThrow {
                registrationService.register(registrationResultId, member, newContext.toMap())
            }
        }

        @Test
        fun `re-registration with previous context that contains the notary key ID won't fail`() {
            val memberContext = argumentCaptor<KeyValuePairList>()
            whenever(keyValuePairListSerializer.serialize(memberContext.capture())).doReturn(MEMBER_CONTEXT_BYTES)
            val notaryKeyConvertedFields = mapOf(
                NOTARY_KEY_PEM_KEY to NOTARY_KEY_PEM,
                NOTARY_KEY_HASH_KEY to NOTARY_KEY_HASH,
                NOTARY_KEY_SIG_SPEC_KEY to NOTARY_KEY_SIG_SPEC,
                NOTARY_KEY_ID_KEY to "123456",
            )

            val previous = mock<MemberContext> {
                on { entries } doReturn (previousNotaryRegistrationContext + notaryKeyConvertedFields).entries
            }
            val newContextEntries = (previousNotaryRegistrationContext).toMutableMap().apply {
                put(SESSION_KEY_ID_KEY, SESSION_KEY_ID)
                put(NOTARY_KEY_ID_KEY, NOTARY_KEY_ID)
            }.entries

            val newContext = mock<MemberContext> {
                on { entries } doReturn newContextEntries
            }
            whenever(memberInfo.memberProvidedContext).doReturn(previous)
            whenever(groupReader.lookup(eq(memberName), any())).doReturn(memberInfo)

            registrationService.register(registrationResultId, member, newContext.toMap())

            assertThat(memberContext.firstValue.toMap())
                .doesNotContainKey(NOTARY_KEY_ID_KEY)
        }

        @Test
        fun `re-registration does not allow optional backchain flag to be set to false from null`() {
            val notaryKeyConvertedFields = mapOf(
                NOTARY_KEY_PEM_KEY to NOTARY_KEY_PEM,
                NOTARY_KEY_HASH_KEY to NOTARY_KEY_HASH,
                NOTARY_KEY_SIG_SPEC_KEY to NOTARY_KEY_SIG_SPEC
            )

            val previous = mock<MemberContext> {
                on { entries } doReturn (previousNotaryRegistrationContext + notaryKeyConvertedFields).entries
            }
            val newContextEntries = (previousNotaryRegistrationContext).toMutableMap().apply {
                put(SESSION_KEY_ID_KEY, SESSION_KEY_ID)
                put(NOTARY_KEY_ID_KEY, NOTARY_KEY_ID)
                put(NOTARY_SERVICE_BACKCHAIN_REQUIRED, "false")
            }.entries

            val newContext = mock<MemberContext> {
                on { entries } doReturn newContextEntries
            }
            whenever(memberInfo.memberProvidedContext).doReturn(previous)
            whenever(groupReader.lookup(eq(memberName), any())).doReturn(memberInfo)

            val registrationException = assertThrows<InvalidMembershipRegistrationException> {
                registrationService.register(registrationResultId, member, newContext.toMap())
            }

            assertThat(registrationException)
                .hasStackTraceContaining("Optional back-chain flag can only move from 'none' to 'true' during re-registration.")
        }

        @Test
        fun `re-registration does not allow optional backchain flag to be set to false from true`() {
            val notaryKeyConvertedFields = mapOf(
                NOTARY_KEY_PEM_KEY to NOTARY_KEY_PEM,
                NOTARY_KEY_HASH_KEY to NOTARY_KEY_HASH,
                NOTARY_KEY_SIG_SPEC_KEY to NOTARY_KEY_SIG_SPEC
            )

            val previousNotaryRegistrationContextWithBackchainFlag = previousNotaryRegistrationContext +
                mapOf(NOTARY_SERVICE_BACKCHAIN_REQUIRED to "true")

            val previous = mock<MemberContext> {
                on { entries } doReturn (previousNotaryRegistrationContextWithBackchainFlag + notaryKeyConvertedFields).entries
            }
            val newContextEntries = (previousNotaryRegistrationContextWithBackchainFlag).toMutableMap().apply {
                put(SESSION_KEY_ID_KEY, SESSION_KEY_ID)
                put(NOTARY_KEY_ID_KEY, NOTARY_KEY_ID)
                put(NOTARY_SERVICE_BACKCHAIN_REQUIRED, "false")
            }.entries

            val newContext = mock<MemberContext> {
                on { entries } doReturn newContextEntries
            }
            whenever(memberInfo.memberProvidedContext).doReturn(previous)
            whenever(groupReader.lookup(eq(memberName), any())).doReturn(memberInfo)

            val registrationException = assertThrows<InvalidMembershipRegistrationException> {
                registrationService.register(registrationResultId, member, newContext.toMap())
            }

            assertThat(registrationException)
                .hasStackTraceContaining("Optional back-chain flag can only move from 'none' to 'true' during re-registration.")
        }

        @Test
        fun `registration fails when notary keys are numbered incorrectly`() {
            val testProperties =
                context + mapOf(
                    String.format(ROLES_PREFIX, 0) to "notary",
                    NOTARY_SERVICE_PROTOCOL to "net.corda.notary.MyNotaryService",
                    NOTARY_SERVICE_NAME to "O=MyNotaryService, L=London, C=GB",
                    "corda.notary.keys.100.id" to LEDGER_KEY_ID,
                )

            assertThrows<InvalidMembershipRegistrationException> {
                registrationService.register(registrationResultId, member, testProperties)
            }
        }

        @Test
        fun `registration pass when notary keys are numbered correctly`() {
            val testProperties =
                contextWithoutLedgerKey + mapOf(
                    String.format(ROLES_PREFIX, 0) to "notary",
                    NOTARY_SERVICE_PROTOCOL to "net.corda.notary.MyNotaryService",
                    NOTARY_SERVICE_NAME to "O=MyNotaryService, L=London, C=GB",
                    NOTARY_KEY_ID_KEY to NOTARY_KEY_ID,
                )

            assertDoesNotThrow {
                registrationService.register(registrationResultId, member, testProperties)
            }
        }

        @Test
        fun `registration fails when ledger keys are specified for a notary vnode`() {
            val testProperties = context + mapOf(
                String.format(ROLES_PREFIX, 0) to "notary",
                NOTARY_SERVICE_PROTOCOL to "net.corda.notary.MyNotaryService",
                NOTARY_SERVICE_NAME to "O=MyNotaryService, L=London, C=GB",
                NOTARY_KEY_ID_KEY to NOTARY_KEY_ID,
            )

            assertThrows<InvalidMembershipRegistrationException> {
                registrationService.register(registrationResultId, member, testProperties)
            }
        }

        @Test
        fun `registration fails when notary keys has the wrong category`() {
            whenever(notaryCryptoSigningKey.category).doReturn(SESSION_INIT)
            val testProperties =
                context + mapOf(
                    String.format(ROLES_PREFIX, 0) to "notary",
                    NOTARY_SERVICE_PROTOCOL to "net.corda.notary.MyNotaryService",
                    NOTARY_SERVICE_NAME to "O=MyNotaryService, L=London, C=GB",
                    NOTARY_KEY_ID_KEY to NOTARY_KEY_ID,
                )

            assertThrows<InvalidMembershipRegistrationException> {
                registrationService.register(registrationResultId, member, testProperties)
            }
        }

        @Test
        fun `registration adds notary information when notary role is set`() {
            val memberContext = argumentCaptor<KeyValuePairList>()
            whenever(keyValuePairListSerializer.serialize(memberContext.capture())).doReturn(MEMBER_CONTEXT_BYTES)
            val testProperties =
                contextWithoutLedgerKey + mapOf(
                    String.format(ROLES_PREFIX, 0) to "notary",
                    NOTARY_SERVICE_PROTOCOL to "net.corda.notary.MyNotaryService",
                    NOTARY_SERVICE_NAME to "O=MyNotaryService, L=London, C=GB",
                    NOTARY_KEY_ID_KEY to NOTARY_KEY_ID,
                )

            registrationService.register(registrationResultId, member, testProperties)

            assertThat(memberContext.firstValue.toMap())
                .containsEntry(String.format(ROLES_PREFIX, 0), "notary")
                .containsKey(NOTARY_SERVICE_NAME)
                .doesNotContainKey(NOTARY_KEY_ID_KEY)
                .containsEntry(String.format(NOTARY_KEY_PEM_KEY, 0), "1234")
                .containsKey(String.format(NOTARY_KEY_HASH_KEY, 0))
                .containsEntry(String.format(NOTARY_KEY_SPEC, 0), SignatureSpecs.ECDSA_SHA256.signatureName)
        }

        @Test
        fun `registration adds session spec if needed`() {
            val memberContext = argumentCaptor<KeyValuePairList>()
            whenever(keyValuePairListSerializer.serialize(memberContext.capture())).doReturn(MEMBER_CONTEXT_BYTES)
            val registrationContext = context - "corda.session.keys.0.signature.spec"

            registrationService.register(registrationResultId, member, registrationContext)

            assertThat(memberContext.firstValue.toMap())
                .containsEntry("corda.session.keys.0.signature.spec", SignatureSpecs.ECDSA_SHA256.signatureName)
        }

        @Test
        fun `registration adds ledger spec if needed`() {
            val memberContext = argumentCaptor<KeyValuePairList>()
            whenever(keyValuePairListSerializer.serialize(memberContext.capture())).doReturn(MEMBER_CONTEXT_BYTES)
            val registrationContext = context - "corda.ledger.keys.0.signature.spec"

            registrationService.register(registrationResultId, member, registrationContext)

            assertThat(memberContext.firstValue.toMap())
                .containsEntry("corda.ledger.keys.0.signature.spec", SignatureSpecs.ECDSA_SHA256.signatureName)
        }

        @Test
        fun `registration fails when notary service is invalid`() {
            val testProperties =
                context + mapOf(
                    ROLES_PREFIX to "notary",
                    NOTARY_SERVICE_NAME to "Hello world",
                    NOTARY_KEY_ID_KEY to NOTARY_KEY_ID,
                    NOTARY_SERVICE_PROTOCOL to "net.corda.notary.MyNotaryService",
                )

            assertThrows<InvalidMembershipRegistrationException> {
                registrationService.register(registrationResultId, member, testProperties)
            }
        }

        @Test
        fun `registration pass when notary service is valid`() {
            val testProperties =
                contextWithoutLedgerKey + mapOf(
                    String.format(ROLES_PREFIX, 0) to "notary",
                    NOTARY_SERVICE_PROTOCOL to "net.corda.notary.MyNotaryService",
                    NOTARY_SERVICE_NAME to "O=MyNotaryService, L=London, C=GB",
                    NOTARY_KEY_ID_KEY to NOTARY_KEY_ID,
                )

            assertDoesNotThrow {
                registrationService.register(registrationResultId, member, testProperties)
            }
        }

        @Test
        fun `registration fails when notary keys are not provided`() {
            val testProperties =
                contextWithoutLedgerKey + mapOf(
                    String.format(ROLES_PREFIX, 0) to "notary",
                    NOTARY_SERVICE_NAME to "O=MyNotaryService, L=London, C=GB",
                    NOTARY_SERVICE_PROTOCOL to "net.corda.notary.MyNotaryService",
                )

            val exception = assertThrows<InvalidMembershipRegistrationException> {
                registrationService.register(registrationResultId, member, testProperties)
            }

            assertThat(exception).hasMessageContaining("notary key")
        }

        @Test
        fun `registration fails when protocol versions are numbered incorrectly`() {
            val testProperties =
                context + mapOf(
                    String.format(ROLES_PREFIX, 0) to "notary",
                    NOTARY_SERVICE_NAME to "O=MyNotaryService, L=London, C=GB",
                    String.format(NOTARY_SERVICE_PROTOCOL_VERSIONS, 5) to "1",
                    NOTARY_KEY_ID_KEY to LEDGER_KEY_ID,
                )

            assertThrows<InvalidMembershipRegistrationException> {
                registrationService.register(registrationResultId, member, testProperties)
            }
        }

        @Test
        fun `ledger keys are optional when notary role is set`() {
            postConfigChangedEvent()
            val testProperties =
                context.filterNot { it.key.startsWith("corda.ledger") } + mapOf(
                    String.format(ROLES_PREFIX, 0) to "notary",
                    NOTARY_SERVICE_PROTOCOL to "net.corda.notary.MyNotaryService",
                    NOTARY_SERVICE_NAME to "O=MyNotaryService, L=London, C=GB",
                    NOTARY_KEY_ID_KEY to NOTARY_KEY_ID,
                )
            registrationService.start()

            assertDoesNotThrow {
                registrationService.register(registrationResultId, member, testProperties)
            }
        }
    }

    @Nested
    inner class LifecycleTests {
        @Test
        fun `starting the service succeeds`() {
            registrationService.start()
            assertThat(registrationService.isRunning).isTrue
            verify(coordinator).start()
        }

        @Test
        fun `stopping the service succeeds`() {
            registrationService.start()
            registrationService.stop()
            assertThat(registrationService.isRunning).isFalse
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
                .isEqualTo(setOf(ConfigKeys.BOOT_CONFIG, ConfigKeys.MESSAGING_CONFIG))

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
    }
}
