package net.corda.membership.impl.persistence.service.handler

import net.corda.data.membership.db.request.MembershipRequestContext
import net.corda.data.membership.db.request.command.ActivateMember
import net.corda.data.membership.db.response.command.ActivateMemberResponse
import net.corda.membership.lib.MemberInfoExtension.Companion.MEMBER_STATUS_ACTIVE
import net.corda.membership.lib.MemberInfoExtension.Companion.MEMBER_STATUS_SUSPENDED

internal class ActivateMemberHandler(
    persistenceHandlerServices: PersistenceHandlerServices
) : BaseSuspensionActivationHandler<ActivateMember, ActivateMemberResponse>(persistenceHandlerServices) {

    override fun invoke(context: MembershipRequestContext, request: ActivateMember): ActivateMemberResponse {
        val updatedMemberInfo = changeMemberStatus(
            context,
            request.activatedMember,
            request.serialNumber,
            MEMBER_STATUS_SUSPENDED,
            MEMBER_STATUS_ACTIVE
        )

        return ActivateMemberResponse(updatedMemberInfo)
    }
}
