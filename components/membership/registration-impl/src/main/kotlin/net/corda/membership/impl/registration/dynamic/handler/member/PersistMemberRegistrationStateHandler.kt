package net.corda.membership.impl.registration.dynamic.handler.member

import net.corda.data.membership.command.registration.member.PersistMemberRegistrationState
import net.corda.data.membership.state.RegistrationState
import net.corda.membership.impl.registration.dynamic.handler.RegistrationHandler
import net.corda.membership.impl.registration.dynamic.handler.RegistrationHandlerResult
import net.corda.membership.persistence.client.MembershipPersistenceClient
import net.corda.virtualnode.toCorda

class PersistMemberRegistrationStateHandler(
    private val membershipPersistenceClient: MembershipPersistenceClient,
) : RegistrationHandler<PersistMemberRegistrationState> {
    override fun invoke(
        state: RegistrationState?,
        key: String,
        command: PersistMemberRegistrationState,
    ): RegistrationHandlerResult {
        membershipPersistenceClient.setRegistrationRequestStatus(
            command.member.toCorda(),
            command.setStatusRequest.registrationId,
            command.setStatusRequest.newStatus,
        )
        return RegistrationHandlerResult(
            null,
            emptyList()
        )
    }

    override val commandType = PersistMemberRegistrationState::class.java
}