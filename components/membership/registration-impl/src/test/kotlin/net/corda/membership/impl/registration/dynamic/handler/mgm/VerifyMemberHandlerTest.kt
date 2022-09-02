package net.corda.membership.impl.registration.dynamic.handler.mgm

import net.corda.data.KeyValuePair
import net.corda.data.KeyValuePairList
import net.corda.data.membership.command.registration.RegistrationCommand
import net.corda.data.membership.command.registration.mgm.VerifyMember
import net.corda.data.membership.common.RegistrationStatus
import net.corda.data.membership.p2p.VerificationRequest
import net.corda.data.membership.state.RegistrationState
import net.corda.membership.impl.registration.dynamic.handler.MissingRegistrationStateException
import net.corda.membership.p2p.helpers.P2pRecordsFactory
import net.corda.membership.persistence.client.MembershipPersistenceClient
import net.corda.membership.persistence.client.MembershipPersistenceResult
import net.corda.messaging.api.records.Record
import net.corda.p2p.app.AppMessage
import net.corda.test.util.identity.createTestHoldingIdentity
import net.corda.virtualnode.toAvro
import net.corda.virtualnode.toCorda
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify

class VerifyMemberHandlerTest {
    private companion object {
        const val GROUP_ID = "ABC123"
        const val REGISTRATION_ID = "REG-01"
        const val TOPIC = "dummyTopic"
    }

    private val mgm = createTestHoldingIdentity("C=GB, L=London, O=MGM", GROUP_ID).toAvro()
    private val member = createTestHoldingIdentity("C=GB, L=London, O=Alice", GROUP_ID).toAvro()
    private val command = VerifyMember()

    private val state = RegistrationState(
        REGISTRATION_ID,
        member,
        mgm
    )

    private val membershipPersistenceClient = mock<MembershipPersistenceClient> {
        on {
            setRegistrationRequestStatus(
                mgm.toCorda(),
                REGISTRATION_ID,
                RegistrationStatus.PENDING_MEMBER_VERIFICATION
            )
        } doReturn MembershipPersistenceResult.success()
    }
    private val verificationRequestRecord = mock<Record<String, AppMessage>>()
    private val p2pRecordsFactory = mock<P2pRecordsFactory> {
        on {
            createAuthenticatedMessageRecord(
                mgm,
                member,
                VerificationRequest(
                    REGISTRATION_ID,
                    KeyValuePairList(emptyList<KeyValuePair>())
                )
            )
        } doReturn verificationRequestRecord
    }
    private val verifyMemberHandler = VerifyMemberHandler(mock(), mock(), membershipPersistenceClient, p2pRecordsFactory)

    @Test
    fun `handler returns request message`() {
        val result = verifyMemberHandler.invoke(state, Record(TOPIC, member.toString(), RegistrationCommand(command)))

        verify(membershipPersistenceClient, times(1)).setRegistrationRequestStatus(
            mgm.toCorda(),
            REGISTRATION_ID,
            RegistrationStatus.PENDING_MEMBER_VERIFICATION
        )

        assertThat(result.outputStates).hasSize(1)
            .contains(verificationRequestRecord)
    }

    @Test
    fun `exception is thrown when RegistrationState is null`() {
        assertThrows<MissingRegistrationStateException> {
            verifyMemberHandler.invoke(null, Record(TOPIC, member.toString(), RegistrationCommand(command)))
        }
    }
}