package net.corda.membership.impl.registration.dynamic.handler.member

import net.corda.data.identity.HoldingIdentity
import net.corda.data.membership.command.registration.member.PersistMemberRegistrationState
import net.corda.data.membership.common.RegistrationStatus
import net.corda.data.membership.p2p.SetOwnRegistrationStatus
import net.corda.membership.persistence.client.MembershipPersistenceClient
import net.corda.membership.persistence.client.MembershipPersistenceOperation
import net.corda.messaging.api.records.Record
import net.corda.virtualnode.toCorda
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.SoftAssertions.assertSoftly
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import java.util.UUID

class PersistMemberRegistrationStateHandlerTest {
    private val records = Record(
        "topic",
        "key",
        33
    )
    private val operation = mock<MembershipPersistenceOperation<Unit>> {
        on { createAsyncCommands() } doReturn listOf(records)
    }
    private val membershipPersistenceClient = mock<MembershipPersistenceClient> {
        on {
            setRegistrationRequestStatus(
                any(),
                any(),
                any(),
                anyOrNull(),
            )
        } doReturn operation
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
        handler.invoke(
            command = command,
            key = "key",
            state = null
        )

        verify(membershipPersistenceClient).setRegistrationRequestStatus(
            command.member.toCorda(),
            command.setStatusRequest.registrationId,
            command.setStatusRequest.newStatus
        )
    }

    @Test
    fun `invoke returns the commands`() {
        val result = handler.invoke(
            command = command,
            key = "key",
            state = null
        )

        assertSoftly { softly ->
            softly.assertThat(result.outputStates).containsExactly(records)
            softly.assertThat(result.updatedState).isNull()
        }
    }
}