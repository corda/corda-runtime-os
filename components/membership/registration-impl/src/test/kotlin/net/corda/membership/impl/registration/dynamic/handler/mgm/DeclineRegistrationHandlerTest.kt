package net.corda.membership.impl.registration.dynamic.handler.mgm

import net.corda.data.membership.command.registration.RegistrationCommand
import net.corda.data.membership.command.registration.mgm.DeclineRegistration
import net.corda.data.membership.p2p.SetOwnRegistrationStatus
import net.corda.data.membership.state.RegistrationState
import net.corda.membership.impl.registration.dynamic.handler.MissingRegistrationStateException
import net.corda.membership.impl.registration.dynamic.handler.helpers.P2pRecordsFactory
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
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.isA
import org.mockito.kotlin.isNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify

class DeclineRegistrationHandlerTest {
    private companion object {
        const val GROUP_ID = "ABC123"
        const val REGISTRATION_ID = "REG-01"
        const val TOPIC = "dummyTopic"
    }

    private val mgm = createTestHoldingIdentity("C=GB, L=London, O=MGM", GROUP_ID).toAvro()
    private val member = createTestHoldingIdentity("C=GB, L=London, O=Alice", GROUP_ID).toAvro()
    private val command = DeclineRegistration()
    private val state = RegistrationState(
        REGISTRATION_ID,
        member,
        mgm
    )

    private val membershipPersistenceClient = mock<MembershipPersistenceClient> {
        on {
            setMemberAndRegistrationRequestAsDeclined(
                mgm.toCorda(),
                member.toCorda(),
                REGISTRATION_ID
            )
        } doReturn MembershipPersistenceResult.success()
    }

    private val record = mock<Record<String, AppMessage>>()
    private val p2pRecordsFactory = mock<P2pRecordsFactory> {
        on { createAuthenticatedMessageRecord(any(), any(), isA<SetOwnRegistrationStatus>(), isNull()) } doReturn record
    }

    private val handler = DeclineRegistrationHandler(membershipPersistenceClient, mock(), mock(), p2pRecordsFactory)

    @Test
    fun `handler calls persistence client and returns no output states`() {
        val result = handler.invoke(state, Record(TOPIC, member.toString(), RegistrationCommand(command)))

        verify(membershipPersistenceClient, times(1)).setMemberAndRegistrationRequestAsDeclined(
            mgm.toCorda(),
            member.toCorda(),
            REGISTRATION_ID
        )

        assertThat(result.outputStates)
            .hasSize(1)
            .contains(record)
        with(result.updatedState) {
            assertThat(this?.registeringMember).isEqualTo(member)
            assertThat(this?.mgm).isEqualTo(mgm)
            assertThat(this?.registrationId).isEqualTo(REGISTRATION_ID)
        }
    }

    @Test
    fun `exception is thrown when RegistrationState is null`() {
        assertThrows<MissingRegistrationStateException> {
            handler.invoke(null, Record(TOPIC, member.toString(), RegistrationCommand(command)))
        }
    }
}
