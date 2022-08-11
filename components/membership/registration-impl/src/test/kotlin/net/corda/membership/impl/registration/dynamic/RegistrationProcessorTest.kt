package net.corda.membership.impl.registration.dynamic

import net.corda.data.CordaAvroDeserializer
import net.corda.data.CordaAvroSerializationFactory
import net.corda.data.CordaAvroSerializer
import net.corda.data.KeyValuePair
import net.corda.data.KeyValuePairList
import net.corda.data.crypto.wire.CryptoSignatureWithKey
import net.corda.data.identity.HoldingIdentity
import net.corda.data.membership.command.registration.RegistrationCommand
import net.corda.data.membership.command.registration.member.ProcessMemberVerificationRequest
import net.corda.data.membership.command.registration.mgm.StartRegistration
import net.corda.data.membership.command.registration.mgm.VerifyMember
import net.corda.data.membership.p2p.MembershipRegistrationRequest
import net.corda.data.membership.p2p.SetOwnRegistrationStatus
import net.corda.data.membership.p2p.VerificationRequest
import net.corda.data.membership.p2p.VerificationResponse
import net.corda.data.membership.state.RegistrationState
import net.corda.layeredpropertymap.LayeredPropertyMapFactory
import net.corda.membership.lib.MemberInfoExtension.Companion.ENDPOINTS
import net.corda.membership.lib.MemberInfoExtension.Companion.GROUP_ID
import net.corda.membership.lib.MemberInfoExtension.Companion.IS_MGM
import net.corda.membership.lib.MemberInfoExtension.Companion.MEMBER_STATUS_ACTIVE
import net.corda.membership.lib.MemberInfoExtension.Companion.STATUS
import net.corda.membership.lib.MemberInfoFactory
import net.corda.membership.lib.toMap
import net.corda.membership.persistence.client.MembershipPersistenceClient
import net.corda.membership.persistence.client.MembershipPersistenceResult
import net.corda.membership.persistence.client.MembershipQueryClient
import net.corda.membership.persistence.client.MembershipQueryResult
import net.corda.membership.read.MembershipGroupReader
import net.corda.membership.read.MembershipGroupReaderProvider
import net.corda.messaging.api.records.Record
import net.corda.p2p.app.AppMessage
import net.corda.p2p.app.AuthenticatedMessage
import net.corda.test.util.time.TestClock
import net.corda.v5.base.types.LayeredPropertyMap
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
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.isA
import org.mockito.kotlin.mock
import java.nio.ByteBuffer
import java.time.Instant
import java.util.*

class RegistrationProcessorTest {

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

        val memberContext = KeyValuePairList(listOf(KeyValuePair("key", "value")))
        val signature = CryptoSignatureWithKey(
            ByteBuffer.wrap("456".toByteArray()),
            ByteBuffer.wrap("789".toByteArray()),
            KeyValuePairList(emptyList())
        )
        val registrationRequest = MembershipRegistrationRequest(registrationId, memberContext, signature)

        val startRegistrationCommand = RegistrationCommand(
            StartRegistration(
                mgmHoldingIdentity,
                holdingIdentity,
                registrationRequest
            )
        )

        val verificationRequest = VerificationRequest(
            registrationId,
            KeyValuePairList(emptyList<KeyValuePair>())
        )

        val verificationResponse = VerificationResponse(
            registrationId,
            KeyValuePairList(emptyList<KeyValuePair>())
        )

        val verificationRequestCommand = RegistrationCommand(
            ProcessMemberVerificationRequest(holdingIdentity, mgmHoldingIdentity, verificationRequest)
        )

        val verifyMemberCommand = RegistrationCommand(VerifyMember())

        val state = RegistrationState(registrationId, holdingIdentity, mgmHoldingIdentity)
    }

    // Class under test
    private lateinit var processor: RegistrationProcessor

    // test dependencies
    lateinit var memberInfoFactory: MemberInfoFactory
    lateinit var membershipGroupReader: MembershipGroupReader
    lateinit var membershipGroupReaderProvider: MembershipGroupReaderProvider
    lateinit var deserializer: CordaAvroDeserializer<KeyValuePairList>
    lateinit var verificationRequestResponseSerializer: CordaAvroSerializer<Any>
    lateinit var cordaAvroSerializationFactory: CordaAvroSerializationFactory
    lateinit var membershipPersistenceClient: MembershipPersistenceClient
    lateinit var membershipQueryClient: MembershipQueryClient

    val memberMemberContext: MemberContext = mock {
        on { parse(eq(GROUP_ID), eq(String::class.java)) } doReturn groupId
        on { parseList(eq(ENDPOINTS), eq(EndpointInfo::class.java)) } doReturn listOf(mock())
    }
    val memberMgmContext: MGMContext = mock {
        on { parse(eq(STATUS), eq(String::class.java)) } doReturn MEMBER_STATUS_ACTIVE
    }
    val memberInfo: MemberInfo = mock {
        on { name } doReturn x500Name
        on { memberProvidedContext } doReturn memberMemberContext
        on { mgmProvidedContext } doReturn memberMgmContext
    }

    val mgmMemberContext: MemberContext = mock {
        on { parse(eq(GROUP_ID), eq(String::class.java)) } doReturn groupId
    }
    val mgmContext: MGMContext = mock {
        on { parseOrNull(eq(IS_MGM), any<Class<Boolean>>()) } doReturn true
    }
    val mgmMemberInfo: MemberInfo = mock {
        on { name } doReturn mgmX500Name
        on { memberProvidedContext } doReturn mgmMemberContext
        on { mgmProvidedContext } doReturn mgmContext
    }

    private val layeredPropertyMap = mock<LayeredPropertyMap> {
        on { entries } doReturn memberContext.toMap().entries
    }
    private val layeredPropertyMapFactory = mock<LayeredPropertyMapFactory> {
        on {createMap(any())} doReturn layeredPropertyMap
    }

    @BeforeEach
    fun setUp() {
        memberInfoFactory = mock {
            on { create(any<SortedMap<String, String?>>(), any()) } doReturn memberInfo
        }
        membershipGroupReader = mock {
            on { lookup(eq(mgmX500Name)) } doReturn mgmMemberInfo
        }
        membershipGroupReaderProvider = mock {
            on { getGroupReader(eq(mgmHoldingIdentity.toCorda())) } doReturn membershipGroupReader
        }
        deserializer = mock {
            on { deserialize(eq(memberContext.toByteBuffer().array())) } doReturn memberContext
        }
        verificationRequestResponseSerializer = mock {
            on { serialize(eq(verificationRequest)) } doReturn "REQUEST".toByteArray()
            on { serialize(eq(verificationResponse)) } doReturn "RESPONSE".toByteArray()
            on { serialize(isA<SetOwnRegistrationStatus>()) } doReturn "setStatus".toByteArray()
        }
        cordaAvroSerializationFactory = mock {
            on { createAvroDeserializer(any(), eq(KeyValuePairList::class.java)) } doReturn deserializer
            on { createAvroSerializer<Any>(any()) }.thenReturn(verificationRequestResponseSerializer)
        }
        membershipPersistenceClient = mock {
            on { persistRegistrationRequest(any(), any()) } doReturn MembershipPersistenceResult.success()
            on { persistMemberInfo(any(), any()) } doReturn MembershipPersistenceResult.success()
        }
        membershipQueryClient = mock {
            on {
                queryMemberInfo(
                    eq(mgmHoldingIdentity.toCorda()),
                    any()
                )
            } doReturn MembershipQueryResult.Success(emptyList())
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
            layeredPropertyMapFactory,
        )
    }

    @Test
    fun `Bad command - onNext called returns no follow on records and an unchanged state`() {
        listOf(
            null,
            RegistrationState(registrationId, holdingIdentity, mgmHoldingIdentity)
        ).forEach { state ->
            with(processor.onNext(state, Record(testTopic, testTopicKey, RegistrationCommand(Any())))) {
                assertThat(updatedState).isEqualTo(state)
                assertThat(responseEvents).isEmpty()
            }
        }
    }

    @Test
    fun `start registration command - onNext can be called for start registration command`() {
        val result = processor.onNext(null, Record(testTopic, testTopicKey, startRegistrationCommand))
        assertThat(result.updatedState).isNotNull
        assertThat(result.responseEvents).isNotEmpty.hasSize(2)
        assertThat((result.responseEvents.first().value as? RegistrationCommand)?.command)
            .isNotNull
            .isInstanceOf(VerifyMember::class.java)
    }

    @Test
    fun `process member verification request command - onNext can be called for command`() {
        val result = processor.onNext(null, Record(testTopic, testTopicKey, verificationRequestCommand))
        assertThat(result.updatedState).isNull()
        assertThat(result.responseEvents).isNotEmpty.hasSize(1)
        assertThat((result.responseEvents.first().value as? AppMessage)?.message as AuthenticatedMessage)
            .isNotNull
    }

    @Test
    fun `verify member command - onNext can be called for command`() {
        val result = processor.onNext(state, Record(testTopic, testTopicKey, verifyMemberCommand))
        assertThat(result.updatedState).isNotNull
        assertThat(result.responseEvents).isNotEmpty.hasSize(2)
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