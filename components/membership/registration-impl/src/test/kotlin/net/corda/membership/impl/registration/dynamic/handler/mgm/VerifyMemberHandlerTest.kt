package net.corda.membership.impl.registration.dynamic.handler.mgm

import net.corda.data.CordaAvroSerializationFactory
import net.corda.data.CordaAvroSerializer
import net.corda.data.membership.command.registration.RegistrationCommand
import net.corda.data.membership.command.registration.mgm.VerifyMember
import net.corda.data.membership.db.request.command.RegistrationStatus
import net.corda.data.membership.p2p.VerificationRequest
import net.corda.data.membership.state.RegistrationState
import net.corda.membership.impl.registration.dynamic.handler.MissingRegistrationStateException
import net.corda.membership.lib.MemberInfoExtension
import net.corda.membership.persistence.client.MembershipPersistenceClient
import net.corda.membership.persistence.client.MembershipPersistenceResult
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
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.KArgumentCaptor
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import java.time.Instant
import java.util.*

class VerifyMemberHandlerTest {
    private companion object {
        const val GROUP_ID = "ABC123"
        const val REGISTRATION_ID = "REG-01"
        const val TOPIC = "dummyTopic"

        val clock = TestClock(Instant.ofEpochSecond(0))
    }

    private val mgm = createTestHoldingIdentity("C=GB, L=London, O=MGM", GROUP_ID).toAvro()
    private val member = createTestHoldingIdentity("C=GB, L=London, O=Alice", GROUP_ID).toAvro()
    private val command = VerifyMember()

    val state = RegistrationState(
        REGISTRATION_ID,
        member,
        mgm
    )

    private val request: KArgumentCaptor<VerificationRequest> = argumentCaptor()
    private val requestSerializer: CordaAvroSerializer<VerificationRequest> = mock {
        on { serialize(request.capture()) } doReturn "REQUEST".toByteArray()
    }
    private val cordaAvroSerializationFactory: CordaAvroSerializationFactory = mock {
        on { createAvroSerializer<VerificationRequest>(any()) } doReturn requestSerializer
    }

    val mgmX500Name = MemberX500Name.parse("C=GB, L=London, O=MGM")

    val groupId = UUID.randomUUID().toString()

    val mgmMemberContext: MemberContext = mock {
        on { parse(eq(MemberInfoExtension.GROUP_ID), eq(String::class.java)) } doReturn GROUP_ID
    }
    val mgmContext: MGMContext = mock {
        on { parseOrNull(eq(MemberInfoExtension.IS_MGM), any<Class<Boolean>>()) } doReturn true
    }

    val mgmMemberInfo: MemberInfo = mock {
        on { name } doReturn mgmX500Name
        on { memberProvidedContext } doReturn mgmMemberContext
        on { mgmProvidedContext } doReturn mgmContext
    }

    var membershipGroupReader: MembershipGroupReader = mock {
        on { lookup(eq(mgmX500Name)) } doReturn mgmMemberInfo
    }

    var membershipGroupReaderProvider: MembershipGroupReaderProvider= mock {
        on { getGroupReader(eq(mgm.toCorda())) } doReturn membershipGroupReader
    }

    private val membershipPersistenceClient = mock<MembershipPersistenceClient> {
        on {
            setRegistrationRequestStatus(
                mgm.toCorda(),
                REGISTRATION_ID,
                RegistrationStatus.PENDING_MEMBER_VERIFICATION
            )
        } doReturn MembershipPersistenceResult.success()
    }

    private val verifyMemberHandler = VerifyMemberHandler(
        clock,
        cordaAvroSerializationFactory,
        membershipPersistenceClient,
        membershipGroupReaderProvider
    )

    @Test
    fun `handler returns request message`() {
        val result = verifyMemberHandler.invoke(state, Record(TOPIC, member.toString(), RegistrationCommand(command)))

        verify(membershipPersistenceClient, times(1)).setRegistrationRequestStatus(
            mgm.toCorda(),
            REGISTRATION_ID,
            RegistrationStatus.PENDING_MEMBER_VERIFICATION
        )

        assertThat(result.outputStates).hasSize(1)
        val appMessage = result.outputStates.first().value as AppMessage
        with(appMessage.message as AuthenticatedMessage) {
            assertThat(this.header.source).isEqualTo(mgm)
            assertThat(this.header.destination).isEqualTo(member)
            assertThat(this.header.ttl).isNull()
            assertThat(this.header.messageId).isNotNull
            assertThat(this.header.traceId).isNull()
            assertThat(this.header.subsystem).isEqualTo("membership")
        }
    }

    @Test
    fun `exception is thrown when RegistrationState is null`() {
        assertThrows<MissingRegistrationStateException> {
            verifyMemberHandler.invoke(null, Record(TOPIC, member.toString(), RegistrationCommand(command)))
        }
    }
}