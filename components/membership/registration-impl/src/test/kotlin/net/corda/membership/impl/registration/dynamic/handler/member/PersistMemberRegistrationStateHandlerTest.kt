package net.corda.membership.impl.registration.dynamic.handler.member

import net.corda.data.identity.HoldingIdentity
import net.corda.data.membership.command.registration.member.PersistMemberRegistrationState
import net.corda.data.membership.common.RegistrationStatus
import net.corda.data.membership.p2p.SetOwnRegistrationStatus
import net.corda.membership.persistence.client.AsyncMembershipPersistenceClient
import net.corda.membership.persistence.client.MembershipPersistenceClient
import net.corda.messaging.api.records.Record
import net.corda.virtualnode.toCorda
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.SoftAssertions.assertSoftly
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.UUID

class PersistMemberRegistrationStateHandlerTest {
    private val asyncMembershipPersistenceClient = mock<AsyncMembershipPersistenceClient>()
    private val membershipPersistenceClient = mock<MembershipPersistenceClient> {
        on { asyncClient } doReturn asyncMembershipPersistenceClient
    }
    val command = PersistMemberRegistrationState(
        HoldingIdentity("O=Alice, L=London, C=GB", "GroupId"),
        SetOwnRegistrationStatus(
            UUID(1,2).toString(),
            RegistrationStatus.DECLINED
        )
    )

    private val handler = PersistMemberRegistrationStateHandler(
        membershipPersistenceClient,
    )

    @Test
    fun `command type return the correct command`() {
        assertThat(handler.commandType).isEqualTo(PersistMemberRegistrationState::class.java)
    }

    @Test
    fun `invoke persist the member`() {
        val records = listOf(Record("one", "two", "three"))
        whenever(
            asyncMembershipPersistenceClient.setRegistrationRequestStatusRequest(
                command.member.toCorda(),
                command.setStatusRequest.registrationId,
                command.setStatusRequest.newStatus
            )
        ).doReturn(records)

        val response = handler.invoke(
            command = command,
            key = "key",
            state = null
        )

        assertThat(response.outputStates).isEqualTo(records)
    }

    @Test
    fun `invoke returns nothing`() {
        val result = handler.invoke(
            command = command,
            key = "key",
            state = null
        )

        assertSoftly { softly ->
            softly.assertThat(result.outputStates).isEmpty()
            softly.assertThat(result.updatedState).isNull()
        }
    }
}