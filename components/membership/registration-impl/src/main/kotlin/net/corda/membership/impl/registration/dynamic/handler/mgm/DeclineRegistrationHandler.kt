package net.corda.membership.impl.registration.dynamic.handler.mgm

import net.corda.data.membership.command.registration.mgm.DeclineRegistration
import net.corda.data.membership.state.RegistrationState
import net.corda.membership.impl.registration.dynamic.handler.MissingRegistrationStateException
import net.corda.membership.impl.registration.dynamic.handler.RegistrationHandler
import net.corda.membership.impl.registration.dynamic.handler.RegistrationHandlerResult
import net.corda.membership.lib.MemberInfoExtension.Companion.holdingIdentity
import net.corda.membership.lib.MemberInfoExtension.Companion.isMgm
import net.corda.membership.persistence.client.MembershipPersistenceClient
import net.corda.membership.persistence.client.MembershipQueryClient
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.membership.MemberInfo
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.toCorda

class DeclineRegistrationHandler(
    private val membershipPersistenceClient: MembershipPersistenceClient,
    private val membershipQueryClient: MembershipQueryClient,
) : RegistrationHandler<DeclineRegistration> {
    override fun invoke(
        state: RegistrationState?,
        key: String,
        command: DeclineRegistration
    ): RegistrationHandlerResult {
        if(state == null) throw MissingRegistrationStateException
        // Update the state of the request and member
        val declinedBy = state.mgm
        val declinedMember = state.registeringMember
        val registrationId = state.registrationId

        val allMembers = getAllMembers(declinedBy.toCorda())
        val mgm = allMembers.firstOrNull { it.isMgm } ?: throw FailToFindMgm

        membershipPersistenceClient.setMemberAndRegistrationRequestAsDeclined(
            viewOwningIdentity = mgm.holdingIdentity,
            declinedMember = declinedMember.toCorda(),
            registrationRequestId = registrationId,
        )
        return RegistrationHandlerResult(
            state,
            emptyList()
        )
    }

    private fun getAllMembers(owner: HoldingIdentity): Collection<MemberInfo> {
        return membershipQueryClient.queryMemberInfo(owner).getOrThrow()
    }

    internal object FailToFindMgm : CordaRuntimeException("Could not find MGM")

    override val commandType: Class<DeclineRegistration>
        get() = DeclineRegistration::class.java
}
