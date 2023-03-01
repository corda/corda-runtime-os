package net.corda.membership.impl.registration.dynamic.member

import com.typesafe.config.ConfigFactory
import net.corda.configuration.read.ConfigChangedEvent
import net.corda.configuration.read.ConfigurationGetService
import net.corda.configuration.read.ConfigurationReadService
import net.corda.crypto.cipher.suite.KeyEncodingService
import net.corda.crypto.client.CryptoOpsClient
import net.corda.crypto.core.CryptoConsts.Categories.LEDGER
import net.corda.crypto.core.CryptoConsts.Categories.NOTARY
import net.corda.crypto.core.CryptoConsts.Categories.PRE_AUTH
import net.corda.crypto.core.CryptoConsts.Categories.SESSION_INIT
import net.corda.crypto.core.ShortHash
import net.corda.crypto.hes.EncryptedDataWithKey
import net.corda.crypto.hes.EphemeralKeyPairEncryptor
import net.corda.data.CordaAvroSerializationFactory
import net.corda.data.CordaAvroSerializer
import net.corda.data.KeyValuePair
import net.corda.data.KeyValuePairList
import net.corda.data.crypto.wire.CryptoSigningKey
import net.corda.data.membership.common.RegistrationStatus
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
import net.corda.membership.impl.registration.TEST_PLATFORM_VERSION
import net.corda.membership.impl.registration.TEST_SOFTWARE_VERSION
import net.corda.membership.impl.registration.buildTestVirtualNodeInfo
import net.corda.membership.lib.MemberInfoExtension.Companion.ECDH_KEY
import net.corda.membership.lib.MemberInfoExtension.Companion.GROUP_ID
import net.corda.membership.lib.MemberInfoExtension.Companion.LEDGER_KEYS_KEY
import net.corda.membership.lib.MemberInfoExtension.Companion.LEDGER_KEY_HASHES_KEY
import net.corda.membership.lib.MemberInfoExtension.Companion.LEDGER_KEY_SIGNATURE_SPEC
import net.corda.membership.lib.MemberInfoExtension.Companion.MEMBER_CPI_NAME
import net.corda.membership.lib.MemberInfoExtension.Companion.MEMBER_CPI_SIGNER_HASH
import net.corda.membership.lib.MemberInfoExtension.Companion.MEMBER_CPI_VERSION
import net.corda.membership.lib.MemberInfoExtension.Companion.PARTY_NAME
import net.corda.membership.lib.MemberInfoExtension.Companion.PARTY_SESSION_KEY
import net.corda.membership.lib.MemberInfoExtension.Companion.PLATFORM_VERSION
import net.corda.membership.lib.MemberInfoExtension.Companion.PROTOCOL_VERSION
import net.corda.membership.lib.MemberInfoExtension.Companion.REGISTRATION_ID
import net.corda.membership.lib.MemberInfoExtension.Companion.SESSION_KEY_HASH
import net.corda.membership.lib.MemberInfoExtension.Companion.SESSION_KEY_SIGNATURE_SPEC
import net.corda.membership.lib.MemberInfoExtension.Companion.SOFTWARE_VERSION
import net.corda.membership.lib.MemberInfoExtension.Companion.URL_KEY
import net.corda.membership.lib.MemberInfoExtension.Companion.ecdhKey
import net.corda.membership.lib.MemberInfoExtension.Companion.groupId
import net.corda.membership.lib.MemberInfoExtension.Companion.isMgm
import net.corda.membership.lib.registration.RegistrationRequest
import net.corda.membership.lib.schema.validation.MembershipSchemaValidationException
import net.corda.membership.lib.schema.validation.MembershipSchemaValidator
import net.corda.membership.lib.schema.validation.MembershipSchemaValidatorFactory
import net.corda.membership.lib.toMap
import net.corda.membership.p2p.helpers.Verifier
import net.corda.membership.persistence.client.MembershipPersistenceClient
import net.corda.membership.persistence.client.MembershipPersistenceResult
import net.corda.membership.read.MembershipGroupReader
import net.corda.membership.read.MembershipGroupReaderProvider
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.records.Record
import net.corda.data.p2p.app.AppMessage
import net.corda.data.p2p.app.UnauthenticatedMessage
import net.corda.libs.configuration.SmartConfig
import net.corda.membership.lib.MemberInfoExtension.Companion.TLS_CERTIFICATE_SUBJECT
import net.corda.membership.locally.hosted.identities.IdentityInfo
import net.corda.membership.locally.hosted.identities.LocallyHostedIdentitiesService
import net.corda.membership.registration.InvalidMembershipRegistrationException
import net.corda.membership.registration.NotReadyMembershipRegistrationException
import net.corda.schema.Schemas
import net.corda.schema.configuration.ConfigKeys
import net.corda.schema.membership.MembershipSchema
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.crypto.DigitalSignature
import net.corda.v5.crypto.ECDSA_SECP256R1_CODE_NAME
import net.corda.v5.crypto.SignatureSpec
import net.corda.v5.membership.MGMContext
import net.corda.v5.membership.MemberContext
import net.corda.v5.membership.MemberInfo
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import net.corda.virtualnode.toAvro
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.SoftAssertions
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
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
        const val PUBLISHER_CLIENT_ID = "dynamic-member-registration-service"
        const val GROUP_NAME = "dummy_group"

        val MEMBER_CONTEXT_BYTES = "2222".toByteArray()
        val REQUEST_BYTES = "3333".toByteArray()
        val UNAUTH_REQUEST_BYTES = "4444".toByteArray()
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
    }
    private val memberName = MemberX500Name("Alice", "London", "GB")
    private val member = HoldingIdentity(memberName, GROUP_NAME)
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
    private val keyEncodingService: KeyEncodingService = mock {
        on { decodePublicKey(SESSION_KEY.toByteArray()) } doReturn sessionKey
        on { decodePublicKey(SESSION_KEY) } doReturn sessionKey
        on { decodePublicKey(LEDGER_KEY.toByteArray()) } doReturn ledgerKey
        on { decodePublicKey(NOTARY_KEY.toByteArray()) } doReturn notaryKey

        on { encodeAsString(any()) } doReturn SESSION_KEY
        on { encodeAsString(ledgerKey) } doReturn LEDGER_KEY
        on { encodeAsByteArray(sessionKey) } doReturn SESSION_KEY.toByteArray()
    }
    private val mockSignature: DigitalSignature.WithKey =
        DigitalSignature.WithKey(
            sessionKey,
            byteArrayOf(1),
            mapOf(
                Verifier.SIGNATURE_SPEC to SignatureSpec.ECDSA_SHA512.signatureName
            )
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
                eq(
                    mapOf(
                        Verifier.SIGNATURE_SPEC to SignatureSpec.ECDSA_SHA512.signatureName
                    )
                ),
            )
        }.doReturn(mockSignature)
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
    private var memberContextCaptor: KArgumentCaptor<KeyValuePairList> = argumentCaptor()
    private val keyValuePairListSerializer: CordaAvroSerializer<Any> = mock {
        on { serialize(memberContextCaptor.capture()) } doReturn MEMBER_CONTEXT_BYTES
    }
    private val registrationRequestSerializer: CordaAvroSerializer<Any> = mock {
        on { serialize(any()) } doReturn REQUEST_BYTES
    }
    private val unauthenticatedRegistrationRequestSerializer: CordaAvroSerializer<Any> = mock {
        on { serialize(any()) } doReturn UNAUTH_REQUEST_BYTES
    }
    private val serializationFactory: CordaAvroSerializationFactory = mock {
        on { createAvroSerializer<Any>(any()) }.thenReturn(
            registrationRequestSerializer, keyValuePairListSerializer, unauthenticatedRegistrationRequestSerializer
        )
    }
    private val groupReader: MembershipGroupReader = mock {
        on { lookup() } doReturn listOf(mgmInfo)
    }
    private val membershipGroupReaderProvider: MembershipGroupReaderProvider = mock {
        on { getGroupReader(any()) } doReturn groupReader
    }
    private val membershipPersistenceClient = mock<MembershipPersistenceClient> {
        on {
            persistRegistrationRequest(
                any(),
                any(),
            )
        } doReturn MembershipPersistenceResult.success()
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
        configurationGetService,
    )

    private val context = mapOf(
        "corda.session.key.id" to SESSION_KEY_ID,
        "corda.session.key.signature.spec" to SignatureSpec.ECDSA_SHA512.signatureName,
        "corda.endpoints.0.connectionURL" to "https://localhost:1080",
        "corda.endpoints.0.protocolVersion" to "1",
        "corda.ledger.keys.0.id" to LEDGER_KEY_ID,
        "corda.ledger.keys.0.signature.spec" to SignatureSpec.ECDSA_SHA512.signatureName,
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
                setOf(ConfigKeys.BOOT_CONFIG, ConfigKeys.MESSAGING_CONFIG),
                mapOf(
                    ConfigKeys.BOOT_CONFIG to testConfig,
                    ConfigKeys.MESSAGING_CONFIG to testConfig
                )
            ), coordinator
        )
    }

    @Nested
    inner class SuccessfulRegistrationTests {
        @Test
        fun `registration successfully builds unauthenticated message and publishes it`() {
            postConfigChangedEvent()
            registrationService.start()
            val capturedPublishedList = argumentCaptor<List<Record<String, Any>>>()
            registrationService.register(registrationResultId, member, context)
            verify(mockPublisher, times(1)).publish(capturedPublishedList.capture())
            val publishedMessageList = capturedPublishedList.firstValue
            SoftAssertions.assertSoftly {
                it.assertThat(publishedMessageList.size).isEqualTo(1)
                val publishedMessage = publishedMessageList.first()
                it.assertThat(publishedMessage.topic).isEqualTo(Schemas.P2P.P2P_OUT_TOPIC)
                it.assertThat(publishedMessage.key).isEqualTo(memberId.value)
                val unauthenticatedMessagePublished =
                    (publishedMessage.value as AppMessage).message as UnauthenticatedMessage
                it.assertThat(unauthenticatedMessagePublished.header.source).isEqualTo(member.toAvro())
                it.assertThat(unauthenticatedMessagePublished.header.destination).isEqualTo(mgm.toAvro())
                it.assertThat(unauthenticatedMessagePublished.payload).isEqualTo(ByteBuffer.wrap(UNAUTH_REQUEST_BYTES))
            }
            registrationService.stop()
        }

        @Test
        fun `registration successfully persist the status to new`() {
            postConfigChangedEvent()
            registrationService.start()
            val status = argumentCaptor<RegistrationRequest>()
            whenever(
                membershipPersistenceClient.persistRegistrationRequest(
                    eq(member),
                    status.capture()
                )
            ).doReturn(
                MembershipPersistenceResult.success()
            )

            registrationService.register(registrationResultId, member, context)

            assertThat(status.firstValue.status).isEqualTo(RegistrationStatus.SENT_TO_MGM)
        }

        @Test
        fun `registration context is constructed as expected`() {
            postConfigChangedEvent()
            registrationService.start()
            registrationService.register(registrationResultId, member, context)

            val memberContext = assertDoesNotThrow { memberContextCaptor.firstValue }

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
                PARTY_SESSION_KEY,
                SESSION_KEY_HASH,
                SESSION_KEY_SIGNATURE_SPEC,
                LEDGER_KEYS_KEY.format(0),
                LEDGER_KEY_HASHES_KEY.format(0),
                LEDGER_KEY_SIGNATURE_SPEC.format(0)
            )
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
            whenever(locallyHostedIdentitiesService.getIdentityInfo(member)).doReturn(identityInfo)

            postConfigChangedEvent()
            registrationService.start()
            registrationService.register(registrationResultId, member, context)

            val memberContext = assertDoesNotThrow { memberContextCaptor.firstValue }

            assertThat(memberContext.items).contains(
                KeyValuePair(
                    TLS_CERTIFICATE_SUBJECT, mgm.x500Name.toString()
                )
            )
        }
    }

    @Nested
    inner class FailedRegistrationTests {
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
                "corda.session.key.id",
                "corda.endpoints.0.connectionURL",
                "corda.endpoints.0.protocolVersion",
                "corda.ledger.keys.0.id",
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
            val registrationContext = context + ("corda.session.key.signature.spec" to "Nop")
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
            whenever(locallyHostedIdentitiesService.getIdentityInfo(member)).doReturn(identityInfo)
            postConfigChangedEvent()
            registrationService.start()

            val exception = assertThrows<NotReadyMembershipRegistrationException> {
                registrationService.register(registrationResultId, member, context)
            }

            assertThat(exception).hasMessageContaining("is missing TLS certificates")
        }
    }

    @Nested
    inner class NotaryRoleTests {
        @Test
        fun `registration fails when notary keys are numbered incorrectly`() {
            postConfigChangedEvent()
            val testProperties =
                context + mapOf(
                    "corda.roles.0" to "notary",
                    "corda.notary.service.name" to "O=MyNotaryService, L=London, C=GB",
                    "corda.notary.keys.100.id" to LEDGER_KEY_ID,
                )
            registrationService.start()

            assertThrows<InvalidMembershipRegistrationException> {
                registrationService.register(registrationResultId, member, testProperties)
            }
        }

        @Test
        fun `registration pass when notary keys are numbered correctly`() {
            postConfigChangedEvent()
            val testProperties =
                context + mapOf(
                    "corda.roles.0" to "notary",
                    "corda.notary.service.name" to "O=MyNotaryService, L=London, C=GB",
                    "corda.notary.keys.0.id" to NOTARY_KEY_ID,
                )
            registrationService.start()

            assertDoesNotThrow {
                registrationService.register(registrationResultId, member, testProperties)
            }
        }

        @Test
        fun `registration fails when notary keys has the wrong category`() {
            whenever(notaryCryptoSigningKey.category).doReturn(SESSION_INIT)
            postConfigChangedEvent()
            val testProperties =
                context + mapOf(
                    "corda.roles.0" to "notary",
                    "corda.notary.service.name" to "O=MyNotaryService, L=London, C=GB",
                    "corda.notary.keys.0.id" to NOTARY_KEY_ID,
                )
            registrationService.start()

            assertThrows<InvalidMembershipRegistrationException> {
                registrationService.register(registrationResultId, member, testProperties)
            }
        }

        @Test
        fun `registration adds notary information when notary role is set`() {
            val memberContext = argumentCaptor<KeyValuePairList>()
            whenever(keyValuePairListSerializer.serialize(memberContext.capture())).doReturn(MEMBER_CONTEXT_BYTES)
            postConfigChangedEvent()
            val testProperties =
                context + mapOf(
                    "corda.roles.0" to "notary",
                    "corda.notary.service.name" to "O=MyNotaryService, L=London, C=GB",
                    "corda.notary.keys.0.id" to NOTARY_KEY_ID,
                )
            registrationService.start()

            registrationService.register(registrationResultId, member, testProperties)

            assertThat(memberContext.firstValue.toMap())
                .containsEntry("corda.roles.0", "notary")
                .containsKey("corda.notary.service.name")
                .containsEntry("corda.notary.keys.0.id", NOTARY_KEY_ID)
                .containsEntry("corda.notary.keys.0.pem", "1234")
                .containsKey("corda.notary.keys.0.hash")
                .containsEntry("corda.notary.keys.0.signature.spec", SignatureSpec.ECDSA_SHA256.signatureName)
        }

        @Test
        fun `registration adds session spec if needed`() {
            val memberContext = argumentCaptor<KeyValuePairList>()
            whenever(keyValuePairListSerializer.serialize(memberContext.capture())).doReturn(MEMBER_CONTEXT_BYTES)
            val registrationContext = context - "corda.session.key.signature.spec"
            postConfigChangedEvent()
            registrationService.start()

            assertThrows<NotReadyMembershipRegistrationException> {
                registrationService.register(registrationResultId, member, registrationContext)
            }

            assertThat(memberContext.firstValue.toMap())
                .containsEntry("corda.session.key.signature.spec", SignatureSpec.ECDSA_SHA256.signatureName)
        }

        @Test
        fun `registration adds ledger spec if needed`() {
            val memberContext = argumentCaptor<KeyValuePairList>()
            whenever(keyValuePairListSerializer.serialize(memberContext.capture())).doReturn(MEMBER_CONTEXT_BYTES)
            val registrationContext = context - "corda.ledger.keys.0.signature.spec"
            postConfigChangedEvent()
            registrationService.start()

            registrationService.register(registrationResultId, member, registrationContext)

            assertThat(memberContext.firstValue.toMap())
                .containsEntry("corda.ledger.keys.0.signature.spec", SignatureSpec.ECDSA_SHA256.signatureName)
        }

        @Test
        fun `registration fails when notary service is invalid`() {
            postConfigChangedEvent()
            val testProperties =
                context + mapOf(
                    "corda.roles" to "notary",
                    "corda.notary.service.name" to "Hello world",
                    "corda.notary.keys.0.id" to NOTARY_KEY_ID,
                )
            registrationService.start()

            assertThrows<InvalidMembershipRegistrationException> {
                registrationService.register(registrationResultId, member, testProperties)
            }
        }

        @Test
        fun `registration pass when notary service is valid`() {
            postConfigChangedEvent()
            val testProperties =
                context + mapOf(
                    "corda.roles.0" to "notary",
                    "corda.notary.service.name" to "O=MyNotaryService, L=London, C=GB",
                    "corda.notary.keys.0.id" to NOTARY_KEY_ID,
                )
            registrationService.start()

            assertDoesNotThrow {
                registrationService.register(registrationResultId, member, testProperties)
            }
        }

        @Test
        fun `registration fails when notary keys are not provided`() {
            postConfigChangedEvent()
            val testProperties =
                context + mapOf(
                    "corda.roles.0" to "notary",
                    "corda.notary.service.name" to "O=MyNotaryService, L=London, C=GB",
                )
            registrationService.start()

            val exception = assertThrows<InvalidMembershipRegistrationException> {
                registrationService.register(registrationResultId, member, testProperties)
            }

            assertThat(exception).hasMessageContaining("notary key")
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
