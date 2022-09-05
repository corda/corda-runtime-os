package net.corda.membership.impl.registration.dynamic.handler.mgm

import net.corda.data.KeyValuePairList
import net.corda.data.membership.command.registration.RegistrationCommand
import net.corda.data.membership.command.registration.mgm.ApproveRegistration
import net.corda.data.membership.command.registration.mgm.ProcessMemberVerificationResponse
import net.corda.data.membership.common.RegistrationStatus
import net.corda.data.membership.p2p.SetOwnRegistrationStatus
import net.corda.data.membership.p2p.VerificationResponse
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

class ProcessMemberVerificationResponseHandlerTest {
    private companion object {
        const val GROUP_ID = "ABC123"
        const val REGISTRATION_ID = "REG-01"
        const val TOPIC = "dummyTopic"
    }

    private val mgm = createTestHoldingIdentity("C=GB, L=London, O=MGM", GROUP_ID).toAvro()
    private val member = createTestHoldingIdentity("C=GB, L=London, O=Alice", GROUP_ID).toAvro()
    private val command = ProcessMemberVerificationResponse(
        VerificationResponse(
            REGISTRATION_ID,
            KeyValuePairList(emptyList())
        )
    )
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
                RegistrationStatus.PENDING_AUTO_APPROVAL
            )
        } doReturn MembershipPersistenceResult.success()
    }
    private val record = mock<Record<String, AppMessage>>()
    private val p2pRecordsFactory = mock<P2pRecordsFactory> {
        on {
            createAuthenticatedMessageRecord(
                mgm,
                member,
                SetOwnRegistrationStatus(
                    REGISTRATION_ID,
                    RegistrationStatus.PENDING_AUTO_APPROVAL
                ),
                null
            )
        } doReturn record
    }

    private val processMemberVerificationResponseHandler = ProcessMemberVerificationResponseHandler(
        membershipPersistenceClient,
        mock(),
        mock(),
        p2pRecordsFactory,
    )

    @Test
    fun `handler returns approve member command`() {
        val result = processMemberVerificationResponseHandler.invoke(state, Record(TOPIC, member.toString(), RegistrationCommand(command)))

        verify(membershipPersistenceClient, times(1)).setRegistrationRequestStatus(
            mgm.toCorda(),
            REGISTRATION_ID,
            RegistrationStatus.PENDING_AUTO_APPROVAL
        )

        assertThat(result.outputStates).hasSize(2)
            .contains(record)
            .anyMatch {
                val key = it.key
                val value = it.value
                key == "$REGISTRATION_ID-${mgm.toCorda().shortHash}" &&
                        value is RegistrationCommand &&
                        value.command is ApproveRegistration
            }
        with(result.updatedState) {
            assertThat(this?.registeringMember).isEqualTo(member)
            assertThat(this?.mgm).isEqualTo(mgm)
            assertThat(this?.registrationId).isEqualTo(REGISTRATION_ID)
        }
    }

    @Test
    fun `exception is thrown when RegistrationState is null`() {
        assertThrows<MissingRegistrationStateException> {
            processMemberVerificationResponseHandler.invoke(null, Record(TOPIC, member.toString(), RegistrationCommand(command)))
        }
    }
}