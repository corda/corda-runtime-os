package net.corda.membership.impl.registration.dynamic.handler.member

import net.corda.data.CordaAvroSerializationFactory
import net.corda.data.CordaAvroSerializer
import net.corda.data.KeyValuePair
import net.corda.data.KeyValuePairList
import net.corda.data.membership.command.registration.RegistrationCommand
import net.corda.data.membership.command.registration.member.ProcessMemberVerificationRequest
import net.corda.data.membership.p2p.VerificationRequest
import net.corda.data.membership.p2p.VerificationResponse
import net.corda.membership.lib.MemberInfoExtension
import net.corda.membership.read.MembershipGroupReader
import net.corda.membership.read.MembershipGroupReaderProvider
import net.corda.messaging.api.records.Record
import net.corda.p2p.app.AppMessage
import net.corda.p2p.app.AuthenticatedMessage
import net.corda.test.util.identity.createTestHoldingIdentity
import net.corda.test.util.time.TestClock
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.membership.MGMContext
import net.corda.v5.membership.MemberContext
import net.corda.v5.membership.MemberInfo
import net.corda.virtualnode.toAvro
import net.corda.virtualnode.toCorda
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.KArgumentCaptor
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import java.time.Instant
import java.util.*

class VerificationRequestHandlerTest {
    private companion object {
        const val GROUP_ID = "ABC123"
        const val REGISTRATION_ID = "REG-01"

        val clock = TestClock(Instant.ofEpochSecond(0))
    }

    private val mgm = createTestHoldingIdentity("C=GB, L=London, O=MGM", GROUP_ID).toAvro()
    private val member = createTestHoldingIdentity("C=GB, L=London, O=Alice", GROUP_ID).toAvro()
    private val requestBody = KeyValuePairList(listOf(KeyValuePair("KEY", "dummyKey")))
    private val verificationRequest = VerificationRequest(
        REGISTRATION_ID,
        requestBody
    )

    private val response: KArgumentCaptor<VerificationResponse> = argumentCaptor()
    private val responseSerializer: CordaAvroSerializer<VerificationResponse> = mock {
        on { serialize(response.capture()) } doReturn "RESPONSE".toByteArray()
    }
    private val cordaAvroSerializationFactory: CordaAvroSerializationFactory = mock {
        on { createAvroSerializer<VerificationResponse>(any()) } doReturn responseSerializer
    }

    val mgmX500Name = MemberX500Name.parse("C=GB, L=London, O=MGM")
    val memberX500Name = MemberX500Name.parse("C=GB, L=London, O=Alice")

    val groupId = UUID.randomUUID().toString()

    val mgmMemberContext: MemberContext = mock {
        on { parse(eq(MemberInfoExtension.GROUP_ID), eq(String::class.java)) } doReturn GROUP_ID
    }
    val mgmContext: MGMContext = mock {
        on { parseOrNull(eq(MemberInfoExtension.IS_MGM), any<Class<Boolean>>()) } doReturn true
    }

    val memberContext: MemberContext = mock {
        on { parse(eq(MemberInfoExtension.GROUP_ID), eq(String::class.java)) } doReturn GROUP_ID
    }
    val memberMGMContext: MGMContext = mock {
        on { parseOrNull(eq(MemberInfoExtension.IS_MGM), any<Class<Boolean>>()) } doReturn false
    }

    val mgmMemberInfo: MemberInfo = mock {
        on { name } doReturn mgmX500Name
        on { memberProvidedContext } doReturn mgmMemberContext
        on { mgmProvidedContext } doReturn mgmContext
    }

    val memberInfo: MemberInfo = mock {
        on { name } doReturn memberX500Name
        on { memberProvidedContext } doReturn memberContext
        on { mgmProvidedContext } doReturn memberMGMContext
    }

    var membershipGroupReader: MembershipGroupReader = mock {
        on { lookup(eq(mgmX500Name)) } doReturn mgmMemberInfo
        on { lookup(eq(memberX500Name)) } doReturn memberInfo
    }

    var membershipGroupReaderProvider: MembershipGroupReaderProvider = mock {
        on { getGroupReader(eq(mgm.toCorda())) } doReturn membershipGroupReader
        on { getGroupReader(eq(member.toCorda())) } doReturn membershipGroupReader
    }

    private val verificationRequestHandler = VerificationRequestHandler(clock, cordaAvroSerializationFactory,membershipGroupReaderProvider)

    @Test
    fun `handler returns response message`() {
        val result = verificationRequestHandler.invoke(
            null,
            Record(
                "dummyTopic",
                member.toString(),
                RegistrationCommand(
                    ProcessMemberVerificationRequest(member, mgm, verificationRequest)
                )
            )
        )
        assertThat(result.outputStates).hasSize(1)
        assertThat(result.updatedState).isNull()
        val appMessage = result.outputStates.first().value as AppMessage
        with(appMessage.message as AuthenticatedMessage) {
            assertThat(this.header.source).isEqualTo(member)
            assertThat(this.header.destination).isEqualTo(mgm)
            assertThat(this.header.ttl).isNull()
            assertThat(this.header.messageId).isNotNull
            assertThat(this.header.traceId).isNull()
            assertThat(this.header.subsystem).isEqualTo("membership")
        }
        with(response.firstValue) {
            assertThat(this.registrationId).isEqualTo(REGISTRATION_ID)
        }
    }
}