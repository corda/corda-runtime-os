package net.corda.membership.impl.registration.dynamic.mgm

import net.corda.avro.serialization.CordaAvroSerializationFactory
import net.corda.avro.serialization.CordaAvroSerializer
import net.corda.configuration.read.ConfigurationGetService
import net.corda.configuration.read.ConfigurationReadService
import net.corda.crypto.cipher.suite.KeyEncodingService
import net.corda.crypto.client.CryptoOpsClient
import net.corda.crypto.core.CryptoConsts.Categories.PRE_AUTH
import net.corda.crypto.core.CryptoConsts.Categories.SESSION_INIT
import net.corda.crypto.core.ShortHash
import net.corda.crypto.core.fullIdHash
import net.corda.crypto.impl.converter.PublicKeyConverter
import net.corda.crypto.impl.converter.PublicKeyHashConverter
import net.corda.data.KeyValuePair
import net.corda.data.KeyValuePairList
import net.corda.data.crypto.wire.CryptoSignatureSpec
import net.corda.data.crypto.wire.CryptoSignatureWithKey
import net.corda.data.crypto.wire.CryptoSigningKey
import net.corda.data.membership.PersistentMemberInfo
import net.corda.data.membership.common.v2.RegistrationStatus
import net.corda.data.membership.event.MembershipEvent
import net.corda.data.membership.event.registration.MgmOnboarded
import net.corda.layeredpropertymap.testkit.LayeredPropertyMapMocks
import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleEventHandler
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.RegistrationHandle
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import net.corda.membership.groupparams.writer.service.GroupParametersWriterService
import net.corda.membership.impl.registration.CPI_HASH
import net.corda.membership.impl.registration.TEST_CPI_NAME
import net.corda.membership.impl.registration.TEST_CPI_VERSION
import net.corda.membership.impl.registration.TEST_PLATFORM_VERSION
import net.corda.membership.impl.registration.TEST_SOFTWARE_VERSION
import net.corda.membership.impl.registration.buildMockPlatformInfoProvider
import net.corda.membership.impl.registration.buildTestVirtualNodeInfo
import net.corda.membership.lib.MemberInfoExtension
import net.corda.membership.lib.MemberInfoExtension.Companion.ECDH_KEY
import net.corda.membership.lib.MemberInfoExtension.Companion.IS_MGM
import net.corda.membership.lib.MemberInfoExtension.Companion.MEMBER_CPI_NAME
import net.corda.membership.lib.MemberInfoExtension.Companion.MEMBER_CPI_SIGNER_HASH
import net.corda.membership.lib.MemberInfoExtension.Companion.MEMBER_CPI_VERSION
import net.corda.membership.lib.MemberInfoExtension.Companion.MEMBER_STATUS_ACTIVE
import net.corda.membership.lib.MemberInfoExtension.Companion.PARTY_NAME
import net.corda.membership.lib.MemberInfoExtension.Companion.PARTY_SESSION_KEYS_PEM
import net.corda.membership.lib.MemberInfoExtension.Companion.PLATFORM_VERSION
import net.corda.membership.lib.MemberInfoExtension.Companion.PROTOCOL_VERSION
import net.corda.membership.lib.MemberInfoExtension.Companion.SERIAL
import net.corda.membership.lib.MemberInfoExtension.Companion.SESSION_KEYS
import net.corda.membership.lib.MemberInfoExtension.Companion.SESSION_KEYS_HASH
import net.corda.membership.lib.MemberInfoExtension.Companion.SOFTWARE_VERSION
import net.corda.membership.lib.MemberInfoExtension.Companion.STATUS
import net.corda.membership.lib.MemberInfoExtension.Companion.URL_KEY
import net.corda.membership.lib.MemberInfoExtension.Companion.groupId
import net.corda.membership.lib.MemberInfoFactory
import net.corda.membership.lib.SelfSignedMemberInfo
import net.corda.membership.lib.SignedGroupParameters
import net.corda.membership.lib.impl.converter.EndpointInfoConverter
import net.corda.membership.lib.impl.converter.MemberNotaryDetailsConverter
import net.corda.membership.lib.registration.RegistrationRequest
import net.corda.membership.lib.schema.validation.MembershipSchemaValidationException
import net.corda.membership.lib.schema.validation.MembershipSchemaValidator
import net.corda.membership.lib.schema.validation.MembershipSchemaValidatorFactory
import net.corda.membership.persistence.client.MembershipPersistenceClient
import net.corda.membership.persistence.client.MembershipPersistenceOperation
import net.corda.membership.persistence.client.MembershipPersistenceResult
import net.corda.membership.persistence.client.MembershipQueryClient
import net.corda.membership.persistence.client.MembershipQueryResult
import net.corda.membership.registration.ExpirationProcessor
import net.corda.membership.registration.InvalidMembershipRegistrationException
import net.corda.membership.registration.NotReadyMembershipRegistrationException
import net.corda.messaging.api.records.Record
import net.corda.schema.Schemas.Membership.EVENT_TOPIC
import net.corda.schema.Schemas.Membership.MEMBER_LIST_TOPIC
import net.corda.schema.configuration.ConfigKeys
import net.corda.schema.membership.MembershipSchema
import net.corda.utilities.time.UTCClock
import net.corda.v5.base.types.LayeredPropertyMap
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.crypto.KeySchemeCodes
import net.corda.v5.membership.MemberContext
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import net.corda.virtualnode.toAvro
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.SoftAssertions.assertSoftly
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito
import org.mockito.kotlin.KArgumentCaptor
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argThat
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doNothing
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.nio.ByteBuffer
import java.security.PublicKey
import java.util.Calendar
import java.util.GregorianCalendar
import java.util.UUID

class MGMRegistrationServiceTest {
    private companion object {
        const val SESSION_KEY_STRING = "1234"
        const val SESSION_KEY_ID = "ABC123456789"
        const val ECDH_KEY_STRING = "5678"
        const val ECDH_KEY_ID = "BBC123456789"
        const val GROUP_ID = "43b5b6e6-4f2d-498f-8b41-5e2f8f97e7e8"

        private val trustrootCert = this::class.java.getResource("/r3Com.pem")!!.readText()
    }

    private val registrationRequestId = UUID(1L, 2L)
    private val reRegistrationRequestId = UUID(2L, 3L)
    private val mgmName = MemberX500Name("Corda MGM", "London", "GB")
    private val mgm = HoldingIdentity(mgmName, GROUP_ID)
    private val mgmId = mgm.shortHash
    private val sessionKey: PublicKey = mock {
        on { encoded } doReturn SESSION_KEY_STRING.toByteArray()
    }
    private val sessionCryptoSigningKey: CryptoSigningKey = mock {
        on { publicKey } doReturn ByteBuffer.wrap(SESSION_KEY_STRING.toByteArray())
        on { category } doReturn SESSION_INIT
        on { schemeCodeName } doReturn KeySchemeCodes.RSA_CODE_NAME
    }
    private val ecdhKey: PublicKey = mock {
        on { encoded } doReturn ECDH_KEY_STRING.toByteArray()
        on { algorithm } doReturn "EC"
    }
    private val ecdhCryptoSigningKey: CryptoSigningKey = mock {
        on { publicKey } doReturn ByteBuffer.wrap(ECDH_KEY_STRING.toByteArray())
        on { category } doReturn PRE_AUTH
    }
    private val keyEncodingService: KeyEncodingService = mock {
        on { decodePublicKey(SESSION_KEY_STRING.toByteArray()) } doReturn sessionKey
        on { decodePublicKey(ECDH_KEY_STRING.toByteArray()) } doReturn ecdhKey

        on { encodeAsString(any()) } doReturn SESSION_KEY_STRING
        on { encodeAsString(ecdhKey) } doReturn ECDH_KEY_STRING
    }
    private val cryptoOpsClient: CryptoOpsClient = mock {
        on { lookupKeysByIds(mgmId.value, listOf(ShortHash.of(SESSION_KEY_ID))) } doReturn listOf(
            sessionCryptoSigningKey
        )
        on { lookupKeysByIds(mgmId.value, listOf(ShortHash.of(ECDH_KEY_ID))) } doReturn listOf(ecdhCryptoSigningKey)
    }
    private val gatewayConfiguration = mock<SmartConfig> {
        on { getConfig("sslConfig") } doReturn mock
        on { getString("tlsType") } doReturn "ONE_WAY"
    }
    private val configurationGetService = mock<ConfigurationGetService> {
        on { getSmartConfig(ConfigKeys.P2P_GATEWAY_CONFIG) } doReturn gatewayConfiguration
    }

    private val componentHandle: RegistrationHandle = mock()
    private val dependentComponents = setOf(
        LifecycleCoordinatorName.forComponent<ConfigurationReadService>(),
        LifecycleCoordinatorName.forComponent<CryptoOpsClient>(),
        LifecycleCoordinatorName.forComponent<ExpirationProcessor>(),
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
    private val layeredPropertyMapFactory = LayeredPropertyMapMocks.createFactory(
        listOf(
            EndpointInfoConverter(),
            MemberNotaryDetailsConverter(keyEncodingService),
            PublicKeyConverter(keyEncodingService),
            PublicKeyHashConverter()
        )
    )
    private val mockSignedGroupParameters: SignedGroupParameters = mock()
    private val statusUpdate = argumentCaptor<RegistrationRequest>()
    private val persistRegistrationRequestOperation = mock<MembershipPersistenceOperation<Unit>> {
        on { execute() } doReturn MembershipPersistenceResult.success()
    }
    private class Operation<T>(
        private val value: MembershipPersistenceResult<T>
    ) : MembershipPersistenceOperation<T> {
        override fun execute() = value

        override fun createAsyncCommands(): Collection<Record<*, *>> {
            return emptyList()
        }
    }
    private val membershipPersistenceClient = mock<MembershipPersistenceClient> {
        on { persistMemberInfo(any(), any()) } doReturn Operation(MembershipPersistenceResult.success())
        on { persistGroupPolicy(any(), any(), any()) } doReturn Operation(MembershipPersistenceResult.success())
        on {
            persistRegistrationRequest(
                eq(mgm),
                statusUpdate.capture(),
                any(),
            )
        } doReturn persistRegistrationRequestOperation
        on { persistGroupParametersInitialSnapshot(any()) } doReturn Operation(
            MembershipPersistenceResult.Success(
                mockSignedGroupParameters
            )
        )
    }
    private val serializedMemberContext = byteArrayOf(1)
    private val serializedMgmContext = byteArrayOf(2)
    private val serializedMgmContextAfterReRegistration = byteArrayOf(3)
    private val keyValuePairListSerializer: CordaAvroSerializer<KeyValuePairList> = mock {
        on { serialize(any()) } doReturn byteArrayOf(1, 2, 3)
        on {
            serialize(
                argThat {
                    items.containsAll(
                        listOf(
                            KeyValuePair(MemberInfoExtension.GROUP_ID, GROUP_ID),
                            KeyValuePair(PARTY_NAME, mgmName.toString()),
                            KeyValuePair(PARTY_SESSION_KEYS_PEM.format(0), SESSION_KEY_STRING),
                            KeyValuePair(SESSION_KEYS_HASH.format(0), sessionKey.fullIdHash().toString()),
                            KeyValuePair(ECDH_KEY, ECDH_KEY_STRING),
                            KeyValuePair(PLATFORM_VERSION, TEST_PLATFORM_VERSION.toString()),
                            KeyValuePair(SOFTWARE_VERSION, TEST_SOFTWARE_VERSION),
                            KeyValuePair(MEMBER_CPI_NAME, TEST_CPI_NAME),
                            KeyValuePair(MEMBER_CPI_SIGNER_HASH, CPI_HASH),
                            KeyValuePair(MEMBER_CPI_VERSION, TEST_CPI_VERSION),
                            KeyValuePair(URL_KEY.format(0), "https://localhost:1080"),
                            KeyValuePair(PROTOCOL_VERSION.format(0), "1"),
                        )
                    )
                }
            )
        } doReturn serializedMemberContext
        on {
            serialize(
                argThat {
                    items.containsAll(
                        listOf(
                            KeyValuePair(STATUS, MEMBER_STATUS_ACTIVE),
                            KeyValuePair(IS_MGM, "true"),
                            KeyValuePair(SERIAL, "1"),
                        )
                    )
                }
            )
        } doReturn serializedMgmContext
        on {
            serialize(
                argThat {
                    items.containsAll(
                        listOf(
                            KeyValuePair(STATUS, MEMBER_STATUS_ACTIVE),
                            KeyValuePair(IS_MGM, "true"),
                            KeyValuePair(SERIAL, "2"),
                        )
                    )
                }
            )
        } doReturn serializedMgmContextAfterReRegistration
    }
    private val cordaAvroSerializationFactory = mock<CordaAvroSerializationFactory> {
        on { createAvroSerializer<KeyValuePairList>(any()) } doReturn keyValuePairListSerializer
    }

    private val signedMemberInfo = mock<SelfSignedMemberInfo> {
        on { memberContextBytes } doReturn serializedMemberContext
        on { memberProvidedContext } doReturn mock()
        on { mgmProvidedContext } doReturn mock()
        on { name } doReturn mgmName
        on { groupId } doReturn GROUP_ID
        on { serial } doReturn 1
    }
    private val signedMemberInfoAfterReRegistration = mock<SelfSignedMemberInfo> {
        on { memberContextBytes } doReturn serializedMemberContext
        on { memberProvidedContext } doReturn mock()
        on { mgmProvidedContext } doReturn mock()
        on { name } doReturn mgmName
        on { groupId } doReturn GROUP_ID
        on { serial } doReturn 2
    }
    private val persistentMemberInfo = mock<PersistentMemberInfo>()
    private val persistentMemberInfoAfterReRegistration = mock<PersistentMemberInfo>()
    private val signature = CryptoSignatureWithKey(
        ByteBuffer.wrap(byteArrayOf()),
        ByteBuffer.wrap(byteArrayOf())
    )
    private val signatureSpec = CryptoSignatureSpec("", null, null)
    private val memberInfoFactory: MemberInfoFactory = mock {
        on {
            createSelfSignedMemberInfo(
                eq(serializedMemberContext),
                eq(serializedMgmContext),
                eq(signature),
                eq(signatureSpec),
            )
        } doReturn signedMemberInfo
        on {
            createSelfSignedMemberInfo(
                eq(serializedMemberContext),
                eq(serializedMgmContextAfterReRegistration),
                eq(signature),
                eq(signatureSpec),
            )
        } doReturn signedMemberInfoAfterReRegistration
        on {
            createPersistentMemberInfo(
                eq(mgm.toAvro()),
                eq(signedMemberInfo),
            )
        } doReturn persistentMemberInfo
        on {
            createPersistentMemberInfo(
                eq(mgm.toAvro()),
                eq(signedMemberInfoAfterReRegistration),
            )
        } doReturn persistentMemberInfoAfterReRegistration
    }
    private val membershipSchemaValidator: MembershipSchemaValidator = mock()
    private val membershipSchemaValidatorFactory: MembershipSchemaValidatorFactory = mock {
        on { createValidator() } doReturn membershipSchemaValidator
    }
    private val platformInfoProvider = buildMockPlatformInfoProvider()
    private val virtualNodeInfo = buildTestVirtualNodeInfo(mgm)
    private val virtualNodeInfoReadService: VirtualNodeInfoReadService = mock {
        on { get(eq(mgm)) } doReturn virtualNodeInfo
    }
    private val writerService: GroupParametersWriterService = mock()
    private val expirationProcessor: ExpirationProcessor = mock()
    private val membershipQueryClient = mock<MembershipQueryClient> {
        on {
            queryMemberInfo(eq(mgm), eq(setOf(mgm)), eq(listOf(MEMBER_STATUS_ACTIVE)))
        } doReturn MembershipQueryResult.Success(listOf(signedMemberInfo))
        on { queryRegistrationRequests(any(), anyOrNull(), any(), isNull()) } doReturn MembershipQueryResult.Success(emptyList())
    }

    private val registrationService = MGMRegistrationService(
        lifecycleCoordinatorFactory,
        cryptoOpsClient,
        keyEncodingService,
        memberInfoFactory,
        membershipPersistenceClient,
        membershipQueryClient,
        layeredPropertyMapFactory,
        cordaAvroSerializationFactory,
        membershipSchemaValidatorFactory,
        platformInfoProvider,
        virtualNodeInfoReadService,
        writerService,
        configurationGetService,
        expirationProcessor,
    )

    private val properties = mapOf(
        "corda.session.keys.0.id" to SESSION_KEY_ID,
        "corda.ecdh.key.id" to ECDH_KEY_ID,
        "corda.group.protocol.registration"
            to "net.corda.membership.impl.registration.dynamic.member.DynamicMemberRegistrationService",
        "corda.group.protocol.synchronisation"
            to "net.corda.membership.impl.synchronisation.MemberSynchronisationServiceImpl",
        "corda.group.protocol.p2p.mode" to "AUTHENTICATION_ENCRYPTION",
        "corda.group.key.session.policy" to "Combined",
        "corda.group.tls.type" to "OneWay",
        "corda.group.pki.session" to "Standard",
        "corda.group.pki.tls" to "C5",
        "corda.endpoints.0.connectionURL" to "https://localhost:1080",
        "corda.endpoints.0.protocolVersion" to "1",
        "corda.group.trustroot.session.0" to trustrootCert,
        "corda.group.trustroot.tls.0" to trustrootCert,
    )

    private val validCertificateDate = GregorianCalendar(2022, Calendar.JULY, 22)
    private val mockClock = Mockito.mockConstruction(UTCClock::class.java) {
            mock, _ ->
        whenever(mock.instant()).thenReturn(validCertificateDate.toInstant())
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

    private fun postUpEvent() {
        postRegistrationStatusChangeEvent(LifecycleStatus.UP)
    }

    @AfterEach
    fun cleanUp() {
        mockClock.close()
    }

    @Nested
    inner class SuccessfulRegistrationTests {
        @Test
        fun `registration successfully builds MGM info and publishes it`() {
            postUpEvent()
            registrationService.start()

            val publishedList = registrationService.register(registrationRequestId, mgm, properties)
            val publishedMgmInfo = publishedList.first()
            val publishedEvent = publishedList.last()
            assertSoftly {
                it.assertThat(publishedList).hasSize(2)

                it.assertThat(publishedMgmInfo.topic).isEqualTo(MEMBER_LIST_TOPIC)
                it.assertThat(publishedEvent.topic).isEqualTo(EVENT_TOPIC)

                val expectedRecordKey = "$mgmId-$mgmId"
                it.assertThat(publishedMgmInfo.key).isEqualTo(expectedRecordKey)
                it.assertThat(publishedEvent.key).isEqualTo(mgmId.value)
                it.assertThat(publishedMgmInfo.value).isInstanceOf(PersistentMemberInfo::class.java)
                it.assertThat(publishedMgmInfo.value as PersistentMemberInfo).isEqualTo(persistentMemberInfo)
                it.assertThat(statusUpdate.firstValue.status).isEqualTo(RegistrationStatus.APPROVED)
                it.assertThat(statusUpdate.firstValue.registrationId).isEqualTo(registrationRequestId.toString())
                it.assertThat(statusUpdate.firstValue.serial).isEqualTo(0L)

                val membershipEvent = publishedEvent.value as MembershipEvent
                it.assertThat(membershipEvent.event).isInstanceOf(MgmOnboarded::class.java)
                val mgmOnboardedEvent = membershipEvent.event as MgmOnboarded
                it.assertThat(mgmOnboardedEvent.onboardedMgm).isEqualTo(mgm.toAvro())
            }
            verify(expirationProcessor).scheduleProcessingOfExpiredRequests(mgm)
            registrationService.stop()
        }

        @Test
        fun `re-registration successfully builds MGM info and publishes it`() {
            postUpEvent()
            registrationService.start()

            val groupPolicy = argumentCaptor<LayeredPropertyMap>()
            whenever(
                membershipPersistenceClient
                    .persistGroupPolicy(
                        eq(mgm),
                        groupPolicy.capture(),
                        eq(1)
                    )
            ).thenReturn(Operation(MembershipPersistenceResult.success()))

            // first registration
            registrationService.register(registrationRequestId, mgm, properties)
            verify(expirationProcessor).scheduleProcessingOfExpiredRequests(mgm)
            whenever(
                membershipQueryClient.queryRegistrationRequests(
                    eq(mgm),
                    eq(mgm.x500Name),
                    eq(listOf(RegistrationStatus.APPROVED)),
                    isNull()
                )
            ).thenReturn(MembershipQueryResult.Success(listOf(mock())))
            whenever(membershipQueryClient.queryGroupPolicy(mgm))
                .thenReturn(MembershipQueryResult.Success(Pair(groupPolicy.firstValue, 1)))

            // second registration
            val publishedList = registrationService.register(reRegistrationRequestId, mgm, properties)
            val publishedMgmInfo = publishedList.first()
            val publishedEvent = publishedList.last()
            assertSoftly {
                it.assertThat(publishedList).hasSize(2)

                it.assertThat(publishedMgmInfo.topic).isEqualTo(MEMBER_LIST_TOPIC)
                it.assertThat(publishedEvent.topic).isEqualTo(EVENT_TOPIC)

                val expectedRecordKey = "$mgmId-$mgmId"
                it.assertThat(publishedMgmInfo.key).isEqualTo(expectedRecordKey)
                it.assertThat(publishedEvent.key).isEqualTo(mgmId.value)
                it.assertThat(publishedMgmInfo.value).isInstanceOf(PersistentMemberInfo::class.java)
                it.assertThat(publishedMgmInfo.value as PersistentMemberInfo)
                    .isEqualTo(persistentMemberInfoAfterReRegistration)
                it.assertThat(statusUpdate.lastValue.status).isEqualTo(RegistrationStatus.APPROVED)
                it.assertThat(statusUpdate.lastValue.registrationId).isEqualTo(reRegistrationRequestId.toString())
                it.assertThat(statusUpdate.lastValue.serial).isEqualTo(1L)

                val membershipEvent = publishedEvent.value as MembershipEvent
                it.assertThat(membershipEvent.event).isInstanceOf(MgmOnboarded::class.java)
                val mgmOnboardedEvent = membershipEvent.event as MgmOnboarded
                it.assertThat(mgmOnboardedEvent.onboardedMgm).isEqualTo(mgm.toAvro())
            }
            verify(expirationProcessor, times(2)).scheduleProcessingOfExpiredRequests(mgm)
            registrationService.stop()
        }

        @Test
        fun `registration persist the group properties`() {
            postUpEvent()
            registrationService.start()
            val groupProperties = argumentCaptor<LayeredPropertyMap>()
            whenever(
                membershipPersistenceClient
                    .persistGroupPolicy(
                        eq(mgm),
                        groupProperties.capture(),
                        eq(1)
                    )
            ).thenReturn(Operation(MembershipPersistenceResult.success()))

            registrationService.register(registrationRequestId, mgm, properties)

            assertThat(groupProperties.firstValue.entries)
                .containsExactlyInAnyOrderElementsOf(
                    mapOf(
                        "protocol.registration"
                            to "net.corda.membership.impl.registration.dynamic.member.DynamicMemberRegistrationService",
                        "protocol.synchronisation"
                            to "net.corda.membership.impl.synchronisation.MemberSynchronisationServiceImpl",
                        "protocol.p2p.mode" to "AUTHENTICATION_ENCRYPTION",
                        "tls.type" to "OneWay",
                        "key.session.policy" to "Combined",
                        "pki.session" to "Standard",
                        "pki.tls" to "C5",
                        "trustroot.session.0" to trustrootCert,
                        "trustroot.tls.0" to trustrootCert,
                    ).entries
                )
            registrationService.stop()
        }

        @Test
        fun `registration persist the MGM member info`() {
            postUpEvent()
            registrationService.start()

            registrationService.register(registrationRequestId, mgm, properties)

            verify(membershipPersistenceClient).persistMemberInfo(
                eq(mgm),
                eq(listOf(signedMemberInfo)),
            )
        }

        @Test
        fun `registration persists initial group parameters snapshot`() {
            postUpEvent()
            registrationService.start()

            registrationService.register(registrationRequestId, mgm, properties)

            verify(membershipPersistenceClient).persistGroupParametersInitialSnapshot(eq(mgm))
        }

        @Test
        fun `registration publishes initial group parameters snapshot to Kafka`() {
            val groupParametersCaptor = argumentCaptor<SignedGroupParameters>()
            postUpEvent()
            registrationService.start()

            registrationService.register(registrationRequestId, mgm, properties)

            verify(writerService).put(eq(mgm), groupParametersCaptor.capture())
            assertThat(groupParametersCaptor.firstValue).isEqualTo(mockSignedGroupParameters)
        }

        @Test
        fun `if session PKI mode is NoPKI, session trust root is optional`() {
            postUpEvent()
            val testProperties = properties.toMutableMap()
            testProperties["corda.group.pki.session"] = "NoPKI"
            testProperties.remove("corda.group.trustroot.session.0")
            registrationService.start()

            assertDoesNotThrow {
                registrationService.register(registrationRequestId, mgm, testProperties)
            }

            registrationService.stop()
        }
    }

    @Nested
    inner class FailedRegistrationTests {
        @Test
        fun `registration failure to persist return an error`() {
            postUpEvent()
            registrationService.start()
            whenever(membershipPersistenceClient.persistMemberInfo(eq(mgm), any()))
                .doReturn(Operation(MembershipPersistenceResult.Failure("Nop")))

            val exception = assertThrows<InvalidMembershipRegistrationException> {
                registrationService.register(registrationRequestId, mgm, properties)
            }

            verify(expirationProcessor, never()).scheduleProcessingOfExpiredRequests(mgm)

            assertThat(exception).hasMessageContaining("Registration failed, persistence error. Reason: Nop")
        }

        @Test
        fun `registration fails when coordinator is not running`() {
            val exception = assertThrows<NotReadyMembershipRegistrationException> {
                registrationService.register(registrationRequestId, mgm, mock())
            }

            assertThat(exception).hasMessageContaining(
                "Registration failed. Reason: MGMRegistrationService is not running."
            )
        }

        @Test
        fun `registration fails when one or more properties are missing`() {
            postUpEvent()
            val testProperties = mutableMapOf<String, String>()
            registrationService.start()
            properties.entries.apply {
                for (index in indices) {
                    assertThrows<InvalidMembershipRegistrationException> {
                        registrationService.register(registrationRequestId, mgm, testProperties)
                    }
                    elementAt(index).let { testProperties.put(it.key, it.value) }
                }
            }
            registrationService.stop()
        }

        @Test
        fun `registration fails when one or more properties are numbered incorrectly`() {
            postUpEvent()
            val testProperties =
                properties + mapOf(
                    "corda.group.trustroot.tls.100" to trustrootCert
                )
            registrationService.start()

            val exception = assertThrows<InvalidMembershipRegistrationException> {
                registrationService.register(registrationRequestId, mgm, testProperties)
            }

            assertThat(exception).hasMessage(
                "Onboarding MGM failed. " +
                    "Provided TLS trust stores are incorrectly numbered."
            )
            registrationService.stop()
        }

        @Test
        fun `registration fails if the registration context doesn't match the schema`() {
            postUpEvent()
            val err = "ERROR-MESSAGE"
            val errReason = "ERROR-REASON"
            whenever(
                membershipSchemaValidator.validateRegistrationContext(
                    eq(MembershipSchema.RegistrationContextSchema.Mgm),
                    any(),
                    any()
                )
            ).doThrow(
                MembershipSchemaValidationException(
                    err,
                    null,
                    MembershipSchema.RegistrationContextSchema.Mgm,
                    listOf(errReason)
                )
            )

            registrationService.start()

            val exception = assertThrows<InvalidMembershipRegistrationException> {
                registrationService.register(registrationRequestId, mgm, properties)
            }

            assertThat(exception)
                .hasMessageContaining(err)
                .hasMessageContaining(errReason)
            registrationService.stop()
        }

        @Test
        fun `registration fails when vnode info cannot be found`() {
            postUpEvent()
            registrationService.start()
            whenever(virtualNodeInfoReadService.get(eq(mgm))).doReturn(null)

            val exception = assertThrows<InvalidMembershipRegistrationException> {
                registrationService.register(registrationRequestId, mgm, properties)
            }

            assertThat(exception).hasMessageContaining("Could not find virtual node info")
        }

        @Test
        fun `re-registration fails when previous request cannot be retrieved`() {
            postUpEvent()
            registrationService.start()

            // first registration
            registrationService.register(registrationRequestId, mgm, properties)
            verify(expirationProcessor).scheduleProcessingOfExpiredRequests(mgm)

            val errorMsg = "Persistence failure"
            whenever(
                membershipQueryClient.queryRegistrationRequests(
                    eq(mgm),
                    eq(mgm.x500Name),
                    eq(listOf(RegistrationStatus.APPROVED)),
                    isNull()
                )
            ).thenReturn(MembershipQueryResult.Failure(errorMsg))

            // second registration
            val message = assertThrows<NotReadyMembershipRegistrationException> {
                registrationService.register(reRegistrationRequestId, mgm, properties)
            }.message
            assertThat(message).contains(errorMsg)
            registrationService.stop()
        }

        @Test
        fun `re-registration fails when previous member info cannot be retrieved`() {
            postUpEvent()
            registrationService.start()

            // first registration
            registrationService.register(registrationRequestId, mgm, properties)
            verify(expirationProcessor).scheduleProcessingOfExpiredRequests(mgm)

            val errorMsg = "Persistence failure"
            whenever(
                membershipQueryClient.queryRegistrationRequests(
                    eq(mgm),
                    eq(mgm.x500Name),
                    eq(listOf(RegistrationStatus.APPROVED)),
                    isNull()
                )
            ).thenReturn(MembershipQueryResult.Success(listOf(mock())))
            whenever(
                membershipQueryClient.queryMemberInfo(eq(mgm), eq(setOf(mgm)), eq(listOf(MEMBER_STATUS_ACTIVE)))
            ).thenReturn(MembershipQueryResult.Failure(errorMsg))

            // second registration
            val message = assertThrows<NotReadyMembershipRegistrationException> {
                registrationService.register(reRegistrationRequestId, mgm, properties)
            }.message
            assertThat(message).contains(errorMsg)
            registrationService.stop()
        }

        @Test
        fun `re-registration fails when non-endpoint changes were made`() {
            postUpEvent()
            registrationService.start()

            // first registration
            registrationService.register(registrationRequestId, mgm, properties)
            verify(expirationProcessor).scheduleProcessingOfExpiredRequests(mgm)

            whenever(
                membershipQueryClient.queryRegistrationRequests(
                    eq(mgm),
                    eq(mgm.x500Name),
                    eq(listOf(RegistrationStatus.APPROVED)),
                    isNull()
                )
            ).thenReturn(MembershipQueryResult.Success(listOf(mock())))
            val oldMemberContext = mock<MemberContext> {
                on { entries } doReturn mapOf(PARTY_SESSION_KEYS_PEM.format(0) to SESSION_KEY_ID).entries
            }
            val signedMemberInfo = mock<SelfSignedMemberInfo> {
                on { memberContextBytes } doReturn serializedMemberContext
                on { memberProvidedContext } doReturn oldMemberContext
                on { mgmProvidedContext } doReturn mock()
                on { name } doReturn mgmName
                on { groupId } doReturn GROUP_ID
                on { serial } doReturn 1
            }
            whenever(
                membershipQueryClient.queryMemberInfo(eq(mgm), eq(setOf(mgm)), eq(listOf(MEMBER_STATUS_ACTIVE)))
            ).thenReturn(MembershipQueryResult.Success(listOf(signedMemberInfo)))
            val invalidKey = "12328832"
            val serializedMemberContext = byteArrayOf(6)
            whenever(
                keyValuePairListSerializer.serialize(
                    argThat {
                        items.containsAll(
                            listOf(
                                KeyValuePair(MemberInfoExtension.GROUP_ID, GROUP_ID),
                                KeyValuePair(PARTY_NAME, mgmName.toString()),
                                // modified session key
                                KeyValuePair(PARTY_SESSION_KEYS_PEM.format(0), invalidKey),
                                KeyValuePair(SESSION_KEYS_HASH.format(0), sessionKey.fullIdHash().toString()),
                                KeyValuePair(ECDH_KEY, ECDH_KEY_STRING),
                                KeyValuePair(PLATFORM_VERSION, TEST_PLATFORM_VERSION.toString()),
                                KeyValuePair(SOFTWARE_VERSION, TEST_SOFTWARE_VERSION),
                                KeyValuePair(MEMBER_CPI_NAME, TEST_CPI_NAME),
                                KeyValuePair(MEMBER_CPI_SIGNER_HASH, CPI_HASH),
                                KeyValuePair(MEMBER_CPI_VERSION, TEST_CPI_VERSION),
                                KeyValuePair(URL_KEY.format(0), "https://localhost:1080"),
                                KeyValuePair(PROTOCOL_VERSION.format(0), "1"),
                            )
                        )
                    }
                )
            ).thenReturn(serializedMemberContext)
            val newMemberContext = mock<MemberContext> {
                on { entries } doReturn mapOf(PARTY_SESSION_KEYS_PEM.format(0) to invalidKey).entries
            }
            val signedMemberInfoAfterReRegistration = mock<SelfSignedMemberInfo> {
                on { memberContextBytes } doReturn serializedMemberContext
                on { memberProvidedContext } doReturn newMemberContext
                on { mgmProvidedContext } doReturn mock()
                on { name } doReturn mgmName
                on { groupId } doReturn GROUP_ID
                on { serial } doReturn 1
            }
            whenever(
                memberInfoFactory.createSelfSignedMemberInfo(
                    eq(serializedMemberContext),
                    eq(serializedMgmContextAfterReRegistration),
                    eq(signature),
                    eq(signatureSpec),
                )
            ).thenReturn(signedMemberInfoAfterReRegistration)

            // second registration
            val message = assertThrows<InvalidMembershipRegistrationException> {
                registrationService.register(
                    reRegistrationRequestId,
                    mgm,
                    properties.filterNot { it.key.startsWith(PARTY_SESSION_KEYS_PEM) } +
                        mapOf(PARTY_SESSION_KEYS_PEM.format(0) to invalidKey)
                )
            }.message
            assertThat(message)
                .contains(SESSION_KEYS)
                .contains("cannot be added, removed or updated during MGM re-registration")
            registrationService.stop()
        }

        @Test
        fun `re-registration fails when group policy changes were made`() {
            postUpEvent()
            registrationService.start()

            val groupPolicy = argumentCaptor<LayeredPropertyMap>()
            whenever(
                membershipPersistenceClient
                    .persistGroupPolicy(
                        eq(mgm),
                        groupPolicy.capture(),
                        eq(1)
                    )
            ).thenReturn(Operation(MembershipPersistenceResult.success()))

            // first registration
            registrationService.register(registrationRequestId, mgm, properties)
            verify(expirationProcessor).scheduleProcessingOfExpiredRequests(mgm)

            whenever(
                membershipQueryClient.queryRegistrationRequests(
                    eq(mgm),
                    eq(mgm.x500Name),
                    eq(listOf(RegistrationStatus.APPROVED)),
                    isNull()
                )
            ).thenReturn(MembershipQueryResult.Success(listOf(mock())))
            whenever(membershipQueryClient.queryGroupPolicy(mgm))
                .thenReturn(MembershipQueryResult.Success(Pair(groupPolicy.firstValue, 1)))

            // second registration
            val message = assertThrows<InvalidMembershipRegistrationException> {
                registrationService.register(
                    reRegistrationRequestId,
                    mgm,
                    properties.filterNot { it.key == "corda.group.protocol.registration" } +
                        mapOf(
                            "corda.group.protocol.registration" to
                                "net.corda.membership.impl.registration.static.member.StaticMemberRegistrationService"
                        )
                )
            }.message
            assertThat(message)
                .contains("protocol.registration")
                .contains("cannot be added, removed or updated during MGM re-registration")
            registrationService.stop()
        }

        @Test
        fun `re-registration fails when persisting the changed info fails`() {
            postUpEvent()
            registrationService.start()

            val groupPolicy = argumentCaptor<LayeredPropertyMap>()
            whenever(
                membershipPersistenceClient
                    .persistGroupPolicy(
                        eq(mgm),
                        groupPolicy.capture(),
                        eq(1)
                    )
            ).thenReturn(Operation(MembershipPersistenceResult.success()))

            // first registration
            registrationService.register(registrationRequestId, mgm, properties)
            verify(expirationProcessor).scheduleProcessingOfExpiredRequests(mgm)

            whenever(
                membershipQueryClient.queryRegistrationRequests(
                    eq(mgm),
                    eq(mgm.x500Name),
                    eq(listOf(RegistrationStatus.APPROVED)),
                    isNull()
                )
            ).thenReturn(MembershipQueryResult.Success(listOf(mock())))
            whenever(membershipQueryClient.queryGroupPolicy(mgm))
                .thenReturn(MembershipQueryResult.Success(Pair(groupPolicy.firstValue, 1)))
            val errorMsg = "Persistence failure"
            whenever(
                membershipPersistenceClient.persistMemberInfo(eq(mgm), eq(listOf(signedMemberInfoAfterReRegistration)))
            ).thenReturn(Operation(MembershipPersistenceResult.Failure(errorMsg)))

            // second registration
            val message = assertThrows<InvalidMembershipRegistrationException> {
                registrationService.register(reRegistrationRequestId, mgm, properties)
            }.message
            assertThat(message).contains(errorMsg)
            registrationService.stop()
        }

        @Test
        fun `re-registration fails when persisting the registration request fails`() {
            postUpEvent()
            registrationService.start()

            val groupPolicy = argumentCaptor<LayeredPropertyMap>()
            whenever(
                membershipPersistenceClient
                    .persistGroupPolicy(
                        eq(mgm),
                        groupPolicy.capture(),
                        eq(1)
                    )
            ).thenReturn(Operation(MembershipPersistenceResult.success()))

            // first registration
            registrationService.register(registrationRequestId, mgm, properties)
            verify(expirationProcessor).scheduleProcessingOfExpiredRequests(mgm)

            whenever(
                membershipQueryClient.queryRegistrationRequests(
                    eq(mgm),
                    eq(mgm.x500Name),
                    eq(listOf(RegistrationStatus.APPROVED)),
                    isNull()
                )
            ).thenReturn(MembershipQueryResult.Success(listOf(mock())))
            whenever(membershipQueryClient.queryGroupPolicy(mgm))
                .thenReturn(MembershipQueryResult.Success(Pair(groupPolicy.firstValue, 1)))
            val errorMsg = "Persistence failure"
            whenever(
                membershipPersistenceClient.persistRegistrationRequest(eq(mgm), any(), any())
            ).thenReturn(Operation(MembershipPersistenceResult.Failure(errorMsg)))

            // second registration
            val message = assertThrows<InvalidMembershipRegistrationException> {
                registrationService.register(reRegistrationRequestId, mgm, properties)
            }.message
            assertThat(message).contains(errorMsg)
            registrationService.stop()
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
    }
}
