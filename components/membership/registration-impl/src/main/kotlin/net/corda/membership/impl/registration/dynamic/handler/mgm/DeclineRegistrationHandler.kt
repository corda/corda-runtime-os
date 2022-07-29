package net.corda.membership.impl.registration.dynamic.handler.mgm

import net.corda.data.membership.command.registration.mgm.DeclineRegistration
import net.corda.data.membership.state.RegistrationState
import net.corda.membership.impl.registration.dynamic.handler.MissingRegistrationStateException
import net.corda.membership.impl.registration.dynamic.handler.RegistrationHandler
import net.corda.membership.impl.registration.dynamic.handler.RegistrationHandlerResult
import net.corda.membership.persistence.client.MembershipPersistenceClient
import net.corda.virtualnode.toCorda

class DeclineRegistrationHandler(
    private val membershipPersistenceClient: MembershipPersistenceClient,
) : RegistrationHandler<DeclineRegistration> {
    override fun invoke(
        state: RegistrationState?,
        key: String,
        command: DeclineRegistration
    ): RegistrationHandlerResult {
        if(state == null) throw MissingRegistrationStateException
        // Update the state of the request and member
        val approvedBy = state.mgm
        val approvedMember = state.registeringMember
        val registrationId = state.registrationId
        membershipPersistenceClient.setMemberAndRegistrationRequestAsDeclined(
            viewOwningIdentity = approvedBy.toCorda(),
            declinedMember = approvedMember.toCorda(),
            registrationRequestId = registrationId,
        )
        return RegistrationHandlerResult(
            RegistrationState(registrationId, approvedMember, approvedBy),
            emptyList()
        )
    }

    override val commandType: Class<DeclineRegistration>
        get() = DeclineRegistration::class.java
}
