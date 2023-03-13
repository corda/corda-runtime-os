package net.corda.membership.impl.persistence.service.handler

import net.corda.data.membership.db.request.MembershipRequestContext
import net.corda.data.membership.db.request.command.SuspendMember
import net.corda.data.membership.db.response.command.SuspendMemberResponse
import net.corda.membership.lib.MemberInfoExtension.Companion.MEMBER_STATUS_ACTIVE
import net.corda.membership.lib.MemberInfoExtension.Companion.MEMBER_STATUS_SUSPENDED

internal class SuspendMemberHandler(
    persistenceHandlerServices: PersistenceHandlerServices
) : BaseSuspensionActivationHandler<SuspendMember, SuspendMemberResponse>(persistenceHandlerServices) {

    override fun invoke(context: MembershipRequestContext, request: SuspendMember): SuspendMemberResponse {
        val updatedMemberInfo = changeMemberStatus(
            context,
            request.suspendedMember,
            request.serialNumber,
            MEMBER_STATUS_ACTIVE,
            MEMBER_STATUS_SUSPENDED
        )

        return SuspendMemberResponse(updatedMemberInfo)
    }
}
