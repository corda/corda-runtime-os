package net.corda.membership.impl.registration.dynamic.handler.member

import net.corda.data.CordaAvroSerializationFactory
import net.corda.data.CordaAvroSerializer
import net.corda.data.KeyValuePair
import net.corda.data.KeyValuePairList
import net.corda.data.membership.command.registration.RegistrationCommand
import net.corda.data.membership.command.registration.member.ProcessMemberVerificationRequest
import net.corda.data.membership.p2p.VerificationRequest
import net.corda.data.membership.p2p.VerificationResponse
import net.corda.messaging.api.records.Record
import net.corda.p2p.app.AppMessage
import net.corda.p2p.app.AuthenticatedMessage
import net.corda.test.util.time.TestClock
import net.corda.v5.base.types.MemberX500Name
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.toAvro
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.KArgumentCaptor
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import java.time.Instant

class VerificationRequestHandlerTest {
    private companion object {
        const val GROUP_ID = "ABC123"
        const val REGISTRATION_ID = "REG-01"

        val clock = TestClock(Instant.ofEpochSecond(0))
    }

    private val mgm = HoldingIdentity(
        MemberX500Name.parse("C=GB, L=London, O=MGM"), GROUP_ID).toAvro()
    private val member = HoldingIdentity(MemberX500Name.parse("C=GB, L=London, O=Alice"), GROUP_ID).toAvro()
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

    private val verificationRequestHandler = VerificationRequestHandler(clock, cordaAvroSerializationFactory)

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
            assertThat(this.header.ttl).isNotNull
            assertThat(this.header.messageId).isNotNull
            assertThat(this.header.traceId).isNull()
            assertThat(this.header.subsystem).isEqualTo("membership")
        }
        with(response.firstValue) {
            assertThat(this.registrationId).isEqualTo(REGISTRATION_ID)
        }
    }
}