package net.corda.membership.impl.registration.dynamic.handler.mgm

import net.corda.data.KeyValuePairList
import net.corda.data.membership.command.registration.RegistrationCommand
import net.corda.data.membership.command.registration.mgm.ApproveRegistration
import net.corda.data.membership.command.registration.mgm.ProcessMemberVerificationResponse
import net.corda.data.membership.db.request.command.RegistrationStatus
import net.corda.data.membership.p2p.VerificationResponse
import net.corda.membership.persistence.client.MembershipPersistenceClient
import net.corda.membership.persistence.client.MembershipPersistenceResult
import net.corda.messaging.api.records.Record
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.toAvro
import net.corda.virtualnode.toCorda
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify

class VerificationResponseHandlerTest {
    private companion object {
        const val GROUP_ID = "ABC123"
        const val REGISTRATION_ID = "REG-01"
    }

    private val mgm = HoldingIdentity("C=GB, L=London, O=MGM", GROUP_ID).toAvro()
    private val member = HoldingIdentity("C=GB, L=London, O=Alice", GROUP_ID).toAvro()
    private val command = ProcessMemberVerificationResponse(
        mgm,
        member,
        VerificationResponse(
            REGISTRATION_ID,
            KeyValuePairList(emptyList())
        )
    )

    private val membershipPersistenceClient = mock<MembershipPersistenceClient> {
        on {
            setRegistrationRequestStatus(
                mgm.toCorda(),
                REGISTRATION_ID,
                RegistrationStatus.PENDING_AUTO_APPROVAL
            )
        } doReturn MembershipPersistenceResult.success()
    }

    private val verificationResponseHandler = VerificationResponseHandler(membershipPersistenceClient)

    @Test
    fun `handler returns approve member command`() {
        val result = verificationResponseHandler.invoke(Record("dummyTopic", member.toString(), RegistrationCommand(command)))

        verify(membershipPersistenceClient, times(1)).setRegistrationRequestStatus(
            mgm.toCorda(),
            REGISTRATION_ID,
            RegistrationStatus.PENDING_AUTO_APPROVAL
        )

        assertThat(result.outputStates).hasSize(1)
        val registrationCommand = result.outputStates.first().value as RegistrationCommand
        val approveRegistrationCommand = registrationCommand.command as ApproveRegistration
        assertThat(approveRegistrationCommand.approvedMember).isEqualTo(member)
        assertThat(approveRegistrationCommand.approvedBy).isEqualTo(mgm)
        assertThat(approveRegistrationCommand.registrationId).isEqualTo(REGISTRATION_ID)
    }
}