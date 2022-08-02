package net.corda.membership.impl.registration.dynamic.handler.mgm

import net.corda.data.membership.command.registration.RegistrationCommand
import net.corda.data.membership.command.registration.mgm.DeclineRegistration
import net.corda.data.membership.state.RegistrationState
import net.corda.membership.impl.registration.dynamic.handler.MissingRegistrationStateException
import net.corda.membership.persistence.client.MembershipPersistenceClient
import net.corda.membership.persistence.client.MembershipPersistenceResult
import net.corda.messaging.api.records.Record
import net.corda.test.util.identity.createTestHoldingIdentity
import net.corda.virtualnode.toAvro
import net.corda.virtualnode.toCorda
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.doReturn
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

    private val handler = DeclineRegistrationHandler(membershipPersistenceClient)

    @Test
    fun `handler calls persistence client and returns no output states`() {
        val result = handler.invoke(state, Record(TOPIC, member.toString(), RegistrationCommand(command)))

        verify(membershipPersistenceClient, times(1)).setMemberAndRegistrationRequestAsDeclined(
            mgm.toCorda(),
            member.toCorda(),
            REGISTRATION_ID
        )

        Assertions.assertThat(result.outputStates).hasSize(0)
        with(result.updatedState) {
            Assertions.assertThat(this?.registeringMember).isEqualTo(member)
            Assertions.assertThat(this?.mgm).isEqualTo(mgm)
            Assertions.assertThat(this?.registrationId).isEqualTo(REGISTRATION_ID)
        }
    }

    @Test
    fun `exception is thrown when RegistrationState is null`() {
        assertThrows<MissingRegistrationStateException> {
            handler.invoke(null, Record(TOPIC, member.toString(), RegistrationCommand(command)))
        }
    }
}
