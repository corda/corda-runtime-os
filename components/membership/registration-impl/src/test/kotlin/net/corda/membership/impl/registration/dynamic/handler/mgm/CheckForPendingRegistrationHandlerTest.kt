package net.corda.membership.impl.registration.dynamic.handler.mgm

import net.corda.data.identity.HoldingIdentity
import net.corda.data.membership.command.registration.RegistrationCommand
import net.corda.data.membership.command.registration.mgm.CheckForPendingRegistration
import net.corda.data.membership.command.registration.mgm.StartRegistration
import net.corda.data.membership.common.RegistrationRequestDetails
import net.corda.data.membership.common.v2.RegistrationStatus
import net.corda.data.membership.state.RegistrationState
import net.corda.membership.persistence.client.MembershipQueryClient
import net.corda.membership.persistence.client.MembershipQueryResult
import net.corda.messaging.api.records.Record
import net.corda.v5.base.types.MemberX500Name
import net.corda.virtualnode.toCorda
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.UUID

class CheckForPendingRegistrationHandlerTest {
    private companion object {
        const val TOPIC = "topic"
        const val KEY = "key"
    }

    private val registrationId = UUID.randomUUID().toString()
    private val groupId = UUID.randomUUID()
    private val aliceName = MemberX500Name("Alice", "London", "GB")
    private val mgmName = MemberX500Name("MGM", "London", "GB")
    private val member = HoldingIdentity(aliceName.toString(), groupId.toString())
    private val mgm = HoldingIdentity(mgmName.toString(), groupId.toString())

    private val registrationRequest = mock<RegistrationRequestDetails> {
        on { registrationId } doReturn registrationId
    }
    private val membershipQueryClient = mock<MembershipQueryClient> {
        on {
            queryRegistrationRequests(
                eq(mgm.toCorda()), eq(member.toCorda().x500Name), eq(listOf(RegistrationStatus.RECEIVED_BY_MGM)), eq(1)
            )
        } doReturn MembershipQueryResult.Success(listOf(registrationRequest))
    }
    private val inputCommand = RegistrationCommand(CheckForPendingRegistration(mgm, member, 0))

    private val handler = CheckForPendingRegistrationHandler(membershipQueryClient)

    @Test
    fun `invoke returns start registration command and non-null state as next step`() {
        with(handler.invoke(null, Record(TOPIC, KEY, inputCommand))) {
            assertThat(updatedState).isNotNull
            assertThat(updatedState?.mgm).isEqualTo(mgm)
            assertThat(updatedState?.registeringMember).isEqualTo(member)
            assertThat(updatedState?.registrationId).isEqualTo(registrationId)
            assertThat(outputStates.size).isEqualTo(1)
            assertThat(outputStates.first().value).isInstanceOf(RegistrationCommand::class.java)
            val registrationCommand = outputStates.first().value as RegistrationCommand
            assertThat(registrationCommand.command).isInstanceOf(StartRegistration::class.java)
        }
    }

    @Test
    fun `do nothing when there is a registration in-progress for member`() {
        with(handler.invoke(RegistrationState(registrationId, member, mgm, emptyList()), Record(TOPIC, KEY, inputCommand))) {
            assertThat(updatedState).isNotNull
            assertThat(outputStates).isEmpty()
        }
    }

    @Test
    fun `retry if querying the request failed`() {
        whenever(membershipQueryClient.queryRegistrationRequests(any(), any(), any(), any()))
            .thenReturn(MembershipQueryResult.Failure("error happened"))
        with(handler.invoke(null, Record(TOPIC, KEY, inputCommand))) {
            assertThat(updatedState).isNull()
            assertThat(outputStates.size).isEqualTo(1)
            assertThat(outputStates.first().value).isInstanceOf(RegistrationCommand::class.java)
            val registrationCommand = outputStates.first().value as RegistrationCommand
            assertThat(registrationCommand.command).isInstanceOf(CheckForPendingRegistration::class.java)
            val outputCommand = registrationCommand.command as CheckForPendingRegistration
            assertThat(outputCommand.mgm).isEqualTo(mgm)
            assertThat(outputCommand.member).isEqualTo(member)
            assertThat(outputCommand.numberOfRetriesSoFar).isEqualTo(1)
        }
    }

    @Test
    fun `do nothing when there is no queued registration request for member`() {
        whenever(membershipQueryClient.queryRegistrationRequests(any(), any(), any(), any()))
            .thenReturn(MembershipQueryResult.Success(emptyList()))
        with(handler.invoke(null, Record(TOPIC, KEY, inputCommand))) {
            assertThat(updatedState).isNull()
            assertThat(outputStates).isEmpty()
        }
    }

    @Test
    fun `discard if max retries exceeded`() {
        val inputCommand = RegistrationCommand(CheckForPendingRegistration(mgm, member, 10))
        with(handler.invoke(null, Record(TOPIC, KEY, inputCommand))) {
            assertThat(updatedState).isNull()
            assertThat(outputStates).isEmpty()
        }
    }
}
