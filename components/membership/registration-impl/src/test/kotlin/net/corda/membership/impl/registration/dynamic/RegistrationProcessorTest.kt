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
import net.corda.data.membership.p2p.VerificationRequest
import net.corda.data.membership.p2p.VerificationResponse
import net.corda.data.membership.state.RegistrationState
import net.corda.membership.lib.MemberInfoExtension.Companion.ENDPOINTS
import net.corda.membership.lib.MemberInfoExtension.Companion.GROUP_ID
import net.corda.membership.lib.MemberInfoExtension.Companion.IS_MGM
import net.corda.membership.lib.MemberInfoFactory
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
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import java.nio.ByteBuffer
import java.time.Instant
import java.util.SortedMap
import java.util.UUID

class RegistrationProcessorTest {

    private companion object {
        val clock = TestClock(Instant.now())
        val registrationId = UUID.randomUUID().toString()
        val x500Name = MemberX500Name.parse("O=Tester,L=London,C=GB")
        val groupId = UUID.randomUUID().toString()
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
        val registrationRequest = MembershipRegistrationRequest(registrationId, memberContext.toByteBuffer(), signature)

        val startRegistrationCommand = RegistrationCommand(
            StartRegistration(
                mgmHoldingIdentity,
                holdingIdentity,
                registrationRequest
            )
        )

        val verificationRequest = VerificationRequest(
            registrationId,
            KeyValuePairList(listOf(KeyValuePair("key", "value")))
        )

        val verificationResponse = VerificationResponse(
            registrationId,
            KeyValuePairList(emptyList<KeyValuePair>())
        )

        val verificationRequestCommand = RegistrationCommand(
            ProcessMemberVerificationRequest(holdingIdentity, mgmHoldingIdentity, verificationRequest)
        )
    }

    // Class under test
    private lateinit var processor: RegistrationProcessor

    // test dependencies
    lateinit var memberInfoFactory: MemberInfoFactory
    lateinit var membershipGroupReader: MembershipGroupReader
    lateinit var membershipGroupReaderProvider: MembershipGroupReaderProvider
    lateinit var deserializer: CordaAvroDeserializer<KeyValuePairList>
    lateinit var verificationSerializer: CordaAvroSerializer<VerificationResponse>
    lateinit var cordaAvroSerializationFactory: CordaAvroSerializationFactory
    lateinit var membershipPersistenceClient: MembershipPersistenceClient
    lateinit var membershipQueryClient: MembershipQueryClient

    val memberMemberContext: MemberContext = mock {
        on { parse(eq(GROUP_ID), eq(String::class.java)) } doReturn groupId
        on { parseList(eq(ENDPOINTS), eq(EndpointInfo::class.java)) } doReturn listOf(mock())
    }
    val memberInfo: MemberInfo = mock {
        on { name } doReturn x500Name
        on { memberProvidedContext } doReturn memberMemberContext
    }

    val mgmMemberContext: MemberContext = mock {
        on { parse(eq(GROUP_ID), eq(String::class.java)) } doReturn groupId
    }
    val mgmContext: MGMContext = mock {
        on { parseOrNull(eq(IS_MGM), any<Class<Boolean>>()) } doReturn true
    }
    val mgmMemberInfo: MemberInfo = mock {
        on { memberProvidedContext } doReturn mgmMemberContext
        on { mgmProvidedContext } doReturn mgmContext
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
        verificationSerializer = mock {
            on { serialize(verificationResponse) } doReturn "RESPONSE".toByteArray()
        }
        cordaAvroSerializationFactory = mock {
            on { createAvroDeserializer(any(), eq(KeyValuePairList::class.java)) } doReturn deserializer
            on { createAvroSerializer<VerificationResponse>(any()) } doReturn verificationSerializer
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
        )
    }

    @Test
    fun `Bad command - onNext called returns no follow on records and an unchanged state`() {
        listOf(
            null,
            RegistrationState(registrationId, holdingIdentity)
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
        assertThat(result.responseEvents).isNotEmpty.hasSize(1)
        assertThat((result.responseEvents.first().value as? RegistrationCommand)?.command)
            .isNotNull
            .isInstanceOf(VerifyMember::class.java)
    }

    @Test
    fun `process member verification request command - onNext can be called for command`() {
        val result = processor.onNext(null, Record(testTopic, testTopicKey, verificationRequestCommand))
        assertThat(result.updatedState).isNotNull
        assertThat(result.responseEvents).isNotEmpty.hasSize(1)
        assertThat((result.responseEvents.first().value as? AppMessage)?.message as AuthenticatedMessage)
            .isNotNull
    }
}