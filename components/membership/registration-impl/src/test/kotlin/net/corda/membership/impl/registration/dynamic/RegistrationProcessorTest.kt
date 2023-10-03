package net.corda.membership.impl.registration.dynamic

import net.corda.avro.serialization.CordaAvroDeserializer
import net.corda.avro.serialization.CordaAvroSerializationFactory
import net.corda.avro.serialization.CordaAvroSerializer
import net.corda.crypto.cipher.suite.KeyEncodingService
import net.corda.data.KeyValuePair
import net.corda.data.KeyValuePairList
import net.corda.data.crypto.wire.CryptoSignatureSpec
import net.corda.data.crypto.wire.CryptoSignatureWithKey
import net.corda.data.identity.HoldingIdentity
import net.corda.data.membership.SignedData
import net.corda.data.membership.command.registration.RegistrationCommand
import net.corda.data.membership.command.registration.member.ProcessMemberVerificationRequest
import net.corda.data.membership.command.registration.mgm.CheckForPendingRegistration
import net.corda.data.membership.command.registration.mgm.QueueRegistration
import net.corda.data.membership.command.registration.mgm.StartRegistration
import net.corda.data.membership.command.registration.mgm.VerifyMember
import net.corda.data.membership.common.RegistrationRequestDetails
import net.corda.data.membership.common.v2.RegistrationStatus
import net.corda.data.membership.p2p.MembershipRegistrationRequest
import net.corda.data.membership.p2p.v2.SetOwnRegistrationStatus
import net.corda.data.membership.p2p.VerificationRequest
import net.corda.data.membership.p2p.VerificationResponse
import net.corda.data.membership.state.RegistrationState
import net.corda.membership.impl.registration.VerificationResponseKeys
import net.corda.membership.lib.MemberInfoExtension.Companion.ENDPOINTS
import net.corda.membership.lib.MemberInfoExtension.Companion.GROUP_ID
import net.corda.membership.lib.MemberInfoExtension.Companion.IS_MGM
import net.corda.membership.lib.MemberInfoExtension.Companion.MEMBER_STATUS_ACTIVE
import net.corda.membership.lib.MemberInfoExtension.Companion.STATUS
import net.corda.membership.lib.MemberInfoFactory
import net.corda.membership.persistence.client.MembershipPersistenceClient
import net.corda.membership.persistence.client.MembershipQueryClient
import net.corda.membership.persistence.client.MembershipQueryResult
import net.corda.membership.read.MembershipGroupReader
import net.corda.membership.read.MembershipGroupReaderProvider
import net.corda.messaging.api.records.Record
import net.corda.data.p2p.app.AppMessage
import net.corda.data.p2p.app.AuthenticatedMessage
import net.corda.membership.lib.MemberInfoExtension.Companion.PARTY_SESSION_KEYS_PEM
import net.corda.membership.lib.MemberInfoExtension.Companion.PLATFORM_VERSION
import net.corda.membership.lib.SelfSignedMemberInfo
import net.corda.membership.persistence.client.MembershipPersistenceOperation
import net.corda.test.util.time.TestClock
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.membership.EndpointInfo
import net.corda.v5.membership.MGMContext
import net.corda.v5.membership.MemberContext
import net.corda.v5.membership.MemberInfo
import net.corda.virtualnode.toCorda
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.isA
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.nio.ByteBuffer
import java.security.PublicKey
import java.time.Instant
import java.util.*

class  RegistrationProcessorTest {

    private companion object {
        val clock = TestClock(Instant.now())
        val registrationId = UUID(1, 2).toString()
        val x500Name = MemberX500Name.parse("O=Tester,L=London,C=GB")
        val groupId = UUID(5, 6).toString()
        val holdingIdentity = HoldingIdentity(x500Name.toString(), groupId)

        val mgmX500Name = MemberX500Name.parse("O=TestMGM,L=London,C=GB")
        val mgmHoldingIdentity = HoldingIdentity(mgmX500Name.toString(), groupId)

        const val testTopic = "topic"
        const val testTopicKey = "key"
        const val SERIAL = 0L

        const val encodedSessionKey = "BBC123456789"
        val memberContext = KeyValuePairList(
            listOf(
                KeyValuePair("key", "value"),
                KeyValuePair(PLATFORM_VERSION, "50100"),
                KeyValuePair(PARTY_SESSION_KEYS_PEM.format(0), encodedSessionKey),
            )
        )
        val registrationContext = KeyValuePairList(listOf(KeyValuePair("key-2", "value-2")))
        val serialisedSigningKey = "456".toByteArray()
        val signingKey = mock<PublicKey>()
        val signature = CryptoSignatureWithKey(
            ByteBuffer.wrap(serialisedSigningKey),
            ByteBuffer.wrap("789".toByteArray())
        )
        val signatureSpec = CryptoSignatureSpec("test", null, null)
        val signedMemberContext = SignedData(
            memberContext.toByteBuffer(),
            signature,
            signatureSpec,
        )
        val signedRegistrationContext = SignedData(
            registrationContext.toByteBuffer(),
            signature,
            signatureSpec,
        )
        val registrationRequest =
            MembershipRegistrationRequest(
                registrationId,
                signedMemberContext,
                signedRegistrationContext,
                SERIAL,
            )
        val registrationRequestDetails =
            RegistrationRequestDetails(
                clock.instant(),
                clock.instant(),
                RegistrationStatus.RECEIVED_BY_MGM,
                registrationId,
                holdingIdentity.toCorda().shortHash.value,
                1,
                signedMemberContext,
                signedRegistrationContext,
                "",
                SERIAL,
            )

        val queueRegistrationCommand = RegistrationCommand(
            QueueRegistration(mgmHoldingIdentity, holdingIdentity, registrationRequest, 0)
        )

        val checkForPendingRegistrationCommand = RegistrationCommand(
            CheckForPendingRegistration(mgmHoldingIdentity, holdingIdentity, 0)
        )

        val startRegistrationCommand = RegistrationCommand(StartRegistration())

        val verificationRequest = VerificationRequest(
            registrationId,
            KeyValuePairList(emptyList<KeyValuePair>())
        )

        val verificationResponse = VerificationResponse(
            registrationId,
            KeyValuePairList(
                listOf(
                    KeyValuePair(VerificationResponseKeys.VERIFIED, true.toString())
                )
            )
        )

        val verificationRequestCommand = RegistrationCommand(
            ProcessMemberVerificationRequest(holdingIdentity, mgmHoldingIdentity, verificationRequest)
        )

        val verifyMemberCommand = RegistrationCommand(VerifyMember())

        val state = RegistrationState(registrationId, holdingIdentity, mgmHoldingIdentity, emptyList())
    }

    // Class under test
    private lateinit var processor: RegistrationProcessor

    // test dependencies
    private lateinit var memberInfoFactory: MemberInfoFactory
    private lateinit var membershipGroupReader: MembershipGroupReader
    private lateinit var membershipGroupReaderProvider: MembershipGroupReaderProvider
    private lateinit var deserializer: CordaAvroDeserializer<KeyValuePairList>
    private lateinit var verificationRequestResponseSerializer: CordaAvroSerializer<Any>
    private lateinit var cordaAvroSerializationFactory: CordaAvroSerializationFactory
    private lateinit var membershipPersistenceClient: MembershipPersistenceClient
    private lateinit var membershipQueryClient: MembershipQueryClient
    private lateinit var keyEncodingService: KeyEncodingService
    private val operation = mock<MembershipPersistenceOperation<Unit>> {
        on { createAsyncCommands() } doReturn emptyList()
    }

    private val memberMemberContext: MemberContext = mock {
        on { parse(eq(GROUP_ID), eq(String::class.java)) } doReturn groupId
        on { parseList(eq(ENDPOINTS), eq(EndpointInfo::class.java)) } doReturn listOf(mock())
    }
    private val memberMgmContext: MGMContext = mock {
        on { parse(eq(STATUS), eq(String::class.java)) } doReturn MEMBER_STATUS_ACTIVE
        on { parseOrNull(eq(IS_MGM), any<Class<Boolean>>()) } doReturn false
    }
    private val memberInfo: SelfSignedMemberInfo = mock {
        on { name } doReturn x500Name
        on { memberProvidedContext } doReturn memberMemberContext
        on { mgmProvidedContext } doReturn memberMgmContext
        on { platformVersion } doReturn 50100
    }

    private val mgmMemberContext: MemberContext = mock {
        on { parse(eq(GROUP_ID), eq(String::class.java)) } doReturn groupId
    }
    private val mgmContext: MGMContext = mock {
        on { parseOrNull(eq(IS_MGM), any<Class<Boolean>>()) } doReturn true
    }
    private val mgmMemberInfo: MemberInfo = mock {
        on { name } doReturn mgmX500Name
        on { memberProvidedContext } doReturn mgmMemberContext
        on { mgmProvidedContext } doReturn mgmContext
    }

    @BeforeEach
    fun setUp() {
        memberInfoFactory = mock {
            on { createSelfSignedMemberInfo(any(), any(), any(), any()) } doReturn memberInfo
        }
        membershipGroupReader = mock {
            on { lookup(eq(mgmX500Name), any()) } doReturn mgmMemberInfo
            on { groupParameters } doReturn mock()
        }
        membershipGroupReaderProvider = mock {
            on { getGroupReader(eq(mgmHoldingIdentity.toCorda())) } doReturn membershipGroupReader
            on { getGroupReader(eq(holdingIdentity.toCorda())) } doReturn membershipGroupReader
        }
        deserializer = mock {
            on { deserialize(eq(memberContext.toByteBuffer().array())) } doReturn memberContext
            on { deserialize(eq(registrationRequestDetails.registrationContext.data.array())) } doReturn mock()
        }
        verificationRequestResponseSerializer = mock {
            on { serialize(eq(verificationRequest)) } doReturn "REQUEST".toByteArray()
            on { serialize(eq(verificationResponse)) } doReturn "RESPONSE".toByteArray()
            on { serialize(isA<SetOwnRegistrationStatus>()) } doReturn "setStatus".toByteArray()
            on { serialize(any()) } doReturn "serializedData".toByteArray()
        }
        cordaAvroSerializationFactory = mock {
            on { createAvroDeserializer(any(), eq(KeyValuePairList::class.java)) } doReturn deserializer
            on { createAvroSerializer<Any>(any()) }.thenReturn(verificationRequestResponseSerializer)
        }
        membershipPersistenceClient = mock {
            on { persistRegistrationRequest(any(), any()) } doReturn operation
            on { setRegistrationRequestStatus(any(), any(), any(), anyOrNull()) } doReturn operation
            on { persistMemberInfo(any(), any()) } doReturn operation
        }
        membershipQueryClient = mock {
            on {
                queryMemberInfo(
                    eq(mgmHoldingIdentity.toCorda()),
                    any(),
                    any(),
                )
            } doReturn MembershipQueryResult.Success(emptyList())
            on {
                queryRegistrationRequest(eq(mgmHoldingIdentity.toCorda()), eq(registrationId))
            } doReturn MembershipQueryResult.Success(registrationRequestDetails)
            on {
                queryRegistrationRequests(
                    eq(mgmHoldingIdentity.toCorda()),
                    eq(holdingIdentity.toCorda().x500Name),
                    eq(listOf(RegistrationStatus.RECEIVED_BY_MGM)),
                    eq(1),
                )
            } doReturn MembershipQueryResult.Success(listOf(registrationRequestDetails))
        }
        keyEncodingService = mock {
            on { decodePublicKey(serialisedSigningKey) } doReturn signingKey
            on { decodePublicKey(encodedSessionKey) } doReturn signingKey
        }

        processor = RegistrationProcessor(
            clock,
            memberInfoFactory,
            membershipGroupReaderProvider,
            cordaAvroSerializationFactory,
            membershipPersistenceClient,
            membershipQueryClient,
            mock(),
            mock(),
            mock(),
            keyEncodingService,
        )
    }

    @Test
    fun `Bad command - onNext called returns no follow on records and an unchanged state`() {
        listOf(
            null,
            RegistrationState(registrationId, holdingIdentity, mgmHoldingIdentity, emptyList())
        ).forEach { state ->
            with(processor.onNext(state, Record(testTopic, testTopicKey, RegistrationCommand(Any())))) {
                assertThat(updatedState).isEqualTo(state)
                assertThat(responseEvents).isEmpty()
            }
        }
    }

    @Test
    fun `queue registration command - onNext can be called`() {
        val result = processor.onNext(null, Record(testTopic, testTopicKey, queueRegistrationCommand))
        assertThat(result.updatedState).isNull()
        assertThat(result.responseEvents).isNotEmpty.hasSize(2)
        assertThat(result.responseEvents.firstNotNullOf { it.value as? RegistrationCommand }.command)
            .isNotNull
            .isInstanceOf(CheckForPendingRegistration::class.java)
    }

    @Test
    fun `check for pending registration command - onNext can be called`() {
        val result = processor.onNext(null, Record(testTopic, testTopicKey, checkForPendingRegistrationCommand))
        assertThat(result.updatedState).isNotNull()
        assertThat(result.responseEvents).isNotEmpty.hasSize(1)
        assertThat((result.responseEvents.first().value as? RegistrationCommand)?.command)
            .isNotNull
            .isInstanceOf(StartRegistration::class.java)
    }

    @Test
    fun `start registration command - onNext can be called for start registration command`() {
        val result = processor.onNext(
            RegistrationState(registrationId, holdingIdentity, mgmHoldingIdentity, emptyList()),
            Record(testTopic, testTopicKey, startRegistrationCommand)
        )
        assertThat(result.updatedState).isNotNull
        val events = result.responseEvents
        assertThat(events).isNotEmpty.hasSize(2)
        assertThat(events.firstNotNullOf { it.value as? RegistrationCommand }.command)
            .isNotNull
            .isInstanceOf(VerifyMember::class.java)
    }

    @Test
    fun `process member verification request command - onNext can be called for command`() {
        val result = processor.onNext(null, Record(testTopic, testTopicKey, verificationRequestCommand))
        assertThat(result.updatedState).isNull()
        assertThat(result.responseEvents)
            .hasSize(1)
            .anySatisfy {
                assertThat((it.value as? AppMessage)?.message).isInstanceOf(AuthenticatedMessage::class.java)
            }
    }
    @Test
    fun `process member verification request command - onNext update the registration request state`() {
        val record = Record(
            "topic",
            "key",
            "value"
        )
        whenever(operation.createAsyncCommands()).doReturn(listOf(record))
        val result = processor.onNext(null, Record(testTopic, testTopicKey, verificationRequestCommand))

        assertThat(result.responseEvents)
            .contains(record)
    }

    @Test
    fun `verify member command - onNext can be called for command`() {
        val result = processor.onNext(state, Record(testTopic, testTopicKey, verifyMemberCommand))
        assertThat(result.updatedState).isNotNull
        assertThat(result.responseEvents).isNotEmpty.hasSize(1)
            .allMatch {
                (result.responseEvents.first().value as? AppMessage)?.message as? AuthenticatedMessage != null
            }
    }

    @Test
    fun `missing RegistrationState results in empty response`() {
        val result = processor.onNext(null, Record(testTopic, testTopicKey, verifyMemberCommand))
        assertThat(result.updatedState).isNull()
        assertThat(result.responseEvents).isEmpty()
    }
}
